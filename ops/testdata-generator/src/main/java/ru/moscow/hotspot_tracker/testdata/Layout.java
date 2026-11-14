package ru.moscow.hotspot_tracker.testdata;

import java.util.List;

/**
 * How a file is written.
 *
 * @param header header rows, written before the data
 * @param delimiter field delimiter
 * @param quoteAll whether every field is quoted (data.mos.ru) or only those that need it (houses.csv)
 * @param trailingDelimiter whether each row ends with a delimiter (data.mos.ru)
 * @param lineEnding line ending of each row
 */
record Layout(
        List<List<String>> header, char delimiter, boolean quoteAll, boolean trailingDelimiter, String lineEnding) {}
