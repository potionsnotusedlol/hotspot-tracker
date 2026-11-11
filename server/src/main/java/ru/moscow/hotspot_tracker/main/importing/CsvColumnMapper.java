package ru.moscow.hotspot_tracker.main.importing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Контракт FR-IMP-03: настраиваемое сопоставление колонок CSV целевым полям.
 * Контракт: Issue #23. Реализация: Issue #32.
 *
 * <p>Сначала вызывающий код передаёт конфигурацию набора и первую запись-заголовок
 * в {@link #resolve}. Полученный план переиспользуется для записей этого файла.
 * Затем {@link #map} преобразует одну запись за вызов. Состояние файла,
 * накопление записей, чтение CSV и запись в БД не входят в обязанности маппера.
 *
 * <p>Контракт зависит от {@link CsvRecordReader.Record}: в resolve передаётся
 * первая запись-заголовок, в map — план и очередная запись данных. Маппер использует
 * fields(), number() и sourceLine() без повторного разбора CSV. Ридер нумерует непустые логические
 * записи с 1, включая заголовки. Вызывающий код пропускает headerRecords записей:
 * первая содержит имена колонок, вторая, если предусмотрена, — подписи data.mos.ru.
 * Обе записи учитываются в нумерации; они не выдаются как результат маппинга.
 *
 * <p>На каждый файл нужен новый план, даже если конфигурация набора та же:
 * порядок колонок может измениться. Поддержка отсутствующего заголовка, загрузка
 * конфигурации из YAML/JSON и распознавание набора по содержимому не входят в API.
 *
 * <p>Значения остаются строками: пробелы, пустые строки и Unicode сохраняются.
 * Преобразование типов, значения по умолчанию, предметная валидация (FR-IMP-05),
 * нормализация (FR-IMP-06), COPY и upsert (FR-IMP-04) выполняются далее.
 * Целевые имена — ключи согласованной схемы импорта, а не произвольный SQL;
 * их допустимость относительно схемы БД проверяет вызывающий слой.
 */
public final class CsvColumnMapper {

    /**
     * Соответствие одной именованной исходной колонки одному целевому полю.
     * Сравнение имён точное, с учётом регистра, без trim и замены символов.
     *
     * @param sourceColumn исходное имя колонки, не null и не blank
     * @param targetField целевой ключ поля, не null и не blank
     */
    public record ColumnMapping(String sourceColumn, String targetField) {
        /**
         * Проверяет имена, не нормализуя их.
         *
         * @throws NullPointerException если имя равно null
         * @throws IllegalArgumentException если имя пустое или состоит из пробелов
         */
        public ColumnMapping {
            Objects.requireNonNull(sourceColumn, "sourceColumn");
            Objects.requireNonNull(targetField, "targetField");
            requireNonBlank(sourceColumn, "sourceColumn");
            requireNonBlank(targetField, "targetField");
        }
    }

    /**
     * Конфигурация отдельного набора данных, не зависящая от порядка колонок.
     * Все указанные sourceColumn должны присутствовать в заголовке; необязательность
     * значения поля относится к последующей валидации, а не к поиску колонки.
     * Несопоставленные колонки игнорируются, но участвуют в проверке ширины записи.
     *
     * @param datasetId непустой и не blank идентификатор набора, не null
     * @param columns непустой список соответствий без null; targetField уникальны;
     *                одну исходную колонку можно направить в несколько разных полей
     * @param headerRecords число заголовочных записей: 1 либо 2; первая содержит
     *                      имена, вторая при наличии содержит поясняющие подписи
     * @param trailingTechnicalColumn если true, последняя колонка заголовка должна
     *                                иметь имя "", а в каждой записи данных — значение "";
     *                                она исключается из результата только по этой настройке
     */
    public record Options(String datasetId, List<ColumnMapping> columns,
                          int headerRecords, boolean trailingTechnicalColumn) {
        /**
         * Проверяет настройки до анализа заголовка и сохраняет неизменяемую копию списка.
         * Последующая мутация исходного списка не должна менять конфигурацию.
         *
         * @throws NullPointerException если datasetId, список или элемент равен null
         * @throws IllegalArgumentException если datasetId blank, список пуст,
         *         targetField повторяется или headerRecords отличается от 1 и 2
         */
        public Options {
            Objects.requireNonNull(datasetId, "datasetId");
            columns = List.copyOf(columns);
            requireNonBlank(datasetId, "datasetId");
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("columns must not be empty");
            }
            if (headerRecords != 1 && headerRecords != 2) {
                throw new IllegalArgumentException("headerRecords must be 1 or 2");
            }
            var targets = new HashSet<String>();
            for (ColumnMapping column : columns) {
                if (!targets.add(column.targetField())) {
                    throw new IllegalArgumentException("Duplicate target field");
                }
            }
        }
    }

    /**
     * Неизменяемый план сопоставления для конкретного заголовка файла.
     *
     * @param options исходная конфигурация, не null
     * @param header неизменяемый снимок всего заголовка, включая техническую колонку
     * @param sourceIndexes индексы исходных колонок с 0, в порядке options.columns;
     *                      список той же длины, без null, каждый индекс указывает
     *                      на точное sourceColumn соответствующего элемента конфигурации
     */
    public record ResolvedMapping(Options options, List<String> header, List<Integer> sourceIndexes) {
        /**
         * Сохраняет защитные копии списков и проверяет согласованность всего плана.
         * Проверки заголовка такие же, как в resolve. Прямая сборка плана не позволяет
         * обойти проверку индексов, имён, технической колонки или конфигурации.
         *
         * @throws NullPointerException если аргумент или элемент списка равен null
         * @throws ConfigurationException если заголовок или индексы не согласованы с options
         */
        public ResolvedMapping {
            Objects.requireNonNull(options, "options");
            header = List.copyOf(header);
            sourceIndexes = List.copyOf(sourceIndexes);
            List<Integer> expected = resolveIndexes(options, header);
            if (!sourceIndexes.equals(expected)) {
                throw new ConfigurationException(options.datasetId(), ConfigurationFailure.INVALID_PLAN);
            }
        }
    }

    /**
     * Результат одной записи для последующей валидации и загрузки в staging.
     *
     * @param number исходный номер логической записи с 1, включая заголовки и пропуски
     *               номеров из-за отклонений ридера; не перенумеровывается
     * @param sourceLine исходная физическая строка начала записи с 1
     * @param values целевые ключи и исходные строковые значения без null;
     *               порядок обхода соответствует options.columns; пустые строки сохраняются
     */
    public record MappedRecord(long number, long sourceLine, Map<String, String> values) {
        /**
         * Проверяет метаданные и создаёт неизменяемый снимок значений с сохранением порядка.
         *
         * @throws NullPointerException если карта, её ключ или значение равны null
         * @throws IllegalArgumentException если number или sourceLine меньше 1
         */
        public MappedRecord {
            Objects.requireNonNull(values, "values");
            var snapshot = new LinkedHashMap<String, String>();
            values.forEach((key, value) -> snapshot.put(
                    Objects.requireNonNull(key, "targetField"),
                    Objects.requireNonNull(value, "value")));
            requireMetadata(number, sourceLine);
            values = Collections.unmodifiableMap(snapshot);
        }
    }

    /** Причина ошибки настройки, заголовка или вручную составленного плана. */
    public enum ConfigurationFailure {
        /** Заголовок пуст или содержит пустое/blank имя вне технической колонки. */
        INVALID_HEADER,
        /** Нет ожидаемой последней колонки с пустым именем. */
        INVALID_TECHNICAL_COLUMN,
        /** Повторяется непустое имя, даже если эта колонка не используется в маппинге. */
        DUPLICATE_HEADER,
        /** Указанного в конфигурации исходного имени нет в заголовке. */
        MISSING_SOURCE_COLUMN,
        /** Размер списка индексов или соответствие индексов именам некорректны. */
        INVALID_PLAN
    }

    /** Причина отклонения отдельной синтаксически корректной записи CSV. */
    public enum RecordFailure {
        /** Число полей не равно ширине всего исходного заголовка. */
        FIELD_COUNT_MISMATCH,
        /** Ожидаемая последняя техническая колонка содержит непустое значение. */
        NON_EMPTY_TECHNICAL_COLUMN
    }

    /**
     * Фатальная ошибка подготовки плана: запись данных ещё не обрабатывается.
     * После неё план не возвращается; конфигурацию или заголовок необходимо исправить.
     * Сообщение содержит datasetId и причину, но не значения записей данных.
     */
    public static final class ConfigurationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final ConfigurationFailure reason;

        /**
         * Создаёт ошибку с машиночитаемой причиной.
         *
         * @param datasetId идентификатор набора, не null и не blank
         * @param reason причина, не null
         * @throws NullPointerException если аргумент равен null
         * @throws IllegalArgumentException если datasetId blank
         */
        public ConfigurationException(String datasetId, ConfigurationFailure reason) {
            super(configurationMessage(datasetId, reason));
            this.reason = reason;
        }

        /** @return причина ошибки */
        public ConfigurationFailure reason() {
            return reason;
        }
    }

    /**
     * Ошибка одной записи, которую вызывающий код может отклонить и продолжить импорт.
     * map не вызывает обработчики и не пропускает записи самостоятельно.
     * Сообщение содержит datasetId, number, sourceLine и причину, без значений полей.
     * Исходная запись остаётся у вызывающего кода для отчёта FR-IMP-08.
     */
    public static final class RecordMappingException extends Exception {
        private static final long serialVersionUID = 1L;
        private final RecordFailure reason;
        private final long number;
        private final long sourceLine;

        /**
         * Создаёт диагностическую ошибку одной записи.
         *
         * @param datasetId идентификатор набора, не null и не blank
         * @param number исходный номер записи с 1
         * @param sourceLine исходная физическая строка с 1
         * @param reason причина, не null
         * @throws NullPointerException если datasetId или reason равны null
         * @throws IllegalArgumentException если datasetId blank либо номер/строка меньше 1
         */
        public RecordMappingException(String datasetId, long number, long sourceLine, RecordFailure reason) {
            super(recordMessage(datasetId, number, sourceLine, reason));
            this.reason = reason;
            this.number = number;
            this.sourceLine = sourceLine;
        }

        /** @return причина */
        public RecordFailure reason() {
            return reason;
        }

        /** @return исходный номер записи */
        public long number() {
            return number;
        }

        /** @return физическая строка начала записи */
        public long sourceLine() {
            return sourceLine;
        }
    }

    /**
     * Разрешает имена колонок один раз по первому заголовку конкретного файла.
     *
     * <p>Порядок колонок произволен. Весь заголовок проверяется до построения плана:
     * он должен содержать хотя бы одну именованную колонку; null недопустим.
     * При trailingTechnicalColumn последняя колонка должна иметь ровно пустое имя
     * и не участвует в поиске; иначе пустое имя является ошибкой. Остальные имена
     * не blank и уникальны. Все sourceColumn из конфигурации должны существовать.
     * Несопоставленные именованные колонки допустимы. Заголовок не нормализуется.
     *
     * <p>При нескольких нарушениях приоритет: INVALID_HEADER,
     * INVALID_TECHNICAL_COLUMN, DUPLICATE_HEADER, MISSING_SOURCE_COLUMN.
     * При проверке ResolvedMapping INVALID_PLAN идёт после проверок заголовка.
     * Ошибки null проверяются до содержательных проверок. Индексы хранятся в порядке
     * конфигурации. Данные и объекты БД не читаются и не изменяются.
     *
     * @param options настройки набора, не null
     * @param header первая заголовочная запись, не null; number() равен 1,
     *               sourceLine() не менее 1; fields() содержит имена колонок
     * @return неизменяемый согласованный план; не null
     * @throws NullPointerException если аргумент равен null
     * @throws IllegalArgumentException если number() заголовка не равен 1
     *         либо sourceLine() меньше 1; метаданные проверяются до содержимого заголовка
     * @throws ConfigurationException если заголовок не соответствует конфигурации
     */
    public ResolvedMapping resolve(Options options, CsvRecordReader.Record header) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(header, "header");
        requireMetadata(header.number(), header.sourceLine());
        if (header.number() != 1) {
            throw new IllegalArgumentException("Header record number must be 1");
        }
        return new ResolvedMapping(options, header.fields(), resolveIndexes(options, header.fields()));
    }

    /**
     * Сопоставляет одну запись данных по ранее построенному плану.
     *
     * <p>Вызывающий код уже пропустил заголовочные записи. Номер записи должен быть
     * больше mapping.options().headerRecords(); передача заголовка — ошибка вызова.
     * Ширина записи должна совпадать с mapping.header().size(), включая техническую
     * и несопоставленные колонки. Сначала проверяется ширина, затем пустота технической
     * колонки. Даже пробел в технической колонке — непустое значение, которое нельзя
     * молча удалить. Значения остальных несопоставленных колонок игнорируются.
     *
     * <p>Пустые значения сопоставленных полей сохраняются; обязательность значений
     * и их типы здесь не проверяются. Результат содержит ровно настроенные targetField
     * в порядке конфигурации. Метаданные не меняются. Снимки входных данных не могут
     * менять уже выданные результаты. Накопления записей нет: память ограничена
     * планом и результатом текущей записи и не зависит от размера файла.
     *
     * <p>После RecordMappingException вызывающий код может зарегистрировать отказ
     * и вызвать map для следующей записи с тем же планом. Ошибки синтаксиса CSV
     * обрабатываются ридером до вызова map; ошибки обработчика импорт прекращают
     * согласно контракту ридера. Ни ридер, ни обработчики здесь не вызываются.
     *
     * @param mapping неизменяемый план для этого файла, не null
     * @param record запись ридера, не null; number() больше числа заголовков,
     *               sourceLine() не менее 1; fields() содержит исходные значения
     * @return неизменяемый результат с исходными строковыми значениями, не null
     * @throws NullPointerException если mapping или record равен null
     * @throws IllegalArgumentException если метаданные некорректны либо передан номер заголовка
     * @throws RecordMappingException если ширина записи не совпадает с заголовком
     *         либо техническая колонка непустая
     */
    public MappedRecord map(ResolvedMapping mapping, CsvRecordReader.Record record)
            throws RecordMappingException {
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(record, "record");
        requireMetadata(record.number(), record.sourceLine());
        Options options = mapping.options();
        if (record.number() <= options.headerRecords()) {
            throw new IllegalArgumentException("Header record cannot be mapped as data");
        }
        List<String> fields = record.fields();
        if (fields.size() != mapping.header().size()) {
            throw new RecordMappingException(options.datasetId(), record.number(), record.sourceLine(),
                    RecordFailure.FIELD_COUNT_MISMATCH);
        }
        if (options.trailingTechnicalColumn() && !fields.get(fields.size() - 1).isEmpty()) {
            throw new RecordMappingException(options.datasetId(), record.number(), record.sourceLine(),
                    RecordFailure.NON_EMPTY_TECHNICAL_COLUMN);
        }
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < options.columns().size(); i++) {
            values.put(options.columns().get(i).targetField(), fields.get(mapping.sourceIndexes().get(i)));
        }
        return new MappedRecord(record.number(), record.sourceLine(), values);
    }

    /** Проверяет весь заголовок до поиска колонок, сохраняя приоритет ошибок. */
    private static List<Integer> resolveIndexes(Options options, List<String> header) {
        int namedColumns = header.size() - (options.trailingTechnicalColumn() ? 1 : 0);
        if (namedColumns < 1) {
            throw new ConfigurationException(options.datasetId(), ConfigurationFailure.INVALID_HEADER);
        }
        for (int i = 0; i < namedColumns; i++) {
            if (header.get(i).isBlank()) {
                throw new ConfigurationException(options.datasetId(), ConfigurationFailure.INVALID_HEADER);
            }
        }
        if (options.trailingTechnicalColumn() && !header.get(header.size() - 1).isEmpty()) {
            throw new ConfigurationException(options.datasetId(), ConfigurationFailure.INVALID_TECHNICAL_COLUMN);
        }
        var indexesByName = new HashMap<String, Integer>();
        for (int i = 0; i < namedColumns; i++) {
            if (indexesByName.put(header.get(i), i) != null) {
                throw new ConfigurationException(options.datasetId(), ConfigurationFailure.DUPLICATE_HEADER);
            }
        }
        var indexes = new ArrayList<Integer>(options.columns().size());
        for (ColumnMapping column : options.columns()) {
            Integer index = indexesByName.get(column.sourceColumn());
            if (index == null) {
                throw new ConfigurationException(options.datasetId(), ConfigurationFailure.MISSING_SOURCE_COLUMN);
            }
            indexes.add(index);
        }
        return indexes;
    }

    private static void requireNonBlank(String value, String name) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireMetadata(long number, long sourceLine) {
        if (number < 1 || sourceLine < 1) {
            throw new IllegalArgumentException("number and sourceLine must be positive");
        }
    }

    private static String configurationMessage(String datasetId, ConfigurationFailure reason) {
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(reason, "reason");
        requireNonBlank(datasetId, "datasetId");
        return "Dataset " + datasetId + ": " + reason;
    }

    private static String recordMessage(String datasetId, long number, long sourceLine, RecordFailure reason) {
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(reason, "reason");
        requireNonBlank(datasetId, "datasetId");
        requireMetadata(number, sourceLine);
        return "Dataset " + datasetId + ": record " + number + ", source line " + sourceLine + ": " + reason;
    }
}
