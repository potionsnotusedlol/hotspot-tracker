package ru.moscow.hotspot_tracker.testdata;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Helpers to generate files in memory. */
final class Fixtures {
    // Bounding box of Moscow with the MCD stations around it
    static final double MIN_LAT = 55.0;
    static final double MAX_LAT = 56.2;
    static final double MIN_LON = 36.5;
    static final double MAX_LON = 38.5;

    private Fixtures() {}

    static Options options(Dataset dataset, int rows, String... extra) {
        List<String> args = new ArrayList<>(List.of("--dataset", dataset.option(), "--rows", String.valueOf(rows)));
        args.addAll(List.of(extra));
        if (!args.contains("--output")) {
            // Not written: the in-memory generation ignores it
            args.addAll(List.of("--output", "unused.csv"));
        }
        return Options.parse(args);
    }

    static String generate(Options options) {
        StringWriter out = new StringWriter();
        try {
            Generator.generate(options, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    static String generate(Dataset dataset, int rows, String... extra) {
        return generate(options(dataset, rows, extra));
    }

    /** Data rows of a generated file, without the header rows. */
    static List<List<String>> data(Dataset dataset, String text) {
        List<List<String>> records = TestCsv.parse(text, dataset.defaultDelimiter());
        return records.subList(dataset.header().size(), records.size());
    }

    static boolean inMoscowRegion(String lat, String lon) {
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lon);
            return latitude >= MIN_LAT && latitude <= MAX_LAT && longitude >= MIN_LON && longitude <= MAX_LON;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
