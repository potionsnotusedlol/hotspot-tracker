package ru.moscow.hotspot_tracker.main.importing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.*;

/** Поведенческие проверки публичных контрактов без Spring и внешних сервисов. */
class CsvFormatResolverTest {
    @TempDir
    Path directory;
    private final CsvFormatResolver resolver = new CsvFormatResolver();
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset CP1251 = Charset.forName("windows-1251");

    private Path file(String text) throws IOException {
        return Files.writeString(directory.resolve("input.csv"), text, UTF8);
    }

    private Options automatic(int bytes, int records) {
        return new Options(Optional.empty(), Optional.empty(), bytes, records);
    }

    @Test
    void validatesOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new Options(Optional.empty(), Optional.empty(), 3, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Options(Optional.of(StandardCharsets.UTF_16), Optional.empty(), 4, 2));
    }

    @Test
    void explicitFormatDoesNotOpenFile() throws Exception {
        Format format = resolver.resolve(directory.resolve("missing"),
                new Options(Optional.of(CP1251), Optional.of(':'), 4, 2));
        assertEquals(CP1251, format.charset());
        assertEquals(':', format.delimiter());
        assertEquals(SelectionSource.EXPLICIT, format.charsetSource());
        assertEquals(SelectionSource.EXPLICIT, format.delimiterSource());
    }

    @Test
    void detectsAllCandidatesAndEncodings() throws Exception {
        for (char delimiter : new char[]{';', ',', '\t', '|'}) {
            for (Charset charset : List.of(UTF8, CP1251)) {
                Path path = Files.write(directory.resolve("input.csv"),
                        ("code" + delimiter + "title\n1" + delimiter + "Москва\n").getBytes(charset));
                Format format = resolver.resolve(path, automatic(200, 10));
                assertEquals(charset, format.charset());
                assertEquals(delimiter, format.delimiter());
            }
        }
    }

    @Test
    void supportsIndependentExplicitSettings() throws Exception {
        Path path = file("a;b\nc;d");
        assertEquals(SelectionSource.SAMPLE, resolver.resolve(path,
                new Options(Optional.of(UTF8), Optional.empty(), 100, 3)).delimiterSource());
        Format format = resolver.resolve(path, new Options(Optional.empty(), Optional.of(':'), 100, 3));
        assertEquals(':', format.delimiter());
        assertEquals(SelectionSource.SAMPLE, format.charsetSource());
    }

    @Test
    void handlesBomAndConflicts() throws Exception {
        Path path = file("\uFEFFa;b\nc;d");
        assertEquals(SelectionSource.BOM, resolver.resolve(path, automatic(100, 3)).charsetSource());
        assertEquals(SelectionSource.EXPLICIT, resolver.resolve(path,
                new Options(Optional.of(UTF8), Optional.empty(), 100, 3)).charsetSource());
        assertEquals(FailureReason.BOM_CONFLICT, assertThrows(FormatResolutionException.class,
                () -> resolver.resolve(path, new Options(Optional.of(CP1251), Optional.empty(), 100, 3))).reason());
        for (byte[] bytes : List.of(new byte[]{(byte) 0xff, (byte) 0xfe, 0, 0},
                new byte[]{0, 0, (byte) 0xfe, (byte) 0xff}, new byte[]{(byte) 0xfe, (byte) 0xff})) {
            Files.write(path, bytes);
            assertEquals(FailureReason.UNSUPPORTED_BOM, assertThrows(FormatResolutionException.class,
                    () -> resolver.resolve(path, automatic(100, 3))).reason());
        }
    }

    @Test
    void reportsAmbiguityAndInsufficientSamples() throws Exception {
        Path ambiguous = file("a,b;c\nd,e;f");
        assertEquals(FailureReason.AMBIGUOUS_DELIMITER, assertThrows(FormatResolutionException.class,
                () -> resolver.resolve(ambiguous, automatic(100, 3))).reason());
        for (String input : List.of("", "a;b", "a\nb", "a;b\nc;d;e", "a;\"x\"z\nc;d")) {
            Path path = file(input);
            assertEquals(FailureReason.INSUFFICIENT_SAMPLE, assertThrows(FormatResolutionException.class,
                    () -> resolver.resolve(path, automatic(100, 3))).reason());
        }
    }

    @Test
    void handlesMultilineQuotesAndTrailingEmptyColumns() throws Exception {
        Format format = resolver.resolve(file("\ncode;title;\r\nКод;Название;\r1;\"а,б\nв|г\";\n"), automatic(500, 10));
        assertEquals(';', format.delimiter());
    }

    @Test
    void ignoresIncompleteRecordAtSampleBoundaryButAcceptsActualEof() throws Exception {
        Path path = file("a;b\nc;d\nx;y\n");
        assertEquals(FailureReason.INSUFFICIENT_SAMPLE, assertThrows(FormatResolutionException.class,
                () -> resolver.resolve(path, automatic(7, 10))).reason());
        assertEquals(';', resolver.resolve(path, automatic(8, 10)).delimiter());
        assertEquals(';', resolver.resolve(file("a;b\nc;d"), automatic(7, 10)).delimiter());
    }

    @Test
    void incompleteUtf8IsAllowedOnlyAtArtificialBoundary() throws Exception {
        Path path = file("a;b\nc;d\nЯ");
        Options options = new Options(Optional.of(UTF8), Optional.empty(), 9, 10);
        assertEquals(';', resolver.resolve(path, options).delimiter());
        byte[] truncated = java.util.Arrays.copyOf(Files.readAllBytes(path), 9);
        Files.write(path, truncated);
        FormatResolutionException failure = assertThrows(FormatResolutionException.class,
                () -> resolver.resolve(path, options));
        assertEquals(FailureReason.INVALID_ENCODING, failure.reason());
        assertEquals(3, failure.sourceLine());
    }

    @Test
    void checksEncodingBeyondRecordLimit() throws Exception {
        byte[] bytes = "a;b\nc;d\nx".getBytes(UTF8);
        bytes[bytes.length - 1] = (byte) 0x98; // Invalid in both UTF-8 and Windows-1251.
        Path path = Files.write(directory.resolve("bad.csv"), bytes);
        FormatResolutionException failure = assertThrows(FormatResolutionException.class,
                () -> resolver.resolve(path, automatic(100, 2)));
        assertEquals(FailureReason.INVALID_ENCODING, failure.reason());
        assertEquals(3, failure.sourceLine());
        assertNotNull(failure.getCause());
    }

}
