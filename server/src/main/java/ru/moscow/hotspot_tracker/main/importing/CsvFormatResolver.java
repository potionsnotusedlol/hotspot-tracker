package ru.moscow.hotspot_tracker.main.importing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Контракт FR-IMP-02: выбор кодировки и разделителя перед запуском CSV-ридера.
 * Выбирает формат по явным настройкам либо ограниченной выборке файла.
 *
 * <p>Каждая настройка задаётся независимо: явная кодировка с автоматическим
 * разделителем, автоматическая кодировка с явным разделителем, обе автоматически
 * или обе явно. Автоматическое определение является ограниченной эвристикой,
 * а не гарантией правильного распознавания произвольного файла.
 *
 * <p>Результат передаётся в {@link CsvRecordReader#read} как charset и delimiter.
 * Компонент не импортирует данные, не удаляет заголовки и последнюю пустую колонку,
 * не запускает обработчики записей и не проверяет предметные значения.
 */
public final class CsvFormatResolver {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final char[] DELIMITERS = {';', ',', '\t', '|'};

    /**
     * Настройки выбора формата и ограничения исследования файла.
     *
     * <p>Пустой Optional означает автоматический выбор. Сам Optional не может
     * быть null. Допустимы только UTF-8 и Windows-1251; сравнение Charset выполняется
     * по эквивалентности кодировок, а не по написанию их алиасов.
     *
     * @param charset явная кодировка либо автоматический выбор
     * @param delimiter явный разделитель либо автоматический выбор;
     *                  двойная кавычка, CR и LF запрещены;
     *                  явный разделитель может отличаться от автоматических кандидатов
     * @param maxProbeBytes максимальное число читаемых байтов префикса файла,
     *                      не менее 4; лимит включает BOM и все буферы опережающего чтения
     * @param maxSampleRecords максимальное число полных непустых логических записей
     *                         для определения разделителя, не менее 2
     */
    public record Options(Optional<Charset> charset, Optional<Character> delimiter,
                          int maxProbeBytes, int maxSampleRecords) {

        /**
         * Создаёт настройки; проверка должна выполняться до открытия файла.
         *
         * @param charset явная кодировка либо автоматический выбор
         * @param delimiter явный разделитель либо автоматический выбор
         * @param maxProbeBytes предел чтения префикса в байтах
         * @param maxSampleRecords предел числа исследуемых записей
         * @throws NullPointerException если любой Optional равен null
         * @throws IllegalArgumentException если кодировка не поддерживается,
         *         разделитель запрещён или ограничения некорректны
         */
        public Options {
            Objects.requireNonNull(charset, "charset");
            Objects.requireNonNull(delimiter, "delimiter");
            if (charset.isPresent() && !charset.get().equals(StandardCharsets.UTF_8)
                    && !charset.get().equals(WINDOWS_1251)) {
                throw new IllegalArgumentException("Only UTF-8 and Windows-1251 are supported");
            }
            if (delimiter.isPresent() && (delimiter.get() == '"'
                    || delimiter.get() == '\r' || delimiter.get() == '\n')) {
                throw new IllegalArgumentException("Invalid delimiter");
            }
            if (maxProbeBytes < 4 || maxSampleRecords < 2) {
                throw new IllegalArgumentException("Sample requires at least 4 bytes and 2 records");
            }
        }
    }

    /**
     * Готовый формат. Происхождение значений позволяет показать выбор пользователю.
     *
     * @param charset поддерживаемая кодировка, не null
     * @param delimiter допустимый разделитель
     * @param charsetSource основание выбора кодировки, не null
     * @param delimiterSource основание выбора разделителя, не null
     */
    public record Format(Charset charset, char delimiter,
                         SelectionSource charsetSource, SelectionSource delimiterSource) {
    }

    /** Основание выбора отдельного параметра формата. */
    public enum SelectionSource {
        /** Значение явно указано вызывающим кодом. */
        EXPLICIT,
        /** UTF-8 определена по начальному BOM. Только для кодировки. */
        BOM,
        /** Значение выбрано по ограниченному префиксу файла. */
        SAMPLE
    }

    /** Причина, по которой формат нельзя выбрать автоматически. */
    public enum FailureReason {
        /** Обнаружен BOM неподдерживаемой кодировки, например UTF-16 или UTF-32. */
        UNSUPPORTED_BOM,
        /** UTF-8 BOM противоречит явно заданной Windows-1251. */
        BOM_CONFLICT,
        /** Исследуемый префикс нельзя строго декодировать выбранной кодировкой. */
        INVALID_ENCODING,
        /** Меньше двух полных непустых записей либо нет подходящего разделителя. */
        INSUFFICIENT_SAMPLE,
        /** Подходят несколько разделителей; требуется явный выбор. */
        AMBIGUOUS_DELIMITER
    }

    /**
     * Диагностическая ошибка выбора формата, отличная от ошибки доступа к файлу.
     * Сообщение должно содержать путь, причину и рекомендацию явно задать формат
     * или увеличить выборку, если это применимо. Причина декодирования сохраняется
     * в cause. Ошибка не содержит весь исследованный текст.
     */
    public static final class FormatResolutionException extends IOException {
        private static final long serialVersionUID = 1L;
        private final FailureReason reason;
        private final long sourceLine;

        /**
         * Создаёт диагностическое исключение.
         *
         * @param path путь файла, не null
         * @param reason причина отказа, не null
         * @param sourceLine строка первого недекодируемого участка с 1 для
         *                   INVALID_ENCODING; 0, если конкретная строка неприменима
         * @param cause исходная ошибка декодирования либо null
         */
        public FormatResolutionException(Path path, FailureReason reason,
                                         long sourceLine, Throwable cause) {
            super(buildErrorMessage(path, reason, sourceLine), cause);
            this.reason = reason;
            this.sourceLine = sourceLine;
        }

        /**
         * Возвращает машиночитаемую причину отказа.
         *
         * @return причина, не null
         */
        public FailureReason reason() {
            return reason;
        }

        /**
         * Возвращает физическую строку диагностики.
         *
         * @return строка с 1 либо 0, если строка неприменима; CRLF считается одним переводом
         */
        public long sourceLine() {
            return sourceLine;
        }
    }

    /**
     * Выбирает формат по явным настройкам и, при необходимости, префиксу файла.
     *
     * <p>Если оба параметра заданы явно, возвращает их с источником EXPLICIT,
     * не открывая файл. Проверка наличия файла, BOM и декодирования в этом режиме
     * остаётся этапу чтения; успешный выбор формата не означает корректность файла.
     *
     * <p>Если хотя бы один параметр автоматический, файл открывается и закрывается
     * внутри вызова при любом исходе. Исследуется не более maxProbeBytes байтов
     * от начала файла, включая опережающее чтение декодера. Память ограничена этим
     * размером и фиксированными буферами; весь файл и все записи не накапливаются.
     * Даже незакрытая кавычка не позволяет выйти за предел исследования.
     * Начальный BOM проверяется до выбора кодировки: UTF-16/32 отклоняются,
     * UTF-8 BOM выбирает UTF-8 либо конфликтует с явно указанной Windows-1251.
     * Если UTF-8 задана явно, её источник остаётся EXPLICIT даже при наличии BOM.
     *
     * <p>Без BOM автоматический выбор сначала строго проверяет UTF-8; если проверка
     * успешна, выбирается UTF-8, включая ASCII-выборку. Иначе строго проверяется
     * Windows-1251; при неуспехе обеих проверок возвращается INVALID_ENCODING
     * с диагностикой Windows-1251. Подстановка повреждённых символов запрещена.
     * Если кодировка задана явно, альтернативная не подбирается при ошибке.
     * Незавершённая последовательность на искусственной границе выборки не является
     * ошибкой: её байты исключаются из анализа, но учитываются в maxProbeBytes.
     * На фактическом EOF такая последовательность является ошибкой.
     *
     * <p>Проверяется весь прочитанный префикс, поэтому достижение maxSampleRecords
     * не отменяет ошибки декодирования оставшихся байтов префикса. При валидном
     * UTF-8 неверная исходная кодировка всё равно возможна; анализ ограниченного
     * префикса не доказывает правильность кодировки оставшейся части файла.
     * Для нестандартных или смешанных данных требуется явная настройка/исправление файла.
     *
     * <p>Автоматические кандидаты разделителя: точка с запятой, запятая, табуляция
     * и вертикальная черта. Для каждого используется один и тот же декодированный
     * префикс и правила кавычек/переводов строк CsvRecordReader. Пустые физические
     * строки вне кавычек игнорируются. Анализируются первые maxSampleRecords полных
     * непустых логических записей для данного кандидата. Запись на фактическом EOF
     * считается полной без перевода строки, если кавычки закрыты. Незавершённая
     * запись на границе выборки не учитывается, чтение за лимит не продолжается.
     *
     * <p>Кандидат подходит, если исследованы минимум две записи, все они имеют
     * одинаковое число полей больше одного и в анализируемой части нет ошибок CSV.
     * Некорректная запись исключает кандидата; восстановление строк здесь не
     * выполняется. Заголовки data.mos.ru участвуют как обычные записи, последняя
     * пустая колонка после разделителя учитывается. Разделители внутри кавычек
     * не учитываются как границы полей. Изменяющееся число колонок допустимо для
     * ридера, но не даёт оснований автоматически выбрать разделитель.
     *
     * <p>При одном подходящем кандидате выбирается он с источником SAMPLE;
     * при нескольких — AMBIGUOUS_DELIMITER, при отсутствии — INSUFFICIENT_SAMPLE.
     * Одноколоночный, пустой файл или файл с одной записью требует явного разделителя.
     * Если разделитель задан явно, его соответствие структуре не проверяется.
     * Сначала разрешается кодировка, затем разделитель: ошибка кодировки имеет приоритет.
     *
     * <p>Метод не сохраняет открытый поток. Ридер впоследствии заново открывает
     * файл с начала; вызывающий код должен обеспечить неизменность файла между
     * определением формата и импортом. Проверка остальных байтов выполняется ридером.
     *
     * @param path путь CSV-файла, не null
     * @param options настройки выбора, не null
     * @return выбранные параметры с основаниями выбора, не null
     * @throws NullPointerException если path или options равен null
     * @throws FormatResolutionException если автоматический выбор невозможен,
     *         BOM конфликтует с настройкой или префикс не декодируется
     * @throws IOException при ошибке открытия, чтения или закрытия файла
     */
    public Format resolve(Path path, Options options) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        if (options.charset().isPresent() && options.delimiter().isPresent()) {
            return new Format(options.charset().get(), options.delimiter().get(),
                    SelectionSource.EXPLICIT, SelectionSource.EXPLICIT);
        }
        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            ByteBuffer sample = readSample(channel, options.maxProbeBytes());
            boolean complete = channel.position() == channel.size();
            int bomLength = detectBom(path, sample);
            Charset charset = resolveCharset(path, options, sample, complete, bomLength);
            String text = decodeSample(path, sample, charset, complete, bomLength);
            char delimiter = options.delimiter().isPresent() ? options.delimiter().get()
                    : resolveDelimiter(path, text, complete, options.maxSampleRecords());
            SelectionSource charsetSource = options.charset().isPresent() ? SelectionSource.EXPLICIT
                    : bomLength > 0 ? SelectionSource.BOM : SelectionSource.SAMPLE;
            return new Format(charset, delimiter, charsetSource,
                    options.delimiter().isPresent() ? SelectionSource.EXPLICIT : SelectionSource.SAMPLE);
        }
    }

    /** Читает префикс без опережающего чтения за байтовый лимит. */
    private static ByteBuffer readSample(SeekableByteChannel channel, int limit) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate((int) Math.min(channel.size(), limit));
        while (bytes.hasRemaining() && channel.read(bytes) != -1) {
            // Канал записывает только в оставшуюся часть ограниченного буфера.
        }
        return bytes.flip();
    }

    /** Возвращает длину UTF-8 BOM; UTF-16/32 отвергаются до декодирования. */
    private static int detectBom(Path path, ByteBuffer bytes) throws FormatResolutionException {
        int size = bytes.remaining();
        if (size >= 2) {
            int first = bytes.get(0) & 255;
            int second = bytes.get(1) & 255;
            if ((first == 255 && second == 254) || (first == 254 && second == 255)
                    || (size >= 4 && first == 0 && second == 0
                    && (bytes.get(2) & 255) == 254 && (bytes.get(3) & 255) == 255)) {
                throw new FormatResolutionException(path, FailureReason.UNSUPPORTED_BOM, 0, null);
            }
        }
        return size >= 3 && (bytes.get(0) & 255) == 239
                && (bytes.get(1) & 255) == 187 && (bytes.get(2) & 255) == 191 ? 3 : 0;
    }

    /** Выбирает кодировку; ошибка явно заданной кодировки не запускает подбор другой. */
    private static Charset resolveCharset(Path path, Options options, ByteBuffer bytes,
                                          boolean complete, int bomLength) throws IOException {
        if (bomLength > 0) {
            if (options.charset().isPresent() && !options.charset().get().equals(StandardCharsets.UTF_8)) {
                throw new FormatResolutionException(path, FailureReason.BOM_CONFLICT, 0, null);
            }
            return StandardCharsets.UTF_8;
        }
        if (options.charset().isPresent()) {
            return options.charset().get();
        }
        try {
            decodeSample(path, bytes, StandardCharsets.UTF_8, complete, 0);
            return StandardCharsets.UTF_8;
        } catch (FormatResolutionException exception) {
            decodeSample(path, bytes, WINDOWS_1251, complete, 0);
            return WINDOWS_1251;
        }
    }

    /** Декодирует весь префикс; неполный символ допустим только на границе выборки. */
    private static String decodeSample(Path path, ByteBuffer sample, Charset charset,
                                       boolean complete, int bomLength) throws FormatResolutionException {
        ByteBuffer bytes = sample.asReadOnlyBuffer();
        bytes.position(bomLength);
        var decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = CharBuffer.allocate(bytes.remaining());
        CoderResult result = decoder.decode(bytes, chars, complete);
        if (result.isError()) {
            chars.flip();
            long line = 1;
            boolean previousCr = false;
            while (chars.hasRemaining()) {
                char c = chars.get();
                if (c == '\r' || (c == '\n' && !previousCr)) line++;
                previousCr = c == '\r';
            }
            try {
                result.throwException();
            } catch (CharacterCodingException cause) {
                throw new FormatResolutionException(path, FailureReason.INVALID_ENCODING, line, cause);
            }
        }
        return chars.flip().toString();
    }

    /** Выбирает ровно одного кандидата, без произвольного разрешения неоднозначности. */
    private static char resolveDelimiter(Path path, String text, boolean complete, int limit)
            throws FormatResolutionException {
        int matches = 0;
        char selected = 0;
        for (char candidate : DELIMITERS) {
            if (evaluateDelimiter(text, complete, limit, candidate)) {
                matches++;
                selected = candidate;
            }
        }
        if (matches != 1) {
            throw new FormatResolutionException(path, matches == 0 ? FailureReason.INSUFFICIENT_SAMPLE
                    : FailureReason.AMBIGUOUS_DELIMITER, 0, null);
        }
        return selected;
    }

    /** Проверяет синтаксис и число полей первых полных непустых записей кандидата. */
    private static boolean evaluateDelimiter(String text, boolean complete, int limit, char delimiter) {
        // 0 — начало поля, 1 — обычное поле, 2 — внутри кавычек, 3 — после кавычки.
        int state = 0, fields = 1, expected = 0, records = 0;
        boolean started = false;
        for (int i = 0; i <= text.length(); i++) {
            boolean eof = i == text.length();
            if (eof && !complete) break;
            char c = eof ? '\n' : text.charAt(i);
            boolean newline = c == '\r' || c == '\n';
            if (eof && state == 2) return false;
            if (newline && state != 2) {
                if (started) {
                    if (fields <= 1 || (expected != 0 && fields != expected)) return false;
                    expected = fields;
                    if (++records == limit) return true;
                }
                state = 0;
                fields = 1;
                started = false;
            } else {
                started = true;
                if (state == 2) {
                    if (c == '"') state = 3;
                } else if (c == delimiter) {
                    fields++;
                    state = 0;
                } else if (c == '"') {
                    if (state == 1) return false;
                    state = 2;
                } else {
                    if (state == 3) return false;
                    state = 1;
                }
            }
            if (!eof && c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        }
        return records >= 2;
    }

    /** Формирует сообщение без включения содержимого CSV. */
    private static String buildErrorMessage(Path path, FailureReason reason, long line) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reason, "reason");
        String advice = switch (reason) {
            case INSUFFICIENT_SAMPLE -> "Specify the delimiter explicitly or increase the sample limits.";
            case AMBIGUOUS_DELIMITER -> "Specify the delimiter explicitly.";
            case UNSUPPORTED_BOM -> "Convert the file to UTF-8 or Windows-1251.";
            case BOM_CONFLICT -> "Use UTF-8 for a file with a UTF-8 BOM.";
            case INVALID_ENCODING -> "Check the encoding setting or repair the file.";
        };
        return path + ": " + reason + (line > 0 ? " at line " + line : "") + ". " + advice;
    }
}
