package ru.moscow.hotspot_tracker.testdata;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;

/** Writes a file row by row; memory does not depend on the number of rows. */
final class Generator {
    /** Result of a run. */
    record Summary(long rows, long dirtyRows) {}

    private Generator() {}

    static Summary generate(Options options) throws IOException {
        Path parent = options.output().toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // A character the charset cannot encode is an error, not a silent "?"
        CharsetEncoder encoder = options.charset()
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Writer out =
                new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(options.output()), encoder), 1 << 16)) {
            return generate(options, out);
        }
    }

    static Summary generate(Options options, Writer out) throws IOException {
        if (options.bom()) {
            out.write('\ufeff');
        }
        CsvWriter csv = new CsvWriter(out, options.layout());
        for (List<String> header : options.dataset().header()) {
            csv.write(header, -1);
        }
        Values values = new Values(new SplittableRandom(options.seed()));
        RowSource rows = options.dataset().rows(options, values);
        DirtApplier dirt = new DirtApplier(options.dataset(), options, values);
        long dirty = 0;
        for (long i = 0; i < options.rows(); i++) {
            DirtApplier.Row row = dirt.apply(rows.next(i));
            csv.write(row.fields(), row.rawField());
            if (row.dirty()) {
                dirty++;
            }
        }
        out.flush();
        return new Summary(options.rows(), dirty);
    }
}
