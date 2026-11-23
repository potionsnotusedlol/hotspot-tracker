package ru.moscow.hotspot_tracker.testdata;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Writes rows in a given layout. Keeps nothing in memory but the current row. */
final class CsvWriter {
    private final Writer out;
    private final Layout layout;

    CsvWriter(Writer out, Layout layout) {
        this.out = out;
        this.layout = layout;
    }

    /**
     * Writes one row.
     *
     * @param fields values of the row
     * @param rawField index of a field written without escaping its quotes, or -1;
     *     this is how a row with broken CSV syntax is produced
     */
    void write(List<String> fields, int rawField) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(layout.delimiter());
            }
            String value = fields.get(i);
            if (i == rawField) {
                writeRaw(value);
            } else {
                writeField(value);
            }
        }
        if (layout.trailingDelimiter()) {
            out.write(layout.delimiter());
        }
        out.write(layout.lineEnding());
    }

    private void writeField(String value) throws IOException {
        if (layout.quoteAll() || needsQuotes(value)) {
            out.write('"');
            out.write(value.replace("\"", "\"\""));
            out.write('"');
        } else {
            out.write(value);
        }
    }

    private void writeRaw(String value) throws IOException {
        if (layout.quoteAll()) {
            out.write('"');
            out.write(value);
            out.write('"');
        } else {
            out.write(value);
        }
    }

    private boolean needsQuotes(String value) {
        return value.indexOf(layout.delimiter()) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }
}
