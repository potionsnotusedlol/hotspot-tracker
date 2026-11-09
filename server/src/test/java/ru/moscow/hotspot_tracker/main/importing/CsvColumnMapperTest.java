package ru.moscow.hotspot_tracker.main.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.ColumnMapping;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.ConfigurationException;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.ConfigurationFailure;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.MappedRecord;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.Options;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.RecordFailure;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.RecordMappingException;
import ru.moscow.hotspot_tracker.main.importing.CsvColumnMapper.ResolvedMapping;

/** Tests the behaviour described in the {@link CsvColumnMapper} contract (FR-IMP-03). */
class CsvColumnMapperTest {

    private static final String DATASET = "wifi-points";

    private final CsvColumnMapper mapper = new CsvColumnMapper();

    private static ColumnMapping column(String source, String target) {
        return new ColumnMapping(source, target);
    }

    private static Options options(ColumnMapping... columns) {
        return new Options(DATASET, List.of(columns), 1, false);
    }

    private static Options optionsWithTechnicalColumn(ColumnMapping... columns) {
        return new Options(DATASET, List.of(columns), 1, true);
    }

    private static CsvRecordReader.Record record(long number, long sourceLine, String... fields) {
        return new CsvRecordReader.Record(number, sourceLine, List.of(fields));
    }

    private static CsvRecordReader.Record header(String... names) {
        return record(1, 1, names);
    }

    private ResolvedMapping plan(Options options, String... names) {
        return mapper.resolve(options, header(names));
    }

    private ConfigurationFailure resolveFailure(Options options, String... names) {
        return catchThrowableOfType(ConfigurationException.class,
                () -> mapper.resolve(options, header(names))).reason();
    }

    private RecordMappingException mapFailure(ResolvedMapping mapping, CsvRecordReader.Record input) {
        return catchThrowableOfType(RecordMappingException.class, () -> mapper.map(mapping, input));
    }

    @Nested
    class ColumnMappingType {

        @Test
        void nullNamesAreRejected() {
            assertThatNullPointerException().isThrownBy(() -> column(null, "target"));
            assertThatNullPointerException().isThrownBy(() -> column("source", null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t "})
        void blankNamesAreRejected(String blank) {
            assertThatIllegalArgumentException().isThrownBy(() -> column(blank, "target"));
            assertThatIllegalArgumentException().isThrownBy(() -> column("source", blank));
        }

        @Test
        void namesAreNotNormalized() {
            ColumnMapping mapping = column(" Source ", " Target ");

            assertThat(mapping.sourceColumn()).isEqualTo(" Source ");
            assertThat(mapping.targetField()).isEqualTo(" Target ");
        }
    }

    @Nested
    class OptionsType {

        private final List<ColumnMapping> columns = List.of(column("a", "x"));

        @Test
        void nullsAreRejected() {
            assertThatNullPointerException().isThrownBy(() -> new Options(null, columns, 1, false));
            assertThatNullPointerException().isThrownBy(() -> new Options(DATASET, null, 1, false));
            assertThatNullPointerException().isThrownBy(
                    () -> new Options(DATASET, Arrays.asList(column("a", "x"), null), 1, false));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void blankDatasetIdIsRejected(String blank) {
            assertThatIllegalArgumentException().isThrownBy(() -> new Options(blank, columns, 1, false));
        }

        @Test
        void emptyColumnListIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Options(DATASET, List.of(), 1, false));
        }

        @Test
        void repeatedTargetFieldIsRejected() {
            List<ColumnMapping> repeated = List.of(column("a", "x"), column("b", "x"));

            assertThatIllegalArgumentException().isThrownBy(() -> new Options(DATASET, repeated, 1, false));
        }

        @Test
        void oneSourceMayFeedSeveralTargets() {
            List<ColumnMapping> shared = List.of(column("a", "x"), column("a", "y"));

            assertThatNoException().isThrownBy(() -> new Options(DATASET, shared, 1, false));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 3})
        void headerRecordsOutsideOneAndTwoAreRejected(int headerRecords) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Options(DATASET, columns, headerRecords, false));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        void oneOrTwoHeaderRecordsAreAccepted(int headerRecords) {
            assertThatNoException().isThrownBy(() -> new Options(DATASET, columns, headerRecords, false));
        }

        @Test
        void columnsAreDefensivelyCopiedAndImmutable() {
            List<ColumnMapping> source = new ArrayList<>(columns);

            Options options = new Options(DATASET, source, 1, false);
            source.add(column("b", "y"));

            assertThat(options.columns()).containsExactly(column("a", "x"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> options.columns().add(column("b", "y")));
        }
    }

    @Nested
    class Resolve {

        @Test
        void nullArgumentsAreRejected() {
            Options options = options(column("a", "x"));

            assertThatNullPointerException().isThrownBy(() -> mapper.resolve(null, header("a")));
            assertThatNullPointerException().isThrownBy(() -> mapper.resolve(options, null));
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 2, 3})
        void headerMustBeTheFirstRecord(long number) {
            Options options = options(column("a", "x"));

            assertThatThrownBy(() -> mapper.resolve(options, record(number, 1, "a")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void headerSourceLineMustBePositive() {
            Options options = options(column("a", "x"));

            assertThatThrownBy(() -> mapper.resolve(options, record(1, 0, "a")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void headerMayStartAfterBlankLines() {
            assertThat(mapper.resolve(options(column("a", "x")), record(1, 5, "a")).sourceIndexes())
                    .containsExactly(0);
        }

        @Test
        void metadataIsCheckedBeforeHeaderContent() {
            Options options = options(column("a", "x"));

            assertThatThrownBy(() -> mapper.resolve(options, record(2, 1, " ")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void indexesFollowConfigurationOrderForAnyColumnOrder() {
            Options options = options(column("a", "x"), column("b", "y"), column("c", "z"));

            assertThat(plan(options, "c", "a", "b").sourceIndexes()).containsExactly(1, 2, 0);
        }

        @Test
        void planKeepsOptionsAndWholeHeader() {
            Options options = optionsWithTechnicalColumn(column("b", "y"));

            ResolvedMapping mapping = plan(options, "a", "b", "");

            assertThat(mapping.options()).isEqualTo(options);
            assertThat(mapping.header()).containsExactly("a", "b", "");
            assertThat(mapping.sourceIndexes()).containsExactly(1);
        }

        @Test
        void unmappedNamedColumnsAreAllowed() {
            assertThat(plan(options(column("b", "y")), "a", "b", "c").sourceIndexes()).containsExactly(1);
        }

        @Test
        void oneSourceFeedingSeveralTargetsResolvesToTheSameIndex() {
            assertThat(plan(options(column("b", "x"), column("b", "y")), "a", "b").sourceIndexes())
                    .containsExactly(1, 1);
        }

        @Test
        void matchingIsCaseSensitive() {
            assertThat(resolveFailure(options(column("Name", "x")), "name"))
                    .isEqualTo(ConfigurationFailure.MISSING_SOURCE_COLUMN);
        }

        @Test
        void headerNamesAreNotTrimmed() {
            assertThat(resolveFailure(options(column("a", "x")), " a "))
                    .isEqualTo(ConfigurationFailure.MISSING_SOURCE_COLUMN);
            assertThat(plan(options(column(" a ", "x")), " a ").sourceIndexes()).containsExactly(0);
        }

        @Test
        void missingSourceColumnIsReported() {
            assertThat(resolveFailure(options(column("a", "x"), column("b", "y")), "a"))
                    .isEqualTo(ConfigurationFailure.MISSING_SOURCE_COLUMN);
        }

        @Test
        void emptyHeaderIsInvalid() {
            assertThat(resolveFailure(options(column("a", "x")))).isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        void blankNameWithoutTechnicalColumnIsInvalid(String blank) {
            assertThat(resolveFailure(options(column("a", "x")), "a", blank))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }

        @Test
        void blankNameBeforeTechnicalColumnIsInvalid() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), "a", "", ""))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }

        @Test
        void headerOfOnlyTechnicalColumnHasNoNamedColumn() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), ""))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }

        @Test
        void technicalColumnIsRequiredWhenConfigured() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), "a", "b"))
                    .isEqualTo(ConfigurationFailure.INVALID_TECHNICAL_COLUMN);
        }

        @Test
        void technicalColumnMustBeExactlyEmpty() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), "a", " "))
                    .isEqualTo(ConfigurationFailure.INVALID_TECHNICAL_COLUMN);
        }

        @Test
        void duplicateIsAnErrorEvenForUnusedColumns() {
            assertThat(resolveFailure(options(column("a", "x")), "a", "b", "b"))
                    .isEqualTo(ConfigurationFailure.DUPLICATE_HEADER);
        }

        @Test
        void namesDifferingInCaseOrSpacesAreNotDuplicates() {
            assertThat(plan(options(column("a", "x")), "a", "A", " a").sourceIndexes()).containsExactly(0);
        }

        @Test
        void invalidHeaderHasPriorityOverTechnicalColumn() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), " ", "a"))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }

        @Test
        void technicalColumnHasPriorityOverDuplicates() {
            assertThat(resolveFailure(optionsWithTechnicalColumn(column("a", "x")), "a", "a"))
                    .isEqualTo(ConfigurationFailure.INVALID_TECHNICAL_COLUMN);
        }

        @Test
        void duplicateHasPriorityOverMissingSource() {
            assertThat(resolveFailure(options(column("x", "t")), "a", "a"))
                    .isEqualTo(ConfigurationFailure.DUPLICATE_HEADER);
        }

        @Test
        void planIsImmutable() {
            ResolvedMapping mapping = plan(options(column("a", "x")), "a");

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> mapping.header().add("b"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> mapping.sourceIndexes().add(0));
        }
    }

    @Nested
    class ResolvedMappingType {

        private final Options options = options(column("a", "x"), column("b", "y"));

        private ConfigurationFailure failure(Options config, List<String> header, List<Integer> indexes) {
            return catchThrowableOfType(ConfigurationException.class,
                    () -> new ResolvedMapping(config, header, indexes)).reason();
        }

        @Test
        void equalsThePlanBuiltByResolve() {
            assertThat(new ResolvedMapping(options, List.of("b", "a"), List.of(1, 0)))
                    .isEqualTo(plan(options, "b", "a"));
        }

        @Test
        void nullsAreRejected() {
            List<String> header = List.of("a", "b");
            List<Integer> indexes = List.of(0, 1);

            assertThatNullPointerException().isThrownBy(() -> new ResolvedMapping(null, header, indexes));
            assertThatNullPointerException().isThrownBy(() -> new ResolvedMapping(options, null, indexes));
            assertThatNullPointerException().isThrownBy(() -> new ResolvedMapping(options, header, null));
            assertThatNullPointerException().isThrownBy(
                    () -> new ResolvedMapping(options, Arrays.asList("a", null), indexes));
            assertThatNullPointerException().isThrownBy(
                    () -> new ResolvedMapping(options, header, Arrays.asList(0, null)));
        }

        @Test
        void listsAreDefensivelyCopied() {
            List<String> header = new ArrayList<>(List.of("a", "b"));
            List<Integer> indexes = new ArrayList<>(List.of(0, 1));

            ResolvedMapping mapping = new ResolvedMapping(options, header, indexes);
            header.set(0, "changed");
            indexes.set(0, 1);

            assertThat(mapping.header()).containsExactly("a", "b");
            assertThat(mapping.sourceIndexes()).containsExactly(0, 1);
        }

        @Test
        void wrongNumberOfIndexesIsAnInvalidPlan() {
            assertThat(failure(options, List.of("a", "b"), List.of(0)))
                    .isEqualTo(ConfigurationFailure.INVALID_PLAN);
            assertThat(failure(options, List.of("a", "b"), List.of(0, 1, 1)))
                    .isEqualTo(ConfigurationFailure.INVALID_PLAN);
        }

        @Test
        void indexPointingToAnotherColumnIsAnInvalidPlan() {
            assertThat(failure(options, List.of("a", "b"), List.of(1, 0)))
                    .isEqualTo(ConfigurationFailure.INVALID_PLAN);
        }

        @Test
        void indexOutsideTheHeaderIsAnInvalidPlan() {
            assertThat(failure(options, List.of("a", "b"), List.of(0, 2)))
                    .isEqualTo(ConfigurationFailure.INVALID_PLAN);
            assertThat(failure(options, List.of("a", "b"), List.of(-1, 1)))
                    .isEqualTo(ConfigurationFailure.INVALID_PLAN);
        }

        @Test
        void headerChecksCannotBeBypassed() {
            assertThat(failure(options, List.of("a", " "), List.of(0, 1)))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
            assertThat(failure(optionsWithTechnicalColumn(column("a", "x")), List.of("a", "b"), List.of(0)))
                    .isEqualTo(ConfigurationFailure.INVALID_TECHNICAL_COLUMN);
            assertThat(failure(options, List.of("a", "b", "b"), List.of(0, 1)))
                    .isEqualTo(ConfigurationFailure.DUPLICATE_HEADER);
            assertThat(failure(options, List.of("a", "c"), List.of(0, 1)))
                    .isEqualTo(ConfigurationFailure.MISSING_SOURCE_COLUMN);
        }

        @Test
        void headerChecksComeBeforeThePlanCheck() {
            assertThat(failure(options, List.of("a", " "), List.of()))
                    .isEqualTo(ConfigurationFailure.INVALID_HEADER);
        }
    }

    @Nested
    class MappedRecordType {

        private final Map<String, String> values = Map.of("x", "1");

        @Test
        void nullsAreRejected() {
            Map<String, String> nullKey = new HashMap<>();
            nullKey.put(null, "1");
            Map<String, String> nullValue = new HashMap<>();
            nullValue.put("x", null);

            assertThatNullPointerException().isThrownBy(() -> new MappedRecord(2, 2, null));
            assertThatNullPointerException().isThrownBy(() -> new MappedRecord(2, 2, nullKey));
            assertThatNullPointerException().isThrownBy(() -> new MappedRecord(2, 2, nullValue));
        }

        @Test
        void numberAndLineMustBePositive() {
            assertThatIllegalArgumentException().isThrownBy(() -> new MappedRecord(0, 1, values));
            assertThatIllegalArgumentException().isThrownBy(() -> new MappedRecord(1, 0, values));
        }

        @Test
        void valuesAreAnImmutableOrderedSnapshot() {
            Map<String, String> source = new LinkedHashMap<>();
            source.put("z", "1");
            source.put("a", "2");

            MappedRecord mapped = new MappedRecord(2, 2, source);
            source.put("b", "3");

            assertThat(mapped.values()).containsOnlyKeys("z", "a");
            assertThat(mapped.values().keySet()).containsExactly("z", "a");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> mapped.values().put("b", "3"));
        }
    }

    @Nested
    class Exceptions {

        @Test
        void configurationExceptionValidatesArguments() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ConfigurationException(null, ConfigurationFailure.INVALID_HEADER));
            assertThatNullPointerException().isThrownBy(() -> new ConfigurationException(DATASET, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ConfigurationException(" ", ConfigurationFailure.INVALID_HEADER));
        }

        @Test
        void configurationExceptionExposesItsReasonAndDataset() {
            ConfigurationException failure = new ConfigurationException(DATASET, ConfigurationFailure.INVALID_PLAN);

            assertThat(failure).isInstanceOf(IllegalArgumentException.class);
            assertThat(failure.reason()).isEqualTo(ConfigurationFailure.INVALID_PLAN);
            assertThat(failure.getMessage()).contains(DATASET);
        }

        @Test
        void recordMappingExceptionValidatesArguments() {
            RecordFailure reason = RecordFailure.FIELD_COUNT_MISMATCH;

            assertThatNullPointerException().isThrownBy(() -> new RecordMappingException(null, 2, 2, reason));
            assertThatNullPointerException().isThrownBy(() -> new RecordMappingException(DATASET, 2, 2, null));
            assertThatIllegalArgumentException().isThrownBy(() -> new RecordMappingException(" ", 2, 2, reason));
            assertThatIllegalArgumentException().isThrownBy(() -> new RecordMappingException(DATASET, 0, 2, reason));
            assertThatIllegalArgumentException().isThrownBy(() -> new RecordMappingException(DATASET, 2, 0, reason));
        }

        @Test
        void recordMappingExceptionIsCheckedAndExposesItsDetails() {
            RecordMappingException failure =
                    new RecordMappingException(DATASET, 42, 57, RecordFailure.NON_EMPTY_TECHNICAL_COLUMN);

            assertThat(failure).isNotInstanceOf(RuntimeException.class);
            assertThat(failure.reason()).isEqualTo(RecordFailure.NON_EMPTY_TECHNICAL_COLUMN);
            assertThat(failure.number()).isEqualTo(42);
            assertThat(failure.sourceLine()).isEqualTo(57);
            assertThat(failure.getMessage()).contains(DATASET).contains("42").contains("57");
        }
    }

    @Nested
    class MapOperation {

        private final ResolvedMapping mapping = plan(options(column("b", "y"), column("a", "x")), "a", "b", "c");

        @Test
        void nullArgumentsAreRejected() {
            assertThatNullPointerException().isThrownBy(() -> mapper.map(null, record(2, 2, "1", "2", "3")));
            assertThatNullPointerException().isThrownBy(() -> mapper.map(mapping, null));
        }

        @Test
        void returnsConfiguredFieldsInConfigurationOrder() throws RecordMappingException {
            MappedRecord mapped = mapper.map(mapping, record(2, 2, "1", "2", "3"));

            assertThat(mapped.values().keySet()).containsExactly("y", "x");
            assertThat(mapped.values()).containsEntry("y", "2").containsEntry("x", "1").hasSize(2);
        }

        @Test
        void keepsOriginalNumberAndLine() throws RecordMappingException {
            MappedRecord mapped = mapper.map(mapping, record(41, 97, "1", "2", "3"));

            assertThat(mapped.number()).isEqualTo(41);
            assertThat(mapped.sourceLine()).isEqualTo(97);
        }

        @Test
        void oneSourceFeedsEveryConfiguredTarget() throws RecordMappingException {
            ResolvedMapping shared = plan(options(column("a", "x"), column("a", "y")), "a");

            assertThat(mapper.map(shared, record(2, 2, "1")).values()).containsEntry("x", "1").containsEntry("y", "1");
        }

        @Test
        void valuesAreKeptExactlyAsRead() throws RecordMappingException {
            MappedRecord mapped = mapper.map(mapping, record(2, 2, "  spaced  ", "", "ignored"));

            assertThat(mapped.values()).containsEntry("x", "  spaced  ").containsEntry("y", "");
        }

        @Test
        void unicodeValuesArePreserved() throws RecordMappingException {
            String value = "\u0418\u043C\u044F \uD83D\uDE00";

            assertThat(mapper.map(mapping, record(2, 2, value, "", "")).values()).containsEntry("x", value);
        }

        @Test
        void resultIsImmutable() throws RecordMappingException {
            MappedRecord mapped = mapper.map(mapping, record(2, 2, "1", "2", "3"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> mapped.values().put("z", "9"));
        }

        @Test
        void headerRecordIsACallerError() {
            assertThatThrownBy(() -> mapper.map(mapping, record(1, 1, "1", "2", "3")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void everyConfiguredHeaderRecordIsACallerError() throws RecordMappingException {
            Options twoHeaders = new Options(DATASET, List.of(column("a", "x")), 2, false);
            ResolvedMapping twoHeaderPlan = plan(twoHeaders, "a");

            assertThatThrownBy(() -> mapper.map(twoHeaderPlan, record(2, 2, "caption")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThat(mapper.map(twoHeaderPlan, record(3, 3, "1")).values()).containsEntry("x", "1");
        }

        @Test
        void sourceLineMustBePositive() {
            assertThatThrownBy(() -> mapper.map(mapping, record(2, 0, "1", "2", "3")))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 4})
        void wrongWidthIsARecordFailure(int width) {
            String[] fields = new String[width];
            Arrays.fill(fields, "v");

            RecordMappingException failure = mapFailure(mapping, record(7, 9, fields));

            assertThat(failure.reason()).isEqualTo(RecordFailure.FIELD_COUNT_MISMATCH);
            assertThat(failure.number()).isEqualTo(7);
            assertThat(failure.sourceLine()).isEqualTo(9);
        }

        @Test
        void failureMessageNamesTheRecordButNotItsValues() {
            RecordMappingException failure = mapFailure(mapping, record(7, 9, "secret-value"));

            assertThat(failure.getMessage()).contains(DATASET).contains("7").contains("9")
                    .doesNotContain("secret-value");
        }

        @Test
        void planIsReusableAfterARecordFailure() throws RecordMappingException {
            mapFailure(mapping, record(2, 2, "too", "short"));

            assertThat(mapper.map(mapping, record(3, 3, "1", "2", "3")).values())
                    .containsEntry("x", "1").containsEntry("y", "2");
        }
    }

    @Nested
    class TechnicalColumn {

        private final ResolvedMapping mapping = plan(optionsWithTechnicalColumn(column("a", "x")), "a", "b", "");

        @Test
        void isExcludedFromTheResult() throws RecordMappingException {
            assertThat(mapper.map(mapping, record(2, 2, "1", "2", "")).values())
                    .containsOnlyKeys("x").containsEntry("x", "1");
        }

        @Test
        void countsTowardsTheRecordWidth() {
            assertThat(mapFailure(mapping, record(2, 2, "1", "2")).reason())
                    .isEqualTo(RecordFailure.FIELD_COUNT_MISMATCH);
        }

        @ParameterizedTest
        @ValueSource(strings = {"value", " "})
        void mustBeExactlyEmptyInEveryRecord(String value) {
            RecordMappingException failure = mapFailure(mapping, record(5, 6, "1", "2", value));

            assertThat(failure.reason()).isEqualTo(RecordFailure.NON_EMPTY_TECHNICAL_COLUMN);
            assertThat(failure.number()).isEqualTo(5);
            assertThat(failure.sourceLine()).isEqualTo(6);
        }

        @Test
        void widthIsCheckedBeforeTheTechnicalColumn() {
            assertThat(mapFailure(mapping, record(2, 2, "1", "2", "3", "4")).reason())
                    .isEqualTo(RecordFailure.FIELD_COUNT_MISMATCH);
        }

        @Test
        void isOnlyDroppedWhenConfigured() throws RecordMappingException {
            ResolvedMapping without = plan(options(column("a", "x")), "a", "b");

            assertThat(mapper.map(without, record(2, 2, "1", "not technical")).values())
                    .containsOnlyKeys("x");
            assertThat(mapFailure(without, record(3, 3, "1", "2", "")).reason())
                    .isEqualTo(RecordFailure.FIELD_COUNT_MISMATCH);
        }
    }

    @Nested
    class WithCsvRecordReader {

        @TempDir
        Path directory;

        @Test
        void mapsADataMosRuFileReadByTheReader() throws IOException {
            String content = "\"id\";\"Name\";\"Skip\";\r\n"
                    + "\"\u041A\u043E\u0434\";\"\u0418\u043C\u044F\";\"-\";\r\n"
                    + "\"1\";\"Point; A\";\"z\";\r\n"
                    + "\r\n"
                    + "\"2\";\"\";\"z\";\r\n";
            Path path = Files.writeString(directory.resolve("input.csv"), content, StandardCharsets.UTF_8);
            Options options = new Options(DATASET, List.of(column("Name", "name"), column("id", "externalId")), 2, true);
            List<CsvRecordReader.Record> records = new ArrayList<>();

            new CsvRecordReader().read(path, StandardCharsets.UTF_8, ';', 1_000, 10_000, records::add,
                    (rejection, text) -> { });

            ResolvedMapping mapping = mapper.resolve(options, records.get(0));
            List<MappedRecord> mapped = new ArrayList<>();
            for (CsvRecordReader.Record input : records.subList(options.headerRecords(), records.size())) {
                try {
                    mapped.add(mapper.map(mapping, input));
                } catch (RecordMappingException failure) {
                    throw new AssertionError(failure);
                }
            }

            assertThat(mapped).hasSize(2);
            assertThat(mapped.get(0).number()).isEqualTo(3);
            assertThat(mapped.get(0).sourceLine()).isEqualTo(3);
            assertThat(mapped.get(0).values().keySet()).containsExactly("name", "externalId");
            assertThat(mapped.get(0).values()).containsEntry("name", "Point; A").containsEntry("externalId", "1");
            assertThat(mapped.get(1).number()).isEqualTo(4);
            assertThat(mapped.get(1).sourceLine()).isEqualTo(5);
            assertThat(mapped.get(1).values()).containsEntry("name", "").containsEntry("externalId", "2");
        }
    }
}
