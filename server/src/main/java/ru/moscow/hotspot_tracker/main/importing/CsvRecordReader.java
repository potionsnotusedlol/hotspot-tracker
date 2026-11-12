package ru.moscow.hotspot_tracker.main.importing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Потоковый CSV-ридер для FR-IMP-01/05/08.
 *
 * <p>Читатель не накапливает файл, принятые записи или ошибки. Размер текущего
 * поля и всей записи ограничивается параметрами вызова. Обработчики вызываются
 * синхронно; если они сохраняют данные, за расход памяти отвечает вызывающий код.
 *
 * <p>Кодировка и разделитель задаются явно (в том числе UTF-8 и Windows-1251).
 * Их определение, маппинг колонок, предметная валидация и сохранение в БД находятся
 * за пределами читателя.
 *
 * <p>Формат data.mos.ru: завершающий разделитель сохраняет последнее пустое поле;
 * обе строки заголовка (английские коды и русские подписи) передаются как обычные
 * записи. Вызывающий код распознаёт заголовки и удаляет техническую последнюю
 * колонку по схеме набора данных. Читатель не удаляет её автоматически, поскольку
 * пустая последняя колонка может быть значением данных.
 *
 * <p>Пределы выбираются конфигурацией импорта с учётом схемы данных, доступной
 * памяти и числа параллельных импортов. Значения по умолчанию и безопасный бюджет
 * памяти этот API не задаёт: положительный, но чрезмерно большой лимит сам по себе
 * не гарантирует отсутствие OOM. Пределы должны учитывать накладные расходы
 * объектов, копирование значений и буферы исходного текста для отчёта об ошибках.
 *
 * <p>Чтение и декодирование буферизованы; одной буферизации
 * недостаточно для подтверждения скорости посимвольного чтения. NFR-PERF-04 требует
 * импорта 10⁶ строк за не более 10 минут на целевом стенде. Это критерий приёмки
 * всей цепочки импорта, а не результат измерения этого компонента. Перед приёмкой
 * необходимы замеры времени и пиковой памяти, включая файл с незакрытой кавычкой.
 */
public final class CsvRecordReader {

    /**
     * Принятая логическая запись с неизменяемым снимком значений колонок.
     *
     * <p>Внешние кавычки удаляются, удвоенные кавычки декодируются в одну,
     * пробелы сохраняются, переводы строк внутри поля заменяются на LF.
     *
     * @param number номер попытки чтения непустой логической записи с 1;
     *               отклонённые записи тоже занимают номер, поэтому возможны пропуски;
     *               заголовки учитываются, пропущенные пустые строки — нет
     * @param sourceLine физическая строка начала записи с 1; LF, CRLF и CR
     *                   считаются одним переводом строки каждый, включая строки внутри кавычек
     * @param fields значения в исходном порядке, без {@code null}; пустое поле — пустая строка
     */
    public record Record(long number, long sourceLine, List<String> fields) {

        /**
         * Создаёт запись с защитной неизменяемой копией списка полей.
         *
         * @param number номер попытки чтения записи
         * @param sourceLine физическая строка начала записи
         * @param fields исходный список значений
         * @throws NullPointerException если список или его элемент равен {@code null}
         */
        public Record {
            fields = List.copyOf(fields);
        }
    }

    /** Причина отклонения логической записи. */
    public enum RejectionReason {
        /** Кавычка в поле без кавычек или посторонний символ после закрывающей кавычки. */
        INVALID_QUOTE,
        /** Поле в кавычках не закрыто до конца файла. */
        UNCLOSED_QUOTE,
        /** Превышен предел длины декодированного поля. */
        FIELD_TOO_LONG,
        /** Превышен предел длины исходной записи. */
        RECORD_TOO_LONG
    }

    /**
     * Описание отклонённой записи без накопления её исходного текста.
     *
     * @param number номер попытки чтения по тем же правилам, что у {@link Record}
     * @param sourceLine физическая строка начала отклонённой записи
     * @param errorLine физическая строка обнаружения ошибки; при незакрытой кавычке — строка EOF
     * @param reason причина отклонения, не {@code null}
     */
    public record RejectedRecord(long number, long sourceLine, long errorLine, RejectionReason reason) {
    }

    /** Синхронный обработчик принятых записей, включая заголовки. */
    @FunctionalInterface
    public interface RecordHandler {

        /**
         * Обрабатывает запись ровно один раз, в порядке чтения файла.
         *
         * @param record принятая запись, не {@code null}
         * @throws IOException если необходимо прекратить импорт
         */
        void accept(Record record) throws IOException;
    }

    /**
     * Синхронный обработчик отклонений в порядке чтения файла.
     * Позволяет потоково записывать отклонённый текст в файл отчёта FR-IMP-08.
     */
    @FunctionalInterface
    public interface RejectionHandler {

        /**
         * Сообщает об одной отклонённой записи; в обработчик принятых она не передаётся.
         *
         * <p>Поток содержит исходный декодированный CSV-текст от начала попытки
         * чтения до границы восстановления включительно: с кавычками, разделителями
         * и исходными CR/LF, без начального BOM файла. При EOF завершающий перевод
         * строки не добавляется. После повреждения многострочного CSV этот участок
         * может включать несколько строк данных; это не гарантия границ исходной записи.
         *
         * <p>Поток ограничен текущим отклонённым участком: его EOF не означает конец
         * файла. Доступен только во время вызова; сохранять ссылку и читать позже
         * нельзя. Его закрытие не закрывает исходный файл. После нормального возврата
         * обработчика читатель дочитывает оставшийся участок без накопления и
         * делает поток недействительным. Обработчик может читать частями или вовсе
         * не читать текст. Реализация не должна собирать весь участок в строку:
         * сохранённый ограниченный префикс дополняется потоковым чтением остатка.
         *
         * <p>Ошибка декодирования/ввода-вывода при чтении участка или его дочитывании
         * фатальна; уже записанный отчёт может оказаться частичным. Экспорт и
         * пометка такого отчёта как незавершённого — ответственность вызывающего кода.
         *
         * @param rejection описание ошибки, не {@code null}
         * @param rejectedText поток отклонённого текста, не {@code null};
         *                     точность исходных байтов не гарантируется
         * @throws IOException если необходимо прекратить импорт
         */
        void accept(RejectedRecord rejection, Reader rejectedText) throws IOException;
    }

    /**
     * Потоково читает CSV, передавая принятые записи и отклонения разным обработчикам.
     *
     * <p>Правила CSV:
     * <ul>
     *   <li>Начальный BOM U+FEFF пропускается. LF, CRLF и CR поддерживаются.</li>
     *   <li>Пустые физические строки вне поля в кавычках пропускаются, включая
     *       строки в конце файла. Строка из пробелов, строка с разделителем и
     *       явно заданное пустое поле в кавычках являются записями.</li>
     *   <li>Внутри двойных кавычек разрешены разделитель и переводы строк;
     *       две двойные кавычки обозначают одну. Кавычка внутри поля без кавычек
     *       и посторонний символ после закрывающей кавычки запрещены.</li>
     *   <li>Пустые поля, включая поле после завершающего разделителя, сохраняются.
     *       Количество колонок не проверяется, пробелы не обрезаются.</li>
     *   <li>Последняя запись обрабатывается и без завершающего перевода строки.
     *       Пустой файл или файл только с BOM/пустыми строками даёт результат 0.</li>
     * </ul>
     *
     * <p>Ограничения проверяются до увеличения буферов сверх заданных пределов.
     * Длина поля измеряется в UTF-16 code units после декодирования CSV:
     * удвоенная кавычка и нормализованный перевод строки занимают по одной единице.
     * Длина записи измеряется в UTF-16 code units исходного CSV после нормализации
     * переводов строк, включая кавычки, разделители и внутренние LF, но без BOM
     * и завершающего перевода строки. Отдельный предел записи ограничивает также
     * число маленьких или пустых полей. Внутренняя память ограничена размером
     * записи и фиксированными буферами ввода, независимо от размера файла.
     * Равенство длины пределу допустимо; отклонение происходит при попытке его
     * превысить. Причина определяется на первом ошибочном символе. Если на нём
     * одновременно нарушены несколько условий, приоритет следующий:
     * RECORD_TOO_LONG, FIELD_TOO_LONG, INVALID_QUOTE. UNCLOSED_QUOTE используется
     * только при EOF, если запись ещё не была отклонена. На этапе восстановления
     * дополнительные ошибки CSV не создают новых отклонений.
     *
     * <p>Ошибка кавычек или превышение любого предела отклоняет текущую запись
     * ровно один раз. Ограниченный исходный префикс и остаток доступны через
     * поток обработчика отклонений. После обработки буферы освобождаются;
     * непрочитанный остаток пропускается без накопления
     * до ближайшего физического перевода строки независимо от кавычек, затем чтение
     * продолжается с новой строки. Если ошибку обнаружили на переводе строки,
     * он уже является границей восстановления. При EOF чтение завершается.
     * Незакрытая кавычка на EOF тоже сообщается как отклонение.
     *
     * <p>После повреждения многострочного CSV границу логической записи нельзя
     * гарантированно восстановить: часть строк может попасть в отклонённую запись,
     * а оставшиеся фрагменты — стать отдельными попытками чтения. Ограничения
     * предотвращают накопление всего файла даже при незакрытой кавычке, но не
     * гарантируют сохранение каждой последующей корректной записи: до обнаружения
     * ошибки она может быть поглощена повреждённым полем.
     *
     * <p>Ошибки декодирования фатальны: недопустимые байты не заменяются символом
     * подстановки. Выбрасывается IOException с путём, кодировкой и физической
     * строкой первого недекодируемого участка, с исходным исключением в cause.
     * Буферизация не должна смещать диагностическую строку вперёд.
     * Декодер сначала отдаёт корректный префикс буфера, затем сообщает ошибку;
     * строка определяется по уже обработанным символам.
     *
     * <p>Ошибки ввода-вывода и исключения любого обработчика прекращают чтение.
     * Исключение обработчика передаётся без подмены. Файл закрывается при любом
     * исходе; ранее выполненные обработчики не откатываются.
     *
     * @param path путь к CSV-файлу, не {@code null}
     * @param charset кодировка, не {@code null}
     * @param delimiter разделитель; двойная кавычка, CR и LF запрещены
     * @param maxFieldLength положительный предел длины одного декодированного поля
     * @param maxRecordLength положительный предел длины исходной записи,
     *                        не меньше {@code maxFieldLength}
     * @param handler обработчик принятых записей, не {@code null}
     * @param rejectionHandler обработчик отклонений, не {@code null}
     * @return число записей, для которых {@code handler} нормально завершился,
     *         включая заголовки; отклонения и пропущенные пустые строки не учитываются.
     *         Это не последнее значение {@code number} и не число сохранённых в БД
     *         объектов. При исключении итоговое число не возвращается; статистику
     *         частичного импорта вызывающий код ведёт в обработчиках
     * @throws NullPointerException если любой ссылочный аргумент равен {@code null}
     * @throws IllegalArgumentException если разделитель запрещён или пределы некорректны
     * @throws IOException при ошибке файла, декодирования или обработчика;
     *                     ошибки структуры CSV сообщаются через {@code rejectionHandler}
     */
    public long read(Path path, Charset charset, char delimiter,
                     int maxFieldLength, int maxRecordLength,
                     RecordHandler handler, RejectionHandler rejectionHandler) throws IOException {
        validateArguments(path, charset, delimiter, maxFieldLength, maxRecordLength, handler, rejectionHandler);
        long accepted = 0;
        long number = 0;
        try (InputStream stream = Files.newInputStream(path)) {
            RejectedTextReader input = new RejectedTextReader(stream, path, charset);
            int c = input.nextUnit();
            if (c == '\uFEFF') c = input.nextUnit();
            while (c != -1) {
                if (c == '\n') {
                    c = input.nextUnit();
                    continue;
                }
                long sourceLine = input.unitLine;
                number++;
                List<String> fields = new ArrayList<>();
                StringBuilder field = new StringBuilder();
                StringBuilder raw = new StringBuilder();
                // 0 — начало поля, 1 — обычное поле, 2 — внутри кавычек, 3 — после кавычки.
                int state = 0;
                long recordLength = 0;
                while (true) {
                    if (c == -1 || (c == '\n' && state != 2)) {
                        if (c == -1 && state == 2) {
                            handleRejectedRecord(input, raw, true, new RejectedRecord(number, sourceLine,
                                    input.line, RejectionReason.UNCLOSED_QUOTE), rejectionHandler);
                        } else {
                            finishField(fields, field);
                            handler.accept(new Record(number, sourceLine, fields));
                            accepted++;
                        }
                        break;
                    }
                    boolean append = state == 2 ? c != '"'
                            : state == 3 ? c == '"' : c != delimiter && c != '"';
                    boolean invalidQuote = (state == 1 && c == '"')
                            || (state == 3 && c != '"' && c != delimiter);
                    RejectionReason reason = checkLimits(recordLength, field.length(), append,
                            invalidQuote, maxFieldLength, maxRecordLength);
                    // Сохраняется только ограниченный префикс, включая первый ошибочный символ.
                    raw.append(input.unitText);
                    if (reason != null) {
                        handleRejectedRecord(input, raw, c == '\n',
                                new RejectedRecord(number, sourceLine, input.unitLine, reason), rejectionHandler);
                        break;
                    }
                    recordLength++;
                    if (append) field.append((char) c);
                    if (state == 2) {
                        if (c == '"') state = 3;
                    } else if (c == delimiter) {
                        finishField(fields, field);
                        state = 0;
                    } else if (c == '"') {
                        state = 2;
                    } else {
                        state = 1;
                    }
                    c = input.nextUnit();
                }
                c = input.nextUnit();
            }
        }
        return accepted;
    }

    /** Проверяет настройки до открытия файла и вызова обработчиков. */
    private static void validateArguments(Path path, Charset charset, char delimiter,
                                          int fieldLimit, int recordLimit,
                                          RecordHandler handler, RejectionHandler rejectionHandler) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(rejectionHandler, "rejectionHandler");
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n') {
            throw new IllegalArgumentException("Invalid delimiter");
        }
        if (fieldLimit <= 0 || recordLimit < fieldLimit) {
            throw new IllegalArgumentException("Require 0 < maxFieldLength <= maxRecordLength");
        }
    }

    /** Проверяет первый ошибочный символ, соблюдая приоритет причин из контракта. */
    private static RejectionReason checkLimits(long recordLength, int fieldLength, boolean append,
                                               boolean invalidQuote, int fieldLimit, int recordLimit) {
        if (recordLength >= recordLimit) return RejectionReason.RECORD_TOO_LONG;
        if (append && fieldLength >= fieldLimit) return RejectionReason.FIELD_TOO_LONG;
        return invalidQuote ? RejectionReason.INVALID_QUOTE : null;
    }

    /** Завершает поле, включая пустое, и переиспользует его ограниченный буфер. */
    private static void finishField(List<String> fields, StringBuilder field) {
        fields.add(field.toString());
        field.setLength(0);
    }

    /** Выдаёт временный поток ошибки и дочитывает остаток после нормального возврата. */
    private static void handleRejectedRecord(RejectedTextReader input, CharSequence prefix,
                                              boolean boundaryReached, RejectedRecord rejection,
                                              RejectionHandler handler) throws IOException {
        RejectedTextReader text = new RejectedTextReader(input, prefix, boundaryReached);
        try {
            handler.accept(rejection, text);
            text.drain();
        } finally {
            text.active = false;
            text.prefix = "";
        }
    }

    /**
     * Внутренний ввод и временное представление отклонённого участка.
     * Корневой экземпляр декодирует фиксированными буферами и считает строки.
     * Представление отдаёт сохранённый префикс и остаток до физического переноса;
     * его закрытие не закрывает файл. Отдельный экземпляр на отклонение исключает
     * повторное использование сохранённой обработчиком ссылки.
     */
    private static final class RejectedTextReader extends Reader {
        private final RejectedTextReader source;
        private final InputStream stream;
        private final Path path;
        private final CharsetDecoder decoder;
        private final ByteBuffer bytes;
        private final CharBuffer chars;
        private CoderResult decodingError;
        private boolean needInput = true;
        private boolean eof;
        private boolean decodedEnd;
        private boolean flushed;
        private int pending = -1;
        private long line = 1;
        private long unitLine = 1;
        private String unitText = "";
        private CharSequence prefix = "";
        private int prefixPosition;
        private boolean boundaryReached;
        private boolean active = true;
        private boolean closed;
        private String tail = "";
        private int tailPosition;

        /** Создаёт буферизованный ввод; ресурсом файла владеет метод read(). */
        private RejectedTextReader(InputStream stream, Path path, Charset charset) {
            this.source = null;
            this.stream = stream;
            this.path = path;
            this.decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            this.bytes = ByteBuffer.allocate(8192);
            this.chars = CharBuffer.allocate(4096);
            bytes.limit(0);
            chars.limit(0);
        }

        /** Создаёт ограниченное представление без копирования исходного префикса. */
        private RejectedTextReader(RejectedTextReader source, CharSequence prefix, boolean boundaryReached) {
            this.source = source;
            this.prefix = prefix;
            this.boundaryReached = boundaryReached;
            this.stream = null;
            this.path = null;
            this.decoder = null;
            this.bytes = null;
            this.chars = null;
        }

        /** Возвращает нормализованный символ, сохраняя исходный CR/LF для отчёта. */
        private int nextUnit() throws IOException {
            unitLine = line;
            int c = pending >= 0 ? pending : nextDecodedChar();
            pending = -1;
            if (c == -1) {
                unitText = "";
                return -1;
            }
            if (c == '\r') {
                line++;
                int next = nextDecodedChar();
                unitText = next == '\n' ? "\r\n" : "\r";
                if (next != '\n') pending = next;
                return '\n';
            }
            unitText = String.valueOf((char) c);
            if (c == '\n') line++;
            return c;
        }

        /** Отдаёт корректные символы перед ошибкой, чтобы её строка не зависела от буфера. */
        private int nextDecodedChar() throws IOException {
            while (!chars.hasRemaining()) {
                if (decodingError != null) {
                    try {
                        decodingError.throwException();
                    } catch (java.nio.charset.CharacterCodingException cause) {
                        throw new IOException(path + ": invalid " + decoder.charset().name()
                                + " at line " + line, cause);
                    }
                }
                if (flushed) return -1;
                if (needInput && !eof) {
                    bytes.compact();
                    int count = stream.read(bytes.array(), bytes.position(), bytes.remaining());
                    if (count == -1) eof = true;
                    else bytes.position(bytes.position() + count);
                    bytes.flip();
                }
                chars.clear();
                CoderResult result;
                if (decodedEnd) {
                    result = decoder.flush(chars);
                    flushed = result.isUnderflow();
                } else {
                    result = decoder.decode(bytes, chars, eof);
                    needInput = result.isUnderflow();
                    decodedEnd = eof && result.isUnderflow();
                }
                if (result.isError()) decodingError = result;
                chars.flip();
            }
            return chars.get();
        }

        /** Читает исходный текст участка; EOF здесь не означает EOF исходного файла. */
        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (!active || closed) throw new IOException("Rejected text is no longer available");
            if (length == 0) return 0;
            int count = 0;
            while (count < length) {
                int c = readRejectedChar();
                if (c == -1) break;
                buffer[offset + count++] = (char) c;
            }
            return count == 0 ? -1 : count;
        }

        /** Читает префикс и потоковый остаток без накопления повреждённой записи. */
        private int readRejectedChar() throws IOException {
            if (prefixPosition < prefix.length()) return prefix.charAt(prefixPosition++);
            if (tailPosition < tail.length()) return tail.charAt(tailPosition++);
            if (boundaryReached) return -1;
            int c = source.nextUnit();
            boundaryReached = c == -1 || c == '\n';
            if (c == -1) return -1;
            tail = source.unitText;
            tailPosition = 1;
            return tail.charAt(0);
        }

        /** Пропускает непрочитанное даже после закрытия представления обработчиком. */
        private void drain() throws IOException {
            prefixPosition = prefix.length();
            while (readRejectedChar() != -1) {
                // Исходный файл читается фиксированными буферами, остаток не сохраняется.
            }
        }

        /** Закрывает только представление, не исходный файл. */
        @Override
        public void close() {
            closed = true;
        }
    }
}
