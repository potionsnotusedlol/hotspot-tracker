package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/** A strict CSV parser for the tests: RFC 4180 quotes, LF or CRLF, no recovery. */
final class TestCsv {
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
        // 0: start of a field, 1: unquoted field, 2: inside quotes, 3: after a closing quote
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (state == 2) {
                if (c == '"') {
                    state = 3;
                } else {
                    field.append(c);
                }
            } else if (state == 3 && c == '"') {
                field.append('"');
                state = 2;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                state = 0;
            } else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                continue;
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                records.add(fields);
                fields = new ArrayList<>();
                state = 0;
            } else if (c == '"') {
                if (state != 0) {
                    throw new IllegalArgumentException("Quote inside an unquoted field at " + i);
                }
                state = 2;
            } else {
                if (state == 3) {
                    throw new IllegalArgumentException("Character after a closing quote at " + i);
                }
                field.append(c);
                state = 1;
            }
        }
        if (state == 2) {
            throw new IllegalArgumentException("Unclosed quote");
        }
        if (!fields.isEmpty() || field.length() > 0) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }
}
