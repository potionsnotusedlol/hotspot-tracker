package ru.moscow.hotspot_tracker.testdata;

/** Kinds of broken rows the generator can mix into a file. */
enum Dirt {
    /** Coordinates that cannot be used: comma as decimal separator, swapped, text or out of range. */
    BAD_COORDINATES("bad-coordinates"),
    /** Valid coordinates outside Moscow (around Saint Petersburg). */
    OUTSIDE_MOSCOW("outside-moscow"),
    /** The identifier of the previous row. */
    DUPLICATE_ID("duplicate-id"),
    /** An empty identifier or empty coordinates. */
    MISSING_REQUIRED("missing-required"),
    /** A dash instead of a number. */
    DASH_NUMBER("dash-number"),
    /** A line break inside a quoted value: valid CSV that naive line-based readers break on. */
    NEWLINE_IN_QUOTES("newline-in-quotes"),
    /** An unescaped quote that breaks the CSV syntax of the row. */
    BROKEN_QUOTE("broken-quote"),
    /** An XSS payload in a name, for the web interface and the bot. */
    XSS("xss"),
    /** A prompt injection in a name, for the chat bot. */
    PROMPT_INJECTION("prompt-injection");

    private final String option;

    Dirt(String option) {
        this.option = option;
    }

    /** Name used on the command line. */
    String option() {
        return option;
    }

    static Dirt fromOption(String value) {
        for (Dirt dirt : values()) {
            if (dirt.option.equals(value)) {
                return dirt;
            }
        }
        throw new IllegalArgumentException("Unknown dirty kind: " + value);
    }
}
