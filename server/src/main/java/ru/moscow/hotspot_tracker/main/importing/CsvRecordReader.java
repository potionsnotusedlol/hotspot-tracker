package ru.moscow.hotspot_tracker.main.importing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads one CSV record at a time, including the header record. */
public final class CsvRecordReader {

    public record Record(long number, long sourceLine, List<String> fields) {
        public Record {
            fields = List.copyOf(fields);
        }
    }

    @FunctionalInterface
    public interface RecordHandler {
        void accept(Record record) throws IOException;
    }

    /**
     * Passes records to the handler without retaining previous records. The caller chooses the
     * charset and delimiter; selection and detection of these belong to FR-IMP-02.
     * Newlines inside quoted fields are represented as '\n'.
     *
     * @return number of records passed to the handler, including the header
     */
    public long read(Path path, Charset charset, char delimiter, RecordHandler handler) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(handler, "handler");
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n') {
            throw new IllegalArgumentException("Invalid CSV delimiter");
        }

        try (BufferedReader buffered = Files.newBufferedReader(path, charset);
             NormalizedReader reader = new NormalizedReader(buffered)) {
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            State state = State.START_FIELD;
            long recordNumber = 0;
            long sourceLine = 1;
            boolean recordStarted = false;
            int ch;

            while ((ch = reader.read()) != -1) {
                // A UTF BOM belongs to the file, not to the first column name.
                if (ch == '\uFEFF' && recordNumber == 0 && !recordStarted) {
                    continue;
                }
                recordStarted = true;
                switch (state) {
                    case START_FIELD -> {
                        if (ch == '"') {
                            state = State.QUOTED;
                        } else if (ch == delimiter) {
                            fields.add("");
                        } else if (ch == '\n') {
                            fields.add("");
                            handler.accept(new Record(++recordNumber, sourceLine, fields));
                            fields.clear();
                            sourceLine = reader.lineNumber();
                            recordStarted = false;
                        } else {
                            field.append((char) ch);
                            state = State.UNQUOTED;
                        }
                    }
                    case UNQUOTED -> {
                        if (ch == delimiter || ch == '\n') {
                            fields.add(field.toString());
                            field.setLength(0);
                            state = State.START_FIELD;
                            if (ch == '\n') {
                                handler.accept(new Record(++recordNumber, sourceLine, fields));
                                fields.clear();
                                sourceLine = reader.lineNumber();
                                recordStarted = false;
                            }
                        } else if (ch == '"') {
                            throw malformed(path, reader.lineNumber(), "Quote inside an unquoted field");
                        } else {
                            field.append((char) ch);
                        }
                    }
                    case QUOTED -> {
                        if (ch == '"') {
                            state = State.AFTER_QUOTE;
                        } else {
                            field.append((char) ch);
                        }
                    }
                    case AFTER_QUOTE -> {
                        if (ch == '"') {
                            field.append('"');
                            state = State.QUOTED;
                        } else if (ch == delimiter || ch == '\n') {
                            fields.add(field.toString());
                            field.setLength(0);
                            state = State.START_FIELD;
                            if (ch == '\n') {
                                handler.accept(new Record(++recordNumber, sourceLine, fields));
                                fields.clear();
                                sourceLine = reader.lineNumber();
                                recordStarted = false;
                            }
                        } else {
                            throw malformed(path, reader.lineNumber(), "Unexpected character after closing quote");
                        }
                    }
                }
            }

            if (state == State.QUOTED) {
                throw malformed(path, sourceLine, "Unclosed quoted field");
            }
            if (recordStarted) {
                fields.add(field.toString());
                handler.accept(new Record(++recordNumber, sourceLine, fields));
            }
            return recordNumber;
        }
    }

    private static IOException malformed(Path path, long line, String reason) {
        return new IOException("Malformed CSV in " + path + " at line " + line + ": " + reason);
    }

    private enum State { START_FIELD, UNQUOTED, QUOTED, AFTER_QUOTE }

    /** Treats CRLF and CR as one line break so source line numbers remain consistent. */
    private static final class NormalizedReader extends PushbackReader {
        private long lineNumber = 1;

        private NormalizedReader(BufferedReader reader) {
            super(reader, 1);
        }

        @Override
        public int read() throws IOException {
            int ch = super.read();
            if (ch == '\r') {
                int next = super.read();
                if (next != '\n' && next != -1) {
                    super.unread(next);
                }
                ch = '\n';
            }
            if (ch == '\n') {
                lineNumber++;
            }
            return ch;
        }

        private long lineNumber() {
            return lineNumber;
        }
    }
}
