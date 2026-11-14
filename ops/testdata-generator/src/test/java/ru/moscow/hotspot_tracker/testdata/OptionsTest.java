package ru.moscow.hotspot_tracker.testdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OptionsTest {
    private static final List<String> REQUIRED = List.of("--dataset", "wifi", "--rows", "5", "--output", "out.csv");

    @Test
    void defaultsFollowTheRealDataMosRuFile() {
        Options options = Options.parse(REQUIRED);

        assertEquals(Dataset.WIFI, options.dataset());
        assertEquals(5, options.rows());
        assertEquals(Path.of("out.csv"), options.output());
        assertEquals(1, options.seed());
        assertEquals(StandardCharsets.UTF_8, options.charset());
        assertFalse(options.bom());
        assertEquals(';', options.delimiter());
        assertEquals("\n", options.lineEnding());
        assertEquals(0, options.dirtyRate());
        assertEquals(EnumSet.allOf(Dirt.class), options.dirt());
    }

    @Test
    void defaultsFollowTheRealHousesFile() {
        Options options = Options.parse(List.of("--dataset", "houses", "--rows", "5", "--output", "h.csv"));

        assertEquals(',', options.delimiter());
        assertEquals("\r\n", options.lineEnding());
        assertEquals(Options.REAL_MOSCOW_SHARE, options.moscowShare());
        assertTrue(options.moscowShare() > 0.04 && options.moscowShare() < 0.05);
    }

    @Test
    void everyOptionCanBeSet() {
        Options options = Options.parse(List.of(
                "--dataset",
                "houses",
                "--rows",
                "1000000",
                "--output",
                "h.csv",
                "--seed",
                "-7",
                "--charset",
                "windows-1251",
                "--delimiter",
                ";",
                "--line-ending",
                "lf",
                "--dirty-rate",
                "0.05",
                "--dirty",
                "xss, duplicate-id",
                "--moscow-share",
                "1"));

        assertEquals(1_000_000, options.rows());
        assertEquals(-7, options.seed());
        assertEquals(Options.WINDOWS_1251, options.charset());
        assertEquals(';', options.delimiter());
        assertEquals("\n", options.lineEnding());
        assertEquals(0.05, options.dirtyRate());
        assertEquals(Set.of(Dirt.XSS, Dirt.DUPLICATE_ID), options.dirt());
        assertEquals(1, options.moscowShare());
    }

    @Test
    void tabIsADelimiter() {
        assertEquals('\t', parse("--delimiter", "tab").delimiter());
    }

    @Test
    void bomWorksWithUtf8() {
        assertTrue(parse("--bom").bom());
    }

    static Stream<Arguments> wrongArguments() {
        return Stream.of(
                Arguments.of(List.of("--rows", "5", "--output", "o.csv"), "--dataset is required"),
                Arguments.of(List.of("--dataset", "wifi", "--output", "o.csv"), "--rows is required"),
                Arguments.of(List.of("--dataset", "wifi", "--rows", "5"), "--output is required"),
                Arguments.of(List.of("--dataset", "trams", "--rows", "5", "--output", "o.csv"), "Unknown dataset"),
                Arguments.of(with("--verbose"), "Unknown option: --verbose"),
                Arguments.of(with("--seed"), "--seed needs a value"),
                Arguments.of(with("--rows", "6"), "--rows is given twice"),
                Arguments.of(List.of("--dataset", "wifi", "--rows", "0", "--output", "o.csv"), "--rows must be from 1"),
                Arguments.of(List.of("--dataset", "wifi", "--rows", "1e6", "--output", "o.csv"), "whole number"),
                Arguments.of(with("--charset", "koi8-r"), "--charset must be utf-8 or windows-1251"),
                Arguments.of(with("--charset", "windows-1251", "--bom"), "--bom is only possible with UTF-8"),
                Arguments.of(with("--delimiter", "\""), "--delimiter must be one character"),
                Arguments.of(with("--delimiter", ";;"), "--delimiter must be one character"),
                Arguments.of(with("--line-ending", "cr"), "--line-ending must be lf or crlf"),
                Arguments.of(with("--dirty-rate", "1.5"), "--dirty-rate must be from 0 to 1"),
                Arguments.of(with("--dirty-rate", "NaN"), "--dirty-rate must be from 0 to 1"),
                Arguments.of(with("--dirty", "xss,typo"), "Unknown dirty kind: typo"),
                Arguments.of(with("--moscow-share", "0.5"), "--moscow-share is only for the houses dataset"));
    }

    @ParameterizedTest
    @MethodSource("wrongArguments")
    void wrongArgumentsAreExplained(List<String> args, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Options.parse(args));

        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    private static List<String> with(String... extra) {
        return Stream.concat(REQUIRED.stream(), Stream.of(extra)).toList();
    }

    private static Options parse(String... extra) {
        return Options.parse(with(extra));
    }
}
