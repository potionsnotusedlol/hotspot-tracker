package ru.moscow.hotspot_tracker.testdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void writesTheFileAndASummary(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("metro.csv");

        int code = run("--dataset", "metro", "--rows", "10", "--output", file.toString(), "--dirty-rate", "1");

        assertEquals(0, code);
        assertTrue(text(out).startsWith("Wrote 10 rows (10 broken) of metro to "), text(out));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void withoutArgumentsPrintsUsageAndFails() {
        assertEquals(2, run());
        assertTrue(text(out).startsWith("Usage:"));
    }

    @Test
    void helpPrintsUsage() {
        assertEquals(0, run("--help"));
        assertTrue(text(out).contains("--dirty KINDS"));
    }

    @Test
    void wrongArgumentsAreReported() {
        assertEquals(2, run("--dataset", "wifi"));
        assertTrue(text(err).startsWith("Error: --rows is required"), text(err));
    }

    @Test
    void unwritableOutputIsReported(@TempDir Path dir) {
        // A directory cannot be opened as a file
        assertEquals(1, run("--dataset", "mcd", "--rows", "1", "--output", dir.toString()));
        assertTrue(text(err).startsWith("Error: cannot write"), text(err));
    }

    private int run(String... args) {
        return Main.run(
                List.of(args),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }
}
