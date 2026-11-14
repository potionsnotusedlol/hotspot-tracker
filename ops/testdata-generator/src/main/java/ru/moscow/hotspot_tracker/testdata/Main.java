package ru.moscow.hotspot_tracker.testdata;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/** Command line entry point. */
public final class Main {
    static final String USAGE = """
            Usage: testdata-generator --dataset NAME --rows N --output FILE [options]

            Writes synthetic CSV files in the formats of the data.mos.ru datasets and houses.csv.

              --dataset NAME       wifi, stops, metro, mcd or houses
              --rows N             number of data rows, without the header
              --output FILE        file to write; an existing file is replaced
              --seed N             seed of the random values (default 1): the same seed gives the same file
              --charset NAME       utf-8 (default) or windows-1251
              --bom                start the file with a UTF-8 byte order mark
              --delimiter C        field delimiter: one character or tab (default: as in the real file)
              --line-ending E      lf or crlf (default: as in the real file)
              --dirty-rate R       share of broken rows, from 0 (default) to 1
              --dirty KINDS        comma-separated kinds of broken rows (default: all):
                                   bad-coordinates, outside-moscow, duplicate-id, missing-required,
                                   dash-number, newline-in-quotes, broken-quote, xss, prompt-injection
              --moscow-share R     houses only: share of Moscow houses (default: as in the real file)
              --help               show this text
            """;

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out, System.err));
    }

    /**
     * Runs the generator.
     *
     * @return 0 on success, 1 if the file cannot be written, 2 if the arguments are wrong
     */
    static int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.isEmpty() || args.contains("--help")) {
            out.print(USAGE);
            return args.isEmpty() ? 2 : 0;
        }
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println("Run with --help to see the options.");
            return 2;
        }
        long start = System.nanoTime();
        try {
            Generator.Summary summary = Generator.generate(options);
            out.printf(
                    Locale.ROOT,
                    "Wrote %d rows (%d broken) of %s to %s in %.1f s%n",
                    summary.rows(),
                    summary.dirtyRows(),
                    options.dataset().option(),
                    options.output(),
                    (System.nanoTime() - start) / 1e9);
            return 0;
        } catch (IOException e) {
            err.println("Error: cannot write " + options.output() + ": " + e.getMessage());
            return 1;
        }
    }
}
