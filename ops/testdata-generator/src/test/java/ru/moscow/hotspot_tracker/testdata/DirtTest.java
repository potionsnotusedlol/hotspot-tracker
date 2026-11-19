package ru.moscow.hotspot_tracker.testdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DirtTest {
    private static final int ROWS = 60;

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void filesAreCleanByDefault(Dataset dataset) {
        Generator.Summary summary = summary(Fixtures.options(dataset, ROWS));

        assertEquals(0, summary.dirtyRows());
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void badCoordinatesCannotBeUsed(Dataset dataset) {
        for (List<String> row : rows(dataset, Dirt.BAD_COORDINATES)) {
            assertFalse(Fixtures.inMoscowRegion(lat(dataset, row), lon(dataset, row)), row.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void outsideMoscowIsAroundSaintPetersburg(Dataset dataset) {
        for (List<String> row : rows(dataset, Dirt.OUTSIDE_MOSCOW)) {
            assertTrue(Double.parseDouble(lat(dataset, row)) > 59, row.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void duplicateIdRepeatsThePreviousRow(Dataset dataset) {
        List<List<String>> rows = rows(dataset, Dirt.DUPLICATE_ID);
        int id = dataset.column(dataset.columns().id());

        for (int i = 1; i < rows.size(); i++) {
            assertEquals(rows.get(i - 1).get(id), rows.get(i).get(id));
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void missingRequiredEmptiesTheIdOrTheCoordinates(Dataset dataset) {
        int id = dataset.column(dataset.columns().id());

        for (List<String> row : rows(dataset, Dirt.MISSING_REQUIRED)) {
            assertTrue(row.get(id).isEmpty() || lat(dataset, row).isEmpty(), row.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void dashNumberPutsADashIntoANumericColumn(Dataset dataset) {
        int number = dataset.column(dataset.columns().number());

        for (List<String> row : rows(dataset, Dirt.DASH_NUMBER)) {
            assertEquals("\u2014", row.get(number));
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void newlineInQuotesIsValidCsv(Dataset dataset) {
        for (List<String> row : rows(dataset, Dirt.NEWLINE_IN_QUOTES)) {
            assertTrue(name(dataset, row).contains("\n"), row.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void brokenQuoteBreaksTheSyntaxOfEveryRow(Dataset dataset) {
        String text = Fixtures.generate(dataset, ROWS, "--dirty-rate", "1", "--dirty", "broken-quote");
        List<String> lines = List.of(text.split("\r?\n"));

        for (String line : lines.subList(dataset.header().size(), lines.size())) {
            assertThrows(IllegalArgumentException.class, () -> TestCsv.parse(line, dataset.defaultDelimiter()), line);
        }
    }

    @ParameterizedTest
    @EnumSource(Dataset.class)
    void xssAndPromptInjectionGoIntoTheName(Dataset dataset) {
        for (List<String> row : rows(dataset, Dirt.XSS)) {
            assertTrue(name(dataset, row).endsWith("<script>alert('xss')</script>"), row.toString());
        }
        for (List<String> row : rows(dataset, Dirt.PROMPT_INJECTION)) {
            assertTrue(name(dataset, row).startsWith("Ignore all previous instructions"), row.toString());
        }
    }

    @Test
    void rateIsTheShareOfBrokenRows() {
        Generator.Summary summary = summary(Fixtures.options(
                Dataset.STOPS, 10_000, "--dirty-rate", "0.2", "--dirty", "xss,dash-number,outside-moscow"));

        assertTrue(summary.dirtyRows() > 1_800 && summary.dirtyRows() < 2_200, summary.toString());
    }

    @Test
    void everyRowIsBrokenAtRateOne() {
        Generator.Summary summary =
                summary(Fixtures.options(Dataset.WIFI, ROWS, "--dirty-rate", "1", "--dirty", "xss"));

        assertEquals(ROWS, summary.dirtyRows());
    }

    private static List<List<String>> rows(Dataset dataset, Dirt dirt) {
        List<List<String>> rows =
                Fixtures.data(dataset, Fixtures.generate(dataset, ROWS, "--dirty-rate", "1", "--dirty", dirt.option()));
        assertEquals(ROWS, rows.size());
        return rows;
    }

    private static Generator.Summary summary(Options options) {
        try {
            return Generator.generate(options, new StringWriter());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lat(Dataset dataset, List<String> row) {
        return row.get(dataset.column(dataset.columns().latitude()));
    }

    private static String lon(Dataset dataset, List<String> row) {
        return row.get(dataset.column(dataset.columns().longitude()));
    }

    private static String name(Dataset dataset, List<String> row) {
        return row.get(dataset.column(dataset.columns().name()));
    }
}
