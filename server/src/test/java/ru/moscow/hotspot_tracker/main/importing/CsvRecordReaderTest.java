package ru.moscow.hotspot_tracker.main.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.moscow.hotspot_tracker.main.importing.CsvRecordReader.RejectedRecord;
import ru.moscow.hotspot_tracker.main.importing.CsvRecordReader.RejectionReason;

/** Tests the behaviour described in the {@link CsvRecordReader} contract (FR-IMP-01/05/08). */
class CsvRecordReaderTest {

    private static final int NO_LIMIT = 10_000_000;
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    @TempDir
    Path directory;

    private final CsvRecordReader reader = new CsvRecordReader();

    /** A rejection together with the rejected text the handler read from the stream. */
    private record Rejected(RejectedRecord rejection, String text) {
    }

    /** Everything one read call produced: the return value and handler calls in order. */
    private record Result(long count, List<Object> events) {

        List<CsvRecordReader.Record> records() {
            return events.stream()
                    .filter(CsvRecordReader.Record.class::isInstance)
                    .map(CsvRecordReader.Record.class::cast)
                    .toList();
        }

        List<Rejected> rejections() {
            return events.stream().filter(Rejected.class::isInstance).map(Rejected.class::cast).toList();
        }
    }

    private static CsvRecordReader.Record record(long number, long sourceLine, String... fields) {
        return new CsvRecordReader.Record(number, sourceLine, List.of(fields));
    }

    private static Rejected rejected(long number, long sourceLine, long errorLine, RejectionReason reason, String text) {
        return new Rejected(new RejectedRecord(number, sourceLine, errorLine, reason), text);
    }

    private Path file(String content) throws IOException {
        return file(content, StandardCharsets.UTF_8);
    }

    private Path file(String content, Charset charset) throws IOException {
        return Files.write(directory.resolve("input.csv"), content.getBytes(charset));
    }

    private Path file(byte[] content) throws IOException {
        return Files.write(directory.resolve("input.csv"), content);
    }

    private static String readAll(Reader text) throws IOException {
        StringWriter writer = new StringWriter();
        text.transferTo(writer);
        return writer.toString();
    }

    private Result read(String content) throws IOException {
        return read(content, ',', NO_LIMIT, NO_LIMIT);
    }

    private Result read(String content, char delimiter) throws IOException {
        return read(content, delimiter, NO_LIMIT, NO_LIMIT);
    }

    private Result read(String content, int maxFieldLength, int maxRecordLength) throws IOException {
        return read(content, ',', maxFieldLength, maxRecordLength);
    }

    private Result read(String content, char delimiter, int maxFieldLength, int maxRecordLength) throws IOException {
        return read(file(content), StandardCharsets.UTF_8, delimiter, maxFieldLength, maxRecordLength);
    }

    private Result read(Path path, Charset charset, char delimiter, int maxFieldLength, int maxRecordLength)
            throws IOException {
        List<Object> events = new ArrayList<>();
        long count = reader.read(path, charset, delimiter, maxFieldLength, maxRecordLength, events::add,
                (rejection, text) -> events.add(new Rejected(rejection, readAll(text))));
        return new Result(count, events);
    }

    private long readIgnoringOutput(Path path, char delimiter, int maxFieldLength, int maxRecordLength)
            throws IOException {
        return reader.read(path, StandardCharsets.UTF_8, delimiter, maxFieldLength, maxRecordLength,
                record -> { }, (rejection, text) -> { });
    }

    @Nested
    class Arguments {

        @Test
        void nullArgumentsAreRejected() throws IOException {
            Path path = file("a\n");
            CsvRecordReader.RecordHandler onRecord = record -> { };
            CsvRecordReader.RejectionHandler onRejection = (rejection, text) -> { };

            assertThatNullPointerException().isThrownBy(() -> reader.read(
                    null, StandardCharsets.UTF_8, ',', 10, 10, onRecord, onRejection));
            assertThatNullPointerException().isThrownBy(() -> reader.read(
                    path, null, ',', 10, 10, onRecord, onRejection));
            assertThatNullPointerException().isThrownBy(() -> reader.read(
                    path, StandardCharsets.UTF_8, ',', 10, 10, null, onRejection));
            assertThatNullPointerException().isThrownBy(() -> reader.read(
                    path, StandardCharsets.UTF_8, ',', 10, 10, onRecord, null));
        }

        @ParameterizedTest
        @ValueSource(chars = {'"', '\r', '\n'})
        void forbiddenDelimitersAreRejected(char delimiter) throws IOException {
            Path path = file("a\n");

            assertThatIllegalArgumentException().isThrownBy(() -> readIgnoringOutput(path, delimiter, 10, 10));
        }

        @ParameterizedTest
        @CsvSource({"0, 10", "-1, 10", "10, 0", "10, -1", "10, 9"})
        void invalidLimitsAreRejected(int maxFieldLength, int maxRecordLength) throws IOException {
            Path path = file("a\n");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> readIgnoringOutput(path, ',', maxFieldLength, maxRecordLength));
        }

        @Test
        void recordLimitMayEqualFieldLimit() throws IOException {
            assertThat(readIgnoringOutput(file("a\n"), ',', 10, 10)).isEqualTo(1);
        }
    }

    @Nested
    class RecordType {

        @Test
        void fieldsAreDefensivelyCopied() {
            List<String> source = new ArrayList<>(List.of("a"));

            CsvRecordReader.Record record = new CsvRecordReader.Record(1, 1, source);
            source.add("b");

            assertThat(record.fields()).containsExactly("a");
        }

        @Test
        void fieldsAreImmutable() {
            CsvRecordReader.Record record = record(1, 1, "a");

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> record.fields().add("b"));
        }

        @Test
        void nullFieldsAreRejected() {
            List<String> withNull = new ArrayList<>();
            withNull.add(null);

            assertThatNullPointerException().isThrownBy(() -> new CsvRecordReader.Record(1, 1, null));
            assertThatNullPointerException().isThrownBy(() -> new CsvRecordReader.Record(1, 1, withNull));
        }
    }

    @Nested
    class Parsing {

        @ParameterizedTest
        @ValueSource(chars = {',', ';', '\t', '|'})
        void splitsFieldsByTheGivenDelimiter(char delimiter) throws IOException {
            Result result = read("a" + delimiter + "b\n", delimiter);

            assertThat(result.events()).containsExactly(record(1, 1, "a", "b"));
            assertThat(result.count()).isEqualTo(1);
        }

        @Test
        void otherDelimitersAreOrdinaryCharacters() throws IOException {
            assertThat(read("a;b,c\n", ',').records()).containsExactly(record(1, 1, "a;b", "c"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"\n", "\r\n", "\r"})
        void supportsEveryLineEnding(String lineEnding) throws IOException {
            Result result = read("a" + lineEnding + "b" + lineEnding);

            assertThat(result.records()).containsExactly(record(1, 1, "a"), record(2, 2, "b"));
        }

        @Test
        void supportsMixedLineEndings() throws IOException {
            assertThat(read("a\nb\r\nc\rd").records()).containsExactly(
                    record(1, 1, "a"), record(2, 2, "b"), record(3, 3, "c"), record(4, 4, "d"));
        }

        @Test
        void lastRecordDoesNotNeedLineBreak() throws IOException {
            Result result = read("a,b\nc,d");

            assertThat(result.records()).containsExactly(record(1, 1, "a", "b"), record(2, 2, "c", "d"));
            assertThat(result.count()).isEqualTo(2);
        }

        @Test
        void leadingBomIsSkipped() throws IOException {
            assertThat(read("\uFEFFid,name\n").records()).containsExactly(record(1, 1, "id", "name"));
        }

        @Test
        void bomInsideTheFileIsData() throws IOException {
            assertThat(read("a\n\uFEFFb\n").records()).containsExactly(record(1, 1, "a"), record(2, 2, "\uFEFFb"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "\uFEFF", "\n", "\r\n\r\n", "\uFEFF\n\n"})
        void fileWithoutRecordsGivesZero(String content) throws IOException {
            Result result = read(content);

            assertThat(result.count()).isZero();
            assertThat(result.events()).isEmpty();
        }

        @Test
        void blankLinesAreSkippedButCountedAsSourceLines() throws IOException {
            Result result = read("a\n\n\r\nb\n\n");

            assertThat(result.records()).containsExactly(record(1, 1, "a"), record(2, 4, "b"));
            assertThat(result.count()).isEqualTo(2);
        }

        @Test
        void lineOfSpacesIsARecord() throws IOException {
            assertThat(read("   \n").records()).containsExactly(record(1, 1, "   "));
        }

        @Test
        void lineWithOnlyDelimiterIsARecord() throws IOException {
            assertThat(read(",\n").records()).containsExactly(record(1, 1, "", ""));
        }

        @Test
        void explicitEmptyQuotedFieldIsARecord() throws IOException {
            assertThat(read("\"\"\n").records()).containsExactly(record(1, 1, ""));
        }

        @Test
        void emptyFieldsArePreserved() throws IOException {
            assertThat(read("a,,b,\n,c\n").records())
                    .containsExactly(record(1, 1, "a", "", "b", ""), record(2, 2, "", "c"));
        }

        @Test
        void trailingDelimiterAtEndOfFileGivesEmptyField() throws IOException {
            assertThat(read("a,").records()).containsExactly(record(1, 1, "a", ""));
        }

        @Test
        void spacesAreNotTrimmed() throws IOException {
            assertThat(read(" a , b \n").records()).containsExactly(record(1, 1, " a ", " b "));
            assertThat(read("\" a \",\" b \"\n").records()).containsExactly(record(1, 1, " a ", " b "));
        }

        @Test
        void columnCountIsNotChecked() throws IOException {
            assertThat(read("a,b\nc\nd,e,f\n").records()).containsExactly(
                    record(1, 1, "a", "b"), record(2, 2, "c"), record(3, 3, "d", "e", "f"));
        }

        @Test
        void quotedFieldMayContainDelimiter() throws IOException {
            assertThat(read("\"a,b\",c\n").records()).containsExactly(record(1, 1, "a,b", "c"));
        }

        @Test
        void doubledQuoteIsDecodedToOneQuote() throws IOException {
            assertThat(read("\"a \"\"quoted\"\" b\",\"\"\"\"\n").records())
                    .containsExactly(record(1, 1, "a \"quoted\" b", "\""));
        }

        @Test
        void lineBreaksInsideQuotesBecomeLf() throws IOException {
            assertThat(read("\"w\r\nx\ry\nz\"\n").records()).containsExactly(record(1, 1, "w\nx\ny\nz"));
        }

        @Test
        void lineBreaksInsideQuotesAreCountedAsSourceLines() throws IOException {
            assertThat(read("\"w\r\nx\ry\nz\"\nnext\n").records())
                    .containsExactly(record(1, 1, "w\nx\ny\nz"), record(2, 5, "next"));
        }

        @Test
        void blankLineInsideQuotesIsData() throws IOException {
            assertThat(read("\"a\n\nb\"\n").records()).containsExactly(record(1, 1, "a\n\nb"));
        }

        @Test
        void quotedFieldAtEndOfFileIsComplete() throws IOException {
            assertThat(read("a,\"b\"").records()).containsExactly(record(1, 1, "a", "b"));
        }

        @Test
        void headersOfDataMosRuFileArePassedAsRecordsAndTrailingColumnIsKept() throws IOException {
            String content = "\"id\";\"Name\";\r\n"
                    + "\"\u041A\u043E\u0434\";\"\u0418\u043C\u044F\";\r\n"
                    + "\"1\";\"Point; A\";\r\n";

            Result result = read(content, ';');

            assertThat(result.records()).containsExactly(
                    record(1, 1, "id", "Name", ""),
                    record(2, 2, "\u041A\u043E\u0434", "\u0418\u043C\u044F", ""),
                    record(3, 3, "1", "Point; A", ""));
            assertThat(result.count()).isEqualTo(3);
        }

        @Test
        void decodesWindows1251() throws IOException {
            Path path = file("\u0418\u043C\u044F,\u0451\n", WINDOWS_1251);

            assertThat(read(path, WINDOWS_1251, ',', NO_LIMIT, NO_LIMIT).records())
                    .containsExactly(record(1, 1, "\u0418\u043C\u044F", "\u0451"));
        }

        @Test
        void decodesUtf8() throws IOException {
            assertThat(read("\u0418\u043C\u044F,\uD83D\uDE00\n").records())
                    .containsExactly(record(1, 1, "\u0418\u043C\u044F", "\uD83D\uDE00"));
        }
    }

    @Nested
    class Numbering {

        @Test
        void recordsAndRejectionsArriveInFileOrderAndRejectionsTakeANumber() throws IOException {
            Result result = read("id,name\n\n1,a\"b\n2,ok\n");

            assertThat(result.events()).containsExactly(
                    record(1, 1, "id", "name"),
                    rejected(2, 3, 3, RejectionReason.INVALID_QUOTE, "1,a\"b\n"),
                    record(3, 4, "2", "ok"));
        }

        @Test
        void returnValueCountsOnlyAcceptedRecords() throws IOException {
            assertThat(read("id\n\n1\"\n2\n3\"\n").count()).isEqualTo(2);
        }
    }

    @Nested
    class Limits {

        @Test
        void fieldOfExactlyTheLimitIsAccepted() throws IOException {
            assertThat(read("abc,de\n", 3, NO_LIMIT).records()).containsExactly(record(1, 1, "abc", "de"));
        }

        @Test
        void fieldOverTheLimitIsRejected() throws IOException {
            assertThat(read("abcd\nok\n", 3, NO_LIMIT).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.FIELD_TOO_LONG, "abcd\n"),
                    record(2, 2, "ok"));
        }

        @Test
        void quotedFieldOverTheLimitIsRejected() throws IOException {
            assertThat(read("\"abcd\"\nok\n", 3, NO_LIMIT).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.FIELD_TOO_LONG, "\"abcd\"\n"),
                    record(2, 2, "ok"));
        }

        @Test
        void fieldLimitAppliesToEachFieldSeparately() throws IOException {
            assertThat(read("abc,def,ghi\n", 3, NO_LIMIT).records())
                    .containsExactly(record(1, 1, "abc", "def", "ghi"));
        }

        @Test
        void doubledQuoteCountsAsOneInFieldLength() throws IOException {
            assertThat(read("\"a\"\"b\"\n", 3, NO_LIMIT).records()).containsExactly(record(1, 1, "a\"b"));
            assertThat(read("\"a\"\"b\"\n", 2, NO_LIMIT).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.FIELD_TOO_LONG);
        }

        @Test
        void crLfInsideQuotesCountsAsOneInFieldLength() throws IOException {
            assertThat(read("\"a\r\nb\"\n", 3, NO_LIMIT).records()).containsExactly(record(1, 1, "a\nb"));
        }

        @Test
        void fieldLengthIsMeasuredInUtf16CodeUnits() throws IOException {
            assertThat(read("\uD83D\uDE00\n", 2, NO_LIMIT).records()).containsExactly(record(1, 1, "\uD83D\uDE00"));
            assertThat(read("\uD83D\uDE00\n", 1, NO_LIMIT).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.FIELD_TOO_LONG);
        }

        @Test
        void recordOfExactlyTheLimitIsAccepted() throws IOException {
            assertThat(read("\"a\",b\n", 2, 5).records()).containsExactly(record(1, 1, "a", "b"));
        }

        @Test
        void recordLengthIncludesQuotesAndDelimiters() throws IOException {
            assertThat(read("\"a\",b\nok\n", 2, 4).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.RECORD_TOO_LONG, "\"a\",b\n"),
                    record(2, 2, "ok"));
        }

        @Test
        void recordLengthCountsNormalizedLineBreaksInsideQuotes() throws IOException {
            assertThat(read("\"a\r\nb\"\n", 3, 5).records()).containsExactly(record(1, 1, "a\nb"));
            assertThat(read("\"a\r\nb\"\n", 3, 4).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.RECORD_TOO_LONG);
        }

        @Test
        void recordLengthExcludesBomAndTerminatingLineBreak() throws IOException {
            assertThat(read("\uFEFFabc\r\n", 3, 3).records()).containsExactly(record(1, 1, "abc"));
        }

        @Test
        void recordLimitAlsoBoundsTheNumberOfEmptyFields() throws IOException {
            assertThat(read(",,,,,\n", 1, 5).records()).containsExactly(record(1, 1, "", "", "", "", "", ""));
            assertThat(read(",,,,,\n", 1, 4).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.RECORD_TOO_LONG);
        }

        @Test
        void recordLimitHasPriorityOverFieldLimit() throws IOException {
            assertThat(read("abcd\n", 3, 3).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.RECORD_TOO_LONG);
        }

        @Test
        void recordLimitHasPriorityOverInvalidQuote() throws IOException {
            assertThat(read("abc\"\n", 3, 3).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.RECORD_TOO_LONG);
        }

        @Test
        void reasonIsTakenFromTheFirstBadCharacter() throws IOException {
            assertThat(read("ab\"\n", 1, NO_LIMIT).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.FIELD_TOO_LONG);
            assertThat(read("a\"bcd\n", 2, NO_LIMIT).rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.INVALID_QUOTE);
        }

        @Test
        void limitsAlwaysWinOverUnclosedQuote() throws IOException {
            assertThat(read("\"abcd", 3, NO_LIMIT).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.FIELD_TOO_LONG, "\"abcd"));
        }

        @Test
        void limitKeepsLinesAfterUnclosedQuote() throws IOException {
            assertThat(read("1,\"aaaaaa\n2,ok\n3,ok\n", 4, NO_LIMIT).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.FIELD_TOO_LONG, "1,\"aaaaaa\n"),
                    record(2, 2, "2", "ok"),
                    record(3, 3, "3", "ok"));
        }
    }

    @Nested
    class Rejections {

        @Test
        void quoteInsideUnquotedFieldIsRejected() throws IOException {
            Result result = read("1,a\"b\n2,ok\n");

            assertThat(result.events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.INVALID_QUOTE, "1,a\"b\n"),
                    record(2, 2, "2", "ok"));
            assertThat(result.count()).isEqualTo(1);
        }

        @Test
        void quoteAfterSpaceIsInsideUnquotedField() throws IOException {
            assertThat(read(" \"a\"\n").rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.INVALID_QUOTE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"a\"b,c\n", "\"a\" ,c\n"})
        void characterAfterClosingQuoteIsRejected(String content) throws IOException {
            assertThat(read(content).events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.INVALID_QUOTE, content));
        }

        @Test
        void unclosedQuoteIsReportedAtEndOfFile() throws IOException {
            Result result = read("id,name\n1,\"unclosed\nvalue");

            assertThat(result.events()).containsExactly(
                    record(1, 1, "id", "name"),
                    rejected(2, 2, 3, RejectionReason.UNCLOSED_QUOTE, "1,\"unclosed\nvalue"));
            assertThat(result.count()).isEqualTo(1);
        }

        @Test
        void unclosedQuoteWithoutLimitsAbsorbsFollowingLines() throws IOException {
            Result result = read("1,\"x\n2,ok\n3,ok");

            assertThat(result.events()).containsExactly(
                    rejected(1, 1, 3, RejectionReason.UNCLOSED_QUOTE, "1,\"x\n2,ok\n3,ok"));
            assertThat(result.count()).isZero();
        }

        @Test
        void errorLineIsTheLineWhereTheErrorWasFound() throws IOException {
            assertThat(read("\"a\nb\"c,d\nok\n").events()).containsExactly(
                    rejected(1, 1, 2, RejectionReason.INVALID_QUOTE, "\"a\nb\"c,d\n"),
                    record(2, 3, "ok"));
        }

        @Test
        void recoveryStopsAtTheNextLineBreakRegardlessOfQuotes() throws IOException {
            assertThat(read("a\"b,\"c\nnext\n").events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.INVALID_QUOTE, "a\"b,\"c\n"),
                    record(2, 2, "next"));
        }

        @Test
        void furtherErrorsDuringRecoveryDoNotCreateRejections() throws IOException {
            assertThat(read("a\"b\"c\"d\"\nnext\n").events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.INVALID_QUOTE, "a\"b\"c\"d\"\n"),
                    record(2, 2, "next"));
        }

        @Test
        void errorFoundOnLineBreakUsesItAsTheRecoveryBoundary() throws IOException {
            Result result = read("\"abc\ndef\"\nz\n", 3, NO_LIMIT);

            assertThat(result.rejections()).extracting(Rejected::text).containsExactly("\"abc\n", "def\"\n");
            assertThat(result.rejections()).extracting(r -> r.rejection().reason())
                    .containsExactly(RejectionReason.FIELD_TOO_LONG, RejectionReason.INVALID_QUOTE);
            assertThat(result.rejections()).extracting(r -> r.rejection().sourceLine()).containsExactly(1L, 2L);
            assertThat(result.records()).containsExactly(record(3, 3, "z"));
        }

        @Test
        void errorOnLastLineEndsTheRead() throws IOException {
            Result result = read("ok\na\"b");

            assertThat(result.events()).containsExactly(
                    record(1, 1, "ok"),
                    rejected(2, 2, 2, RejectionReason.INVALID_QUOTE, "a\"b"));
            assertThat(result.count()).isEqualTo(1);
        }
    }

    @Nested
    class RejectedText {

        @Test
        void keepsSourceTextIncludingQuotesDelimitersAndCrLf() throws IOException {
            assertThat(read("1;\"x\";a\"b;c\r\nnext\r\n", ';').rejections()).extracting(Rejected::text)
                    .containsExactly("1;\"x\";a\"b;c\r\n");
        }

        @Test
        void keepsCrLineBreak() throws IOException {
            Result result = read("a\"b\rnext");

            assertThat(result.rejections()).extracting(Rejected::text).containsExactly("a\"b\r");
            assertThat(result.records()).containsExactly(record(2, 2, "next"));
        }

        @Test
        void keepsOriginalLineBreaksInsideDamagedRecord() throws IOException {
            assertThat(read("\"a\r\nb\rc\"d\n").rejections()).extracting(Rejected::text)
                    .containsExactly("\"a\r\nb\rc\"d\n");
        }

        @Test
        void excludesBom() throws IOException {
            assertThat(read("\uFEFFa\"b\n").rejections()).extracting(Rejected::text).containsExactly("a\"b\n");
        }

        @Test
        void doesNotAddLineBreakAtEndOfFile() throws IOException {
            assertThat(read("a\"b").rejections()).extracting(Rejected::text).containsExactly("a\"b");
        }

        @Test
        void givesAccessToTextLongerThanTheLimits() throws IOException {
            String longLine = "x".repeat(100_000);

            Result result = read(longLine + "\nnext\n", 10, 10);

            assertThat(result.rejections()).extracting(Rejected::text).containsExactly(longLine + "\n");
            assertThat(result.records()).containsExactly(record(2, 2, "next"));
        }

        @Test
        void handlerMayIgnoreTheText() throws IOException {
            List<CsvRecordReader.Record> records = new ArrayList<>();

            long count = reader.read(file("a\"b,c\nnext\n"), StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT,
                    records::add, (rejection, text) -> { });

            assertThat(records).containsExactly(record(2, 2, "next"));
            assertThat(count).isEqualTo(1);
        }

        @Test
        void handlerMayReadPartOfTheText() throws IOException {
            List<CsvRecordReader.Record> records = new ArrayList<>();
            StringBuilder prefix = new StringBuilder();

            reader.read(file("a\"bcdef\nnext\n"), StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, records::add,
                    (rejection, text) -> {
                        char[] part = new char[3];
                        prefix.append(part, 0, text.read(part));
                    });

            assertThat(prefix).hasToString("a\"b");
            assertThat(records).containsExactly(record(2, 2, "next"));
        }

        @Test
        void endOfTextIsNotEndOfFile() throws IOException {
            assertThat(read("a\"\nb\"\nnext\n").events()).containsExactly(
                    rejected(1, 1, 1, RejectionReason.INVALID_QUOTE, "a\"\n"),
                    rejected(2, 2, 2, RejectionReason.INVALID_QUOTE, "b\"\n"),
                    record(3, 3, "next"));
        }

        @Test
        void closingTheTextDoesNotCloseTheFile() throws IOException {
            List<CsvRecordReader.Record> records = new ArrayList<>();

            reader.read(file("a\"b\nnext\n"), StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, records::add,
                    (rejection, text) -> text.close());

            assertThat(records).containsExactly(record(2, 2, "next"));
        }

        @Test
        void textIsNotAvailableAfterTheHandlerReturns() throws IOException {
            Reader[] saved = new Reader[1];

            reader.read(file("a\"b\nnext\n"), StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, record -> { },
                    (rejection, text) -> saved[0] = text);

            // The contract only says the stream becomes invalid: it must not give out file data.
            int character;
            try {
                character = saved[0].read();
            } catch (IOException invalid) {
                return;
            }
            assertThat(character).isEqualTo(-1);
        }
    }

    @Nested
    class Failures {

        @Test
        void missingFileFailsWithoutCallingHandlers() {
            List<Object> events = new ArrayList<>();

            assertThatThrownBy(() -> reader.read(directory.resolve("missing.csv"), StandardCharsets.UTF_8, ',',
                    NO_LIMIT, NO_LIMIT, events::add, (rejection, text) -> events.add(rejection)))
                    .isInstanceOf(IOException.class);
            assertThat(events).isEmpty();
        }

        @Test
        void recordHandlerIOExceptionStopsReadingAndIsPassedUnchanged() throws IOException {
            IOException failure = new IOException("stop");
            List<CsvRecordReader.Record> seen = new ArrayList<>();
            Path path = file("a\nb\n");

            assertThatThrownBy(() -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT,
                    record -> {
                        seen.add(record);
                        throw failure;
                    },
                    (rejection, text) -> { }))
                    .isSameAs(failure);
            assertThat(seen).containsExactly(record(1, 1, "a"));
        }

        @Test
        void recordHandlerRuntimeExceptionIsPassedUnchanged() throws IOException {
            IllegalStateException failure = new IllegalStateException("stop");
            Path path = file("a\nb\n");

            assertThatThrownBy(() -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT,
                    record -> {
                        throw failure;
                    },
                    (rejection, text) -> { }))
                    .isSameAs(failure);
        }

        @Test
        void rejectionHandlerExceptionStopsReadingAndIsPassedUnchanged() throws IOException {
            IOException failure = new IOException("stop");
            List<CsvRecordReader.Record> seen = new ArrayList<>();
            Path path = file("a\"b\nnext\n");

            assertThatThrownBy(() -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, seen::add,
                    (rejection, text) -> {
                        throw failure;
                    }))
                    .isSameAs(failure);
            assertThat(seen).isEmpty();
        }

        @Test
        void fileIsClosedAfterNormalRead() throws IOException {
            Path path = file("a\n");

            readIgnoringOutput(path, ',', NO_LIMIT, NO_LIMIT);

            // On Windows a file that is still open can't be deleted.
            Files.delete(path);
            assertThat(path).doesNotExist();
        }

        @Test
        void fileIsClosedAfterHandlerFailure() throws IOException {
            Path path = file("a\nb\n");

            assertThatThrownBy(() -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT,
                    record -> {
                        throw new IOException("stop");
                    },
                    (rejection, text) -> { }))
                    .isInstanceOf(IOException.class);

            Files.delete(path);
            assertThat(path).doesNotExist();
        }

        private static byte[] bytes(String before, int invalidByte, String after) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(before.getBytes(StandardCharsets.UTF_8));
            output.write(invalidByte);
            output.write(after.getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }

        private void assertDiagnostics(IOException failure, Path path, Charset charset, long line) {
            assertThat(failure).hasCauseInstanceOf(CharacterCodingException.class);
            assertThat(failure.getMessage()).contains(path.toString()).contains(charset.name());
            assertThat(failure.getMessage().replace(path.toString(), "")).containsPattern("\\b" + line + "\\b");
        }

        @Test
        void invalidBytesAreFatalAndReportedWithFileCharsetAndLine() throws IOException {
            Path path = file(bytes("a\nb\nc", 0xFF, "d\ne\n"));
            List<CsvRecordReader.Record> seen = new ArrayList<>();

            IOException failure = catchThrowableOfType(IOException.class,
                    () -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, seen::add,
                            (rejection, text) -> { }));

            assertDiagnostics(failure, path, StandardCharsets.UTF_8, 3);
            assertThat(seen).allSatisfy(record -> assertThat(record.fields()).noneMatch(f -> f.contains("\uFFFD")));
            assertThat(seen).extracting(CsvRecordReader.Record::fields).doesNotContain(List.of("e"));
        }

        @Test
        void bufferingDoesNotShiftTheReportedLine() throws IOException {
            int lines = 100_000;
            Path path = file(bytes("line\n".repeat(lines), 0xFF, "\nafter\n"));

            IOException failure = catchThrowableOfType(IOException.class,
                    () -> readIgnoringOutput(path, ',', NO_LIMIT, NO_LIMIT));

            assertDiagnostics(failure, path, StandardCharsets.UTF_8, lines + 1L);
        }

        @Test
        void unmappableWindows1251ByteIsFatal() throws IOException {
            Path path = file(bytes("a\n", 0x98, "\nb\n"));

            IOException failure = catchThrowableOfType(IOException.class,
                    () -> reader.read(path, WINDOWS_1251, ',', NO_LIMIT, NO_LIMIT, record -> { },
                            (rejection, text) -> { }));

            assertDiagnostics(failure, path, WINDOWS_1251, 2);
        }

        @Test
        void invalidBytesInTheSkippedRestOfARejectedRecordAreFatal() throws IOException {
            Path path = file(bytes("a\"b", 0xFF, "\nnext\n"));
            List<CsvRecordReader.Record> seen = new ArrayList<>();

            IOException failure = catchThrowableOfType(IOException.class,
                    () -> reader.read(path, StandardCharsets.UTF_8, ',', NO_LIMIT, NO_LIMIT, seen::add,
                            (rejection, text) -> { }));

            assertThat(failure).hasCauseInstanceOf(CharacterCodingException.class);
            assertThat(seen).isEmpty();
        }
    }
}
