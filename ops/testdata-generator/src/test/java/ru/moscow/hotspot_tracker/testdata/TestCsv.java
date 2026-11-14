package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/** A strict CSV parser for the tests: RFC 4180 quotes, LF or CRLF, no recovery. */
final class TestCsv {
    /** Where the parser is inside the current field. */
    private enum State {
        /** Nothing of the field is read yet. */
        FIELD_START,
        /** Inside a field without quotes. */
        UNQUOTED,
        /** Inside quotes. */
        QUOTED,
        /** Right after a closing quote, or the first quote of a doubled one. */
        AFTER_QUOTE
    }

    private TestCsv() {}

    /**
     * Parses the whole text.
     *
     * @throws IllegalArgumentException on a quote inside an unquoted field, a character after a closing quote
     *     or an unclosed quote
     */
    static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.FIELD_START;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (state == State.QUOTED) {
                if (c == '"') {
                    state = State.AFTER_QUOTE;
                } else {
                    field.append(c);
                }
            } else if (state == State.AFTER_QUOTE && c == '"') {
                field.append('"');
                state = State.QUOTED;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                state = State.FIELD_START;
            } else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                continue;
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                records.add(fields);
                fields = new ArrayList<>();
                state = State.FIELD_START;
            } else if (c == '"') {
                if (state != State.FIELD_START) {
                    throw new IllegalArgumentException("Quote inside an unquoted field at " + i);
                }
                state = State.QUOTED;
            } else {
                if (state == State.AFTER_QUOTE) {
                    throw new IllegalArgumentException("Character after a closing quote at " + i);
                }
                field.append(c);
                state = State.UNQUOTED;
            }
        }
        if (state == State.QUOTED) {
            throw new IllegalArgumentException("Unclosed quote");
        }
        if (!fields.isEmpty() || field.length() > 0) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }
}
