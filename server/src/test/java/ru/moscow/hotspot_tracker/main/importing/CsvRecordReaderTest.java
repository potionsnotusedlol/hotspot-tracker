package ru.moscow.hotspot_tracker.main.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvRecordReaderTest {

    @TempDir Path temporaryDirectory;

    private final CsvRecordReader reader = new CsvRecordReader();

    @Test
    void readsHeaderQuotedFieldsEmptyFieldsAndPhysicalLineNumbers() throws IOException {
        Path file = temporaryDirectory.resolve("points.csv");
        Files.writeString(file, "\uFEFFid;name;address\r\n1;\"Stop; West\";\"Street\r\nHouse\"\r\n"
                + "2;\"A \"\"quoted\"\" name\";\r\n", StandardCharsets.UTF_8);

        List<CsvRecordReader.Record> records = new ArrayList<>();
        long count = reader.read(file, StandardCharsets.UTF_8, ';', records::add);

        assertEquals(3, count);
        assertEquals(List.of("id", "name", "address"), records.get(0).fields());
        assertEquals(List.of("1", "Stop; West", "Street\nHouse"), records.get(1).fields());
        assertEquals(List.of("2", "A \"quoted\" name", ""), records.get(2).fields());
        assertEquals(List.of(1L, 2L, 4L), records.stream().map(CsvRecordReader.Record::sourceLine).toList());
    }

    @Test
    void readsLastRecordWithoutNewlineAndEmptyFile() throws IOException {
        Path file = temporaryDirectory.resolve("houses.csv");
        Files.writeString(file, "id,population\n1,23", StandardCharsets.UTF_8);
        List<CsvRecordReader.Record> records = new ArrayList<>();

        assertEquals(2, reader.read(file, StandardCharsets.UTF_8, ',', records::add));
        assertEquals(List.of("1", "23"), records.get(1).fields());

        Files.writeString(file, "", StandardCharsets.UTF_8);
        assertEquals(0, reader.read(file, StandardCharsets.UTF_8, ',', record -> {
            throw new AssertionError("Empty file must not produce records");
        }));
    }

    @Test
    void rejectsMalformedCsvWithSourceLine() throws IOException {
        Path file = temporaryDirectory.resolve("broken.csv");
        Files.writeString(file, "id,name\n1,\"unclosed\nvalue", StandardCharsets.UTF_8);

        IOException failure = assertThrows(IOException.class,
                () -> reader.read(file, StandardCharsets.UTF_8, ',', record -> {}));

        assertTrue(failure.getMessage().contains("broken.csv"));
        assertTrue(failure.getMessage().contains("line 2"));
    }

    @Test
    void stopsReadingAsSoonAsHandlerFails() throws IOException {
        Path file = temporaryDirectory.resolve("large.csv");
        Files.writeString(file, "id\n1\n2,\"broken", StandardCharsets.UTF_8);
        IOException stop = new IOException("stop after first record");

        IOException failure = assertThrows(IOException.class,
                () -> reader.read(file, StandardCharsets.UTF_8, ',', record -> {
                    throw stop;
                }));

        assertEquals(stop, failure);
    }

    @Test
    void processesLargeFileWithoutCollectingRecords() throws IOException {
        Path file = temporaryDirectory.resolve("many.csv");
        int rows = 1_000_000;
        try (var output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            output.write("id,value\n");
            for (int i = 0; i < rows; i++) {
                output.write(i + ",value\n");
            }
        }

        long[] lastId = {-1};
        long count = reader.read(file, StandardCharsets.UTF_8, ',', record -> {
            if (record.number() > 1) {
                lastId[0] = Long.parseLong(record.fields().get(0));
            }
        });

        assertEquals(rows + 1L, count);
        assertEquals(rows - 1L, lastId[0]);
    }
}
