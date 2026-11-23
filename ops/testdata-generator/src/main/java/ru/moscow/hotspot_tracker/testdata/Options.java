package ru.moscow.hotspot_tracker.testdata;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings of one run, parsed from the command line. Unset values take the defaults of the dataset.
 *
 * @param dataset dataset to generate
 * @param rows number of data rows, without the header
 * @param output file to write; existing files are replaced
 * @param seed seed of the random values: the same seed and settings give the same file
 * @param charset UTF-8 or Windows-1251
 * @param bom whether the file starts with a UTF-8 byte order mark
 * @param delimiter field delimiter
 * @param lineEnding line ending
 * @param dirtyRate share of broken rows, from 0 to 1
 * @param dirt kinds of broken rows to choose from
 * @param moscowShare share of Moscow houses (houses only)
 */
record Options(
        Dataset dataset,
        long rows,
        Path output,
        long seed,
        Charset charset,
        boolean bom,
        char delimiter,
        String lineEnding,
        double dirtyRate,
        Set<Dirt> dirt,
        double moscowShare) {

    static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    /** Share of Moscow in the real houses.csv. */
    static final double REAL_MOSCOW_SHARE = Dictionaries.REGIONS.stream()
            .filter(region -> region.name().equals(HouseRows.MOSCOW))
            .findFirst()
            .orElseThrow()
            .share();

    private static final long MAX_ROWS = 1_000_000_000L;
    private static final Set<String> VALUE_OPTIONS = Set.of(
            "--dataset",
            "--rows",
            "--output",
            "--seed",
            "--charset",
            "--delimiter",
            "--line-ending",
            "--dirty-rate",
            "--dirty",
            "--moscow-share");

    Options {
        dirt = Set.copyOf(dirt);
    }

    /**
     * Parses the arguments.
     *
     * @throws IllegalArgumentException with a message for the user if an argument is missing or wrong
     */
    static Options parse(List<String> args) {
        Map<String, String> values = new HashMap<>();
        boolean bom = false;
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.equals("--bom")) {
                bom = true;
            } else if (VALUE_OPTIONS.contains(arg)) {
                if (!iterator.hasNext()) {
                    throw new IllegalArgumentException(arg + " needs a value");
                }
                if (values.put(arg, iterator.next()) != null) {
                    throw new IllegalArgumentException(arg + " is given twice");
                }
            } else {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        Dataset dataset = Dataset.fromOption(required(values, "--dataset"));
        long rows = number(required(values, "--rows"), "--rows");
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("--rows must be from 1 to " + MAX_ROWS);
        }
        Path output = Path.of(required(values, "--output"));
        long seed = number(values.getOrDefault("--seed", "1"), "--seed");
        Charset charset = charset(values.getOrDefault("--charset", "utf-8"));
        if (bom && !charset.equals(StandardCharsets.UTF_8)) {
            throw new IllegalArgumentException("--bom is only possible with UTF-8");
        }
        char delimiter =
                values.containsKey("--delimiter") ? delimiter(values.get("--delimiter")) : dataset.defaultDelimiter();
        String lineEnding = values.containsKey("--line-ending")
                ? lineEnding(values.get("--line-ending"))
                : dataset.defaultLineEnding();
        double dirtyRate = share(values.getOrDefault("--dirty-rate", "0"), "--dirty-rate");
        Set<Dirt> dirt = values.containsKey("--dirty") ? dirt(values.get("--dirty")) : EnumSet.allOf(Dirt.class);
        if (values.containsKey("--moscow-share") && dataset != Dataset.HOUSES) {
            throw new IllegalArgumentException("--moscow-share is only for the houses dataset");
        }
        double moscowShare = values.containsKey("--moscow-share")
                ? share(values.get("--moscow-share"), "--moscow-share")
                : REAL_MOSCOW_SHARE;
        return new Options(
                dataset, rows, output, seed, charset, bom, delimiter, lineEnding, dirtyRate, dirt, moscowShare);
    }

    Layout layout() {
        return new Layout(dataset.header(), delimiter, dataset.dataMosRu(), dataset.dataMosRu(), lineEnding);
    }

    private static String required(Map<String, String> values, String option) {
        String value = values.get(option);
        if (value == null) {
            throw new IllegalArgumentException(option + " is required");
        }
        return value;
    }

    private static long number(String value, String option) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a whole number: " + value, e);
        }
    }

    private static double share(String value, String option) {
        double share;
        try {
            share = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a number: " + value, e);
        }
        if (!(share >= 0 && share <= 1)) {
            throw new IllegalArgumentException(option + " must be from 0 to 1");
        }
        return share;
    }

    private static Charset charset(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "utf-8", "utf8" -> StandardCharsets.UTF_8;
            case "windows-1251", "cp1251" -> WINDOWS_1251;
            default -> throw new IllegalArgumentException("--charset must be utf-8 or windows-1251");
        };
    }

    private static char delimiter(String value) {
        if (value.equals("tab")) {
            return '\t';
        }
        if (value.length() != 1 || value.equals("\"") || value.equals("\n") || value.equals("\r")) {
            throw new IllegalArgumentException("--delimiter must be one character (not a quote) or tab");
        }
        return value.charAt(0);
    }

    private static String lineEnding(String value) {
        return switch (value) {
            case "lf" -> "\n";
            case "crlf" -> "\r\n";
            default -> throw new IllegalArgumentException("--line-ending must be lf or crlf");
        };
    }

    private static Set<Dirt> dirt(String value) {
        Set<Dirt> result = EnumSet.noneOf(Dirt.class);
        for (String kind : value.split(",", -1)) {
            result.add(Dirt.fromOption(kind.trim()));
        }
        return result;
    }
}
