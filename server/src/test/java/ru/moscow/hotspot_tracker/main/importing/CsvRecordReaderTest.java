package ru.moscow.hotspot_tracker.main.importing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.moscow.hotspot_tracker.main.importing.CsvRecordReader.RejectionReason.*;

/** Поведенческие проверки публичных контрактов без Spring и внешних сервисов. */
class CsvRecordReaderTest {
    @TempDir
    Path directory;
    private final CsvRecordReader reader = new CsvRecordReader();
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset CP1251 = Charset.forName("windows-1251");

    private Path file(String text) throws IOException {
        return Files.writeString(directory.resolve("input.csv"), text, UTF8);
    }

    private List<CsvRecordReader.Record> read(Path path, int fieldLimit, int recordLimit) throws IOException {
        List<CsvRecordReader.Record> records = new ArrayList<>();
        long count = reader.read(path, UTF8, ';', fieldLimit, recordLimit, records::add,
                (error, text) -> fail("Unexpected rejection: " + error));
        assertEquals(records.size(), count);
        return records;
    }

    private String text(Reader reader) throws IOException {
        StringWriter writer = new StringWriter();
        reader.transferTo(writer);
        return writer.toString();
    }

    @Test
    void preservesFieldsQuotesHeadersAndPhysicalLines() throws Exception {
        var records = read(file("\uFEFF\r\ncode;title;\rКод;Название;\n1;\"Москва;\r\nцентр \"\"А\"\"\";\r\n\"\"; ;"), 100, 200);
        assertEquals(4, records.size());
        assertEquals(List.of("1", "Москва;\nцентр \"А\"", ""), records.get(2).fields());
        assertEquals(List.of("", " ", ""), records.get(3).fields());
        assertEquals(List.of(2L, 3L, 4L, 6L), records.stream().map(CsvRecordReader.Record::sourceLine).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), records.stream().map(CsvRecordReader.Record::number).toList());
    }

    @Test
    void emptyFilesProduceNoRecords() throws Exception {
        for (String value : List.of("", "\uFEFF", "\r\n\r\n\n", "\uFEFF\n")) {
            assertTrue(read(file(value), 10, 20).isEmpty());
        }
    }

    @Test
    void recordCopiesFields() {
        var fields = new ArrayList<>(List.of("a"));
        var record = new CsvRecordReader.Record(1, 1, fields);
        fields.set(0, "b");
        assertEquals(List.of("a"), record.fields());
        assertThrows(UnsupportedOperationException.class, () -> record.fields().add("c"));
        assertThrows(NullPointerException.class, () -> new CsvRecordReader.Record(1, 1, null));
    }

    @Test
    void validatesBeforeOpeningFile() {
        Path missing = directory.resolve("absent.csv");
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(missing, UTF8, '"', 1, 1, r -> {}, (r, t) -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(missing, UTF8, ';', 2, 1, r -> {}, (r, t) -> {}));
        assertThrows(NullPointerException.class,
                () -> reader.read(missing, UTF8, ';', 1, 1, null, (r, t) -> {}));
    }

    @Test
    void rejectsOnceAndPreservesRawText() throws Exception {
        List<CsvRecordReader.Record> accepted = new ArrayList<>();
        List<CsvRecordReader.RejectedRecord> rejected = new ArrayList<>();
        List<String> raw = new ArrayList<>();
        long count = reader.read(file("a;\"bad\r\nline\"x;z\r\nok;yes\n"), UTF8, ';', 100, 200,
                accepted::add, (error, value) -> { rejected.add(error); raw.add(text(value)); });
        assertEquals(1, count);
        assertEquals(List.of("a;\"bad\r\nline\"x;z\r\n"), raw);
        assertEquals(INVALID_QUOTE, rejected.getFirst().reason());
        assertEquals(1, rejected.getFirst().sourceLine());
        assertEquals(2, rejected.getFirst().errorLine());
        assertEquals(2, accepted.getFirst().number());
        assertEquals(3, accepted.getFirst().sourceLine());
    }

    @Test
    void rejectionMayBeIgnoredPartiallyReadOrClosed() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            int selectedMode = mode;
            Reader[] saved = new Reader[1];
            List<CsvRecordReader.Record> accepted = new ArrayList<>();
            reader.read(file("bad\";tail\r\nok;x"), UTF8, ';', 30, 60, accepted::add, (error, value) -> {
                saved[0] = value;
                if (selectedMode == 1) assertEquals('b', value.read());
                if (selectedMode == 2) value.close();
            });
            assertEquals(List.of("ok", "x"), accepted.getFirst().fields());
            assertThrows(IOException.class, () -> saved[0].read());
        }
    }

    @Test
    void priorRejectionStreamCannotReadLaterRejection() throws Exception {
        Reader[] previous = new Reader[1];
        int[] errors = {0};
        reader.read(file("x\"\ny\"\n"), UTF8, ';', 10, 20, r -> fail(), (error, value) -> {
            if (previous[0] != null) assertThrows(IOException.class, () -> previous[0].read());
            previous[0] = value;
            errors[0]++;
        });
        assertEquals(2, errors[0]);
    }

    @Test
    void limitsAllowEqualityAndRespectPriority() throws Exception {
        assertEquals(List.of("ab", "c"), read(file("ab;c"), 2, 4).getFirst().fields());
        List<CsvRecordReader.RejectedRecord> errors = new ArrayList<>();
        reader.read(file("abc\n\"ab\"\"\n"), UTF8, ';', 2, 4, r -> fail(), (r, t) -> errors.add(r));
        assertEquals(List.of(FIELD_TOO_LONG, RECORD_TOO_LONG), errors.stream().map(CsvRecordReader.RejectedRecord::reason).toList());
        errors.clear();
        reader.read(file("ab\""), UTF8, ';', 2, 2, r -> fail(), (r, t) -> errors.add(r));
        assertEquals(RECORD_TOO_LONG, errors.getFirst().reason());
    }

    @Test
    void rejectionAtNewlineDoesNotConsumeFollowingRecord() throws Exception {
        List<CsvRecordReader.Record> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        reader.read(file("\"ab\r\nok;x"), UTF8, ';', 2, 20, accepted::add, (r, t) -> rejected.add(text(t)));
        assertEquals(List.of("\"ab\r\n"), rejected);
        assertEquals(List.of("ok", "x"), accepted.getFirst().fields());
    }

    @Test
    void unclosedQuoteAtEofReportsOriginalText() throws Exception {
        List<String> raw = new ArrayList<>();
        reader.read(file("\"a\r\nb"), UTF8, ';', 20, 30, r -> fail(), (r, t) -> {
            assertEquals(UNCLOSED_QUOTE, r.reason());
            assertEquals(2, r.errorLine());
            raw.add(text(t));
        });
        assertEquals(List.of("\"a\r\nb"), raw);
    }

    @Test
    void handlerExceptionsAreNotWrapped() throws Exception {
        IOException failure = new IOException("stop");
        Path good = file("a;b");
        assertSame(failure, assertThrows(IOException.class,
                () -> reader.read(good, UTF8, ';', 10, 20, r -> { throw failure; }, (r, t) -> {})));
        Path bad = file("a\"");
        assertSame(failure, assertThrows(IOException.class,
                () -> reader.read(bad, UTF8, ';', 10, 20, r -> {}, (r, t) -> { throw failure; })));
    }

    @Test
    void decodingErrorsHaveExactPhysicalLineAndCause() throws Exception {
        for (String prefix : List.of("a;b\r\nc;d\r", "a;b\nc;d\n", "a;b\rc;d\r")) {
            byte[] bytes = (prefix + "x").getBytes(UTF8);
            bytes[bytes.length - 1] = (byte) 0xff;
            Path path = Files.write(directory.resolve("invalid.csv"), bytes);
            IOException failure = assertThrows(IOException.class, () -> read(path, 100, 200));
            assertTrue(failure.getMessage().contains("line 3"), failure.getMessage());
            assertNotNull(failure.getCause());
        }
    }

    @Test
    void decodingErrorsDuringRecoveryRemainFatal() throws Exception {
        byte[] bytes = "a\"xxxxxxxx".getBytes(UTF8);
        bytes[bytes.length - 1] = (byte) 0xff;
        Path path = Files.write(directory.resolve("bad.csv"), bytes);
        assertThrows(IOException.class, () -> reader.read(path, UTF8, ';', 10, 20, r -> {}, (r, t) -> {}));
    }

    @Test
    void largeMalformedTailIsStreamedAndRecoveryContinues() throws Exception {
        Path path = file("\"" + "a".repeat(2_000_000) + "\r\nok;x");
        List<CsvRecordReader.Record> accepted = new ArrayList<>();
        long[] rejectedChars = {0};
        reader.read(path, UTF8, ';', 16, 32, accepted::add, (r, t) -> {
            assertEquals(FIELD_TOO_LONG, r.reason());
            char[] buffer = new char[127];
            int count;
            while ((count = t.read(buffer)) != -1) rejectedChars[0] += count;
        });
        assertEquals(2_000_003, rejectedChars[0]);
        assertEquals(List.of("ok", "x"), accepted.getFirst().fields());
    }

    @Test
    void utf8AndCrLfWorkAcrossBufferBoundaries() throws Exception {
        String value = "я".repeat(4095) + "😀";
        var records = read(file("\"" + value + "\r\nконец\";x"), 9000, 10000);
        assertEquals(value + "\nконец", records.getFirst().fields().getFirst());
    }
    @Test
    void readsBothEncodingsWithEachDelimiter() throws Exception {
        for (char delimiter : new char[]{';', ',', '\t', '|'}) {
            for (Charset charset : List.of(UTF8, CP1251)) {
                Path path = Files.write(directory.resolve("input.csv"),
                        ("code" + delimiter + "title\n1" + delimiter + "Москва\n").getBytes(charset));
                List<CsvRecordReader.Record> records = new ArrayList<>();
                long count = reader.read(path, charset, delimiter, 30, 60, records::add, (r, t) -> fail());
                assertEquals(2, count);
                assertEquals(List.of("1", "Москва"), records.get(1).fields());
            }
        }
    }
}
