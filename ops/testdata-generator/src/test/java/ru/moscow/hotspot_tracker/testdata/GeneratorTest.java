package ru.moscow.hotspot_tracker.testdata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GeneratorTest {
    private static final Pattern GEO = Pattern.compile("\\{coordinates=\\[(-?[0-9.]+), (-?[0-9.]+)], type=Point}");
    private static final Pattern QUOTED_LINE = Pattern.compile("(\"([^\"]|\"\")*\";)+");
    private static final int ROWS = 300;

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void sameSeedGivesSameFile(Dataset dataset) {
        assertEquals(
                Fixtures.generate(dataset, ROWS, "--seed", "42"), Fixtures.generate(dataset, ROWS, "--seed", "42"));
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void differentSeedsGiveDifferentFiles(Dataset dataset) {
        assertNotEquals(
                Fixtures.generate(dataset, ROWS, "--seed", "1"), Fixtures.generate(dataset, ROWS, "--seed", "2"));
    }

    @ParameterizedTest
    @EnumSource(value = Dataset.class, names = "HOUSES", mode = EnumSource.Mode.EXCLUDE)
    void dataMosRuFilesQuoteEveryFieldAndEndRowsWithDelimiter(Dataset dataset) {
        String text = Fixtures.generate(dataset, ROWS);

        assertFalse(text.contains("\r"), "data.mos.ru files use LF");
        String[] lines = text.split("\n", -1);
        assertEquals(ROWS + 2 + 1, lines.length, "two header rows, the data and the final line break");
        assertEquals("", lines[lines.length - 1]);
        for (int i = 0; i < lines.length - 1; i++) {
            assertTrue(QUOTED_LINE.matcher(lines[i]).matches(), "line " + (i + 1) + ": " + lines[i]);
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void headerRowsAreTheRealOnes(Dataset dataset) {
        List<List<String>> records = TestCsv.parse(Fixtures.generate(dataset, 1), dataset.defaultDelimiter());

        for (int i = 0; i < dataset.header().size(); i++) {
            List<String> header = records.get(i);
            List<String> expected = dataset.header().get(i);
            assertEquals(expected, dataset.dataMosRu() ? header.subList(0, header.size() - 1) : header);
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void everyRowHasTheColumnsOfTheHeader(Dataset dataset) {
        int width = dataset.header().get(0).size() + (dataset.dataMosRu() ? 1 : 0);

        for (List<String> row : Fixtures.data(dataset, Fixtures.generate(dataset, ROWS))) {
            assertEquals(width, row.size(), row.toString());
            if (dataset.dataMosRu()) {
                assertEquals("", row.get(width - 1), "the trailing delimiter gives an empty last column");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void coordinatesAreAroundMoscowAndRepeatedInGeoData(Dataset dataset) {
        Dataset.Columns columns = dataset.columns();
        int lat = dataset.column(columns.latitude());
        int lon = dataset.column(columns.longitude());
        List<Integer> geo = columns.geo().stream().map(dataset::column).toList();
        int region = dataset == Dataset.HOUSES ? dataset.column("region") : -1;

        for (List<String> row : Fixtures.data(dataset, Fixtures.generate(dataset, ROWS))) {
            if (region >= 0 && !row.get(region).equals(HouseRows.MOSCOW)) {
                continue;
            }
            assertTrue(Fixtures.inMoscowRegion(row.get(lat), row.get(lon)), row.toString());
            for (int column : geo) {
                Matcher matcher = GEO.matcher(row.get(column));
                assertTrue(matcher.matches(), row.get(column));
                assertEquals(0, new BigDecimal(matcher.group(1)).compareTo(new BigDecimal(row.get(lon))));
                assertEquals(0, new BigDecimal(matcher.group(2)).compareTo(new BigDecimal(row.get(lat))));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void identifiersAreUnique(Dataset dataset) {
        int id = dataset.column(dataset.columns().id());
        Set<String> seen = new HashSet<>();

        for (List<String> row : Fixtures.data(dataset, Fixtures.generate(dataset, ROWS))) {
            assertTrue(seen.add(row.get(id)), "duplicate " + row.get(id));
        }
    }

    @Test
    void housesUseCommasCrlfAndTheValueFormatsOfTheRealFile() {
        String text = Fixtures.generate(Dataset.HOUSES, ROWS);
        Dataset houses = Dataset.HOUSES;

        assertEquals(ROWS + 1, text.split("\r\n", -1).length - 1, "every row ends with CRLF");
        assertFalse(text.replace("\r\n", "").contains("\n"));
        for (List<String> row : Fixtures.data(houses, text)) {
            assertTrue(row.get(houses.column("square")).matches("\\d{1,3}( \\d{3})*\\.\\d{2}|\u2014"), row.toString());
            assertTrue(row.get(houses.column("population")).matches("\\d{1,3}( \\d{3})*|\u2014|"), row.toString());
            assertTrue(row.get(houses.column("maintenance_year")).matches("\\d{4}|1|\u2014|\u0434\u043e 1945|"));
            assertTrue(row.get(houses.column("communal_service_id")).matches("\\d+\\.0"));
            Double.parseDouble(row.get(houses.column("latitude")));
            Double.parseDouble(row.get(houses.column("longitude")));
        }
    }

    @Test
    void moscowShareOfHousesCanBeChanged() {
        List<List<String>> rows =
                Fixtures.data(Dataset.HOUSES, Fixtures.generate(Dataset.HOUSES, 2_000, "--moscow-share", "0.5"));
        int region = Dataset.HOUSES.column("region");

        long moscow = rows.stream()
                .filter(row -> row.get(region).equals(HouseRows.MOSCOW))
                .count();

        assertTrue(moscow > 850 && moscow < 1_150, "Moscow rows: " + moscow);
    }

    @Test
    void delimiterAndLineEndingCanBeChanged() {
        String text = Fixtures.generate(Dataset.WIFI, 10, "--delimiter", "tab", "--line-ending", "crlf");

        List<List<String>> records = TestCsv.parse(text, '\t');
        assertEquals(12, records.size());
        assertEquals(Dataset.WIFI.header().get(0).size() + 1, records.get(2).size());
        assertTrue(text.endsWith("\t\r\n"));
    }

    /**
     * The file decodes strictly as Windows-1251, and not as UTF-8. It must contain "operating" in Russian:
     * every Wi-Fi row has it.
     */
    @Test
    void windows1251FileIsWrittenInWindows1251(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("wifi.csv");
        Generator.generate(
                Fixtures.options(Dataset.WIFI, 20, "--charset", "windows-1251", "--output", file.toString()));

        byte[] bytes = Files.readAllBytes(file);
        String text = Options.WINDOWS_1251
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        assertTrue(text.contains("\u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442"));
        assertThrows(
                CharacterCodingException.class,
                () -> StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)));
    }

    @Test
    void bomStartsTheFileOnlyWhenRequested(@TempDir Path dir) throws IOException {
        Path withBom = dir.resolve("with.csv");
        Path without = dir.resolve("without.csv");
        Generator.generate(Fixtures.options(Dataset.MCD, 5, "--bom", "--output", withBom.toString()));
        Generator.generate(Fixtures.options(Dataset.MCD, 5, "--output", without.toString()));

        byte[] bom = Arrays.copyOf(Files.readAllBytes(withBom), 3);
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, bom);
        assertEquals('"', Files.readAllBytes(without)[0]);
    }

    @Test
    void outputDirectoriesAreCreated(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a/b/stops.csv");

        Generator.Summary summary = Generator.generate(Fixtures.options(Dataset.STOPS, 7, "--output", file.toString()));

        assertEquals(new Generator.Summary(7, 0), summary);
        assertEquals(9, Files.readAllLines(file, StandardCharsets.UTF_8).size());
    }
}
