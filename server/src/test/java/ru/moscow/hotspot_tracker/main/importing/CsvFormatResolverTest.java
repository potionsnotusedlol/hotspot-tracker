package ru.moscow.hotspot_tracker.main.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.FailureReason;
import ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.Format;
import ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.FormatResolutionException;
import ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.Options;
import ru.moscow.hotspot_tracker.main.importing.CsvFormatResolver.SelectionSource;

/** Tests the behaviour described in the {@link CsvFormatResolver} contract (FR-IMP-02). */
class CsvFormatResolverTest {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final int PROBE = 64 * 1024;
    private static final int SAMPLE = 100;

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /** A byte that is invalid in UTF-8 and has no mapping in Windows-1251. */
    private static final int INVALID_IN_BOTH = 0x98;
    private static final String CYRILLIC = "\u0418\u043C\u044F";

    @TempDir
    Path directory;

    private final CsvFormatResolver resolver = new CsvFormatResolver();

    private static Options auto() {
        return new Options(Optional.empty(), Optional.empty(), PROBE, SAMPLE);
    }

    private static Options autoWith(int maxProbeBytes, int maxSampleRecords) {
        return new Options(Optional.empty(), Optional.empty(), maxProbeBytes, maxSampleRecords);
    }

    private static Options explicitCharset(Charset charset) {
        return new Options(Optional.of(charset), Optional.empty(), PROBE, SAMPLE);
    }

    private static Options explicitDelimiter(char delimiter) {
        return new Options(Optional.empty(), Optional.of(delimiter), PROBE, SAMPLE);
    }

    private static Options explicitBoth(Charset charset, char delimiter) {
        return new Options(Optional.of(charset), Optional.of(delimiter), PROBE, SAMPLE);
    }

    private Path file(byte[] content) throws IOException {
        return Files.write(directory.resolve("input.csv"), content);
    }

    private Path file(String content) throws IOException {
        return file(content.getBytes(UTF_8));
    }

    private static byte[] join(Object... parts) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Object part : parts) {
            switch (part) {
                case byte[] bytes -> output.write(bytes);
                case String text -> output.write(text.getBytes(UTF_8));
                case Integer single -> output.write(single);
                default -> throw new IllegalArgumentException("Unexpected part: " + part);
            }
        }
        return output.toByteArray();
    }

    private FormatResolutionException failure(Path path, Options options) {
        return catchThrowableOfType(FormatResolutionException.class, () -> resolver.resolve(path, options));
    }

    @Nested
    class OptionsValidation {

        @Test
        void nullOptionalsAreRejected() {
            assertThatNullPointerException().isThrownBy(() -> new Options(null, Optional.empty(), PROBE, SAMPLE));
            assertThatNullPointerException().isThrownBy(() -> new Options(Optional.empty(), null, PROBE, SAMPLE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-16", "ISO-8859-1", "US-ASCII"})
        void unsupportedCharsetsAreRejected(String charset) {
            assertThatIllegalArgumentException().isThrownBy(() -> explicitCharset(Charset.forName(charset)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-8", "utf8", "windows-1251", "cp1251"})
        void supportedCharsetsAreAcceptedByAnyAlias(String charset) {
            assertThatNoException().isThrownBy(() -> explicitCharset(Charset.forName(charset)));
        }

        @ParameterizedTest
        @ValueSource(chars = {'"', '\r', '\n'})
        void forbiddenDelimitersAreRejected(char delimiter) {
            assertThatIllegalArgumentException().isThrownBy(() -> explicitDelimiter(delimiter));
        }

        @Test
        void explicitDelimiterMayDifferFromAutomaticCandidates() {
            assertThatNoException().isThrownBy(() -> explicitDelimiter(':'));
        }

        @ParameterizedTest
        @CsvSource({"3, 2", "0, 2", "-1, 2", "4, 1", "4, 0", "4, -1"})
        void invalidBoundsAreRejected(int maxProbeBytes, int maxSampleRecords) {
            assertThatIllegalArgumentException().isThrownBy(() -> autoWith(maxProbeBytes, maxSampleRecords));
        }

        @Test
        void minimalBoundsAreAccepted() {
            assertThatNoException().isThrownBy(() -> autoWith(4, 2));
        }
    }

    @Nested
    class Arguments {

        @Test
        void nullPathIsRejected() {
            assertThatNullPointerException().isThrownBy(() -> resolver.resolve(null, auto()));
        }

        @Test
        void nullOptionsAreRejected() throws IOException {
            Path path = file("a;b\nc;d\n");

            assertThatNullPointerException().isThrownBy(() -> resolver.resolve(path, null));
        }
    }

    @Nested
    class BothExplicit {

        @Test
        void returnsGivenValuesWithoutOpeningTheFile() throws IOException {
            Path missing = directory.resolve("missing.csv");

            assertThat(resolver.resolve(missing, explicitBoth(WINDOWS_1251, ':')))
                    .isEqualTo(new Format(WINDOWS_1251, ':', SelectionSource.EXPLICIT, SelectionSource.EXPLICIT));
        }

        @Test
        void doesNotInspectBom() throws IOException {
            Path path = file(join(UTF_8_BOM, "a;b\n"));

            assertThat(resolver.resolve(path, explicitBoth(WINDOWS_1251, ';')))
                    .isEqualTo(new Format(WINDOWS_1251, ';', SelectionSource.EXPLICIT, SelectionSource.EXPLICIT));
        }
    }

    @Nested
    class Bom {

        @Test
        void utf8BomSelectsUtf8() throws IOException {
            assertThat(resolver.resolve(file(join(UTF_8_BOM, "a;b\nc;d\n")), auto()))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.BOM, SelectionSource.SAMPLE));
        }

        @Test
        void explicitUtf8StaysExplicitWithBom() throws IOException {
            assertThat(resolver.resolve(file(join(UTF_8_BOM, "a;b\nc;d\n")), explicitCharset(UTF_8)))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.EXPLICIT, SelectionSource.SAMPLE));
        }

        @Test
        void utf8BomConflictsWithExplicitWindows1251() throws IOException {
            Path path = file(join(UTF_8_BOM, "a;b\nc;d\n"));

            assertThat(failure(path, explicitCharset(WINDOWS_1251)).reason()).isEqualTo(FailureReason.BOM_CONFLICT);
        }

        @ParameterizedTest
        @ValueSource(strings = {"FF FE", "FE FF", "FF FE 00 00", "00 00 FE FF"})
        void utf16AndUtf32BomsAreUnsupported(String bom) throws IOException {
            Path path = file(join(bytes(bom), "a;b\nc;d\n"));

            assertThat(failure(path, auto()).reason()).isEqualTo(FailureReason.UNSUPPORTED_BOM);
        }

        @Test
        void unsupportedBomIsRejectedEvenWithExplicitCharset() throws IOException {
            Path path = file(join(bytes("FF FE"), "a;b\nc;d\n"));

            assertThat(failure(path, explicitCharset(UTF_8)).reason()).isEqualTo(FailureReason.UNSUPPORTED_BOM);
        }

        private static byte[] bytes(String hex) {
            String[] parts = hex.split(" ");
            byte[] result = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = (byte) Integer.parseInt(parts[i], 16);
            }
            return result;
        }
    }

    @Nested
    class CharsetDetection {

        @Test
        void asciiIsDetectedAsUtf8() throws IOException {
            assertThat(resolver.resolve(file("a;b\nc;d\n"), auto()))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @Test
        void validUtf8IsDetected() throws IOException {
            assertThat(resolver.resolve(file(CYRILLIC + ";b\nc;d\n"), auto()))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @Test
        void bytesInvalidInUtf8FallBackToWindows1251() throws IOException {
            Path path = file((CYRILLIC + ";b\nc;d\n").getBytes(WINDOWS_1251));

            assertThat(resolver.resolve(path, auto()))
                    .isEqualTo(new Format(WINDOWS_1251, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @Test
        void bytesInvalidInBothCharsetsFailWithWindows1251Diagnostics() throws IOException {
            Path path = file(join("a;b\nc;d\ne", INVALID_IN_BOTH, ";f\n"));

            FormatResolutionException failure = failure(path, auto());

            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(3);
            assertThat(failure).hasCauseInstanceOf(CharacterCodingException.class);
        }

        @Test
        void explicitUtf8DoesNotFallBack() throws IOException {
            Path path = file(("a;b\nc;d\n" + CYRILLIC + ";e\n").getBytes(WINDOWS_1251));

            FormatResolutionException failure = failure(path, explicitCharset(UTF_8));

            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(3);
            assertThat(failure).hasCauseInstanceOf(CharacterCodingException.class);
        }

        @Test
        void explicitWindows1251RejectsUnmappableBytes() throws IOException {
            Path path = file(join("a;b\n", INVALID_IN_BOTH, ";c\n"));

            FormatResolutionException failure = failure(path, explicitCharset(WINDOWS_1251));

            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(2);
        }

        @Test
        void explicitWindows1251IsUsedForDelimiterDetection() throws IOException {
            Path path = file((CYRILLIC + ";b\nc;d\n").getBytes(WINDOWS_1251));

            assertThat(resolver.resolve(path, explicitCharset(WINDOWS_1251)))
                    .isEqualTo(new Format(WINDOWS_1251, ';', SelectionSource.EXPLICIT, SelectionSource.SAMPLE));
        }

        @Test
        void incompleteSequenceAtProbeBoundaryIsNotAnError() throws IOException {
            String prefix = "a;b\nc;d\n";
            int cutInsideCharacter = prefix.length() + 1;
            Path path = file(prefix + CYRILLIC);

            assertThat(resolver.resolve(path, autoWith(cutInsideCharacter, SAMPLE)))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @Test
        void incompleteSequenceAtEndOfFileIsAnError() throws IOException {
            byte[] firstByteOfTwo = {(byte) 0xD0};
            Path path = file(join("a;b\nc;d\n", firstByteOfTwo));

            FormatResolutionException failure = failure(path, explicitCharset(UTF_8));

            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(3);
        }

        @Test
        void wholeProbeIsCheckedEvenAfterEnoughRecords() throws IOException {
            Path path = file(join("a;b\nc;d\ne;f\ng", INVALID_IN_BOTH, ";h\n"));
            Options sampleTwo = new Options(Optional.of(UTF_8), Optional.empty(), PROBE, 2);

            FormatResolutionException failure = failure(path, sampleTwo);

            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(4);
        }

        @Test
        void bytesBeyondTheProbeAreNotChecked() throws IOException {
            String prefix = "a;b\nc;d\n";
            Path path = file(join(prefix, INVALID_IN_BOTH));

            assertThat(resolver.resolve(path, autoWith(prefix.length(), SAMPLE)))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @Test
        void encodingErrorHasPriorityOverDelimiterProblems() throws IOException {
            Path path = file(join("a", INVALID_IN_BOTH, "\n"));

            assertThat(failure(path, auto()).reason()).isEqualTo(FailureReason.INVALID_ENCODING);
        }
    }

    @Nested
    class DelimiterDetection {

        @ParameterizedTest
        @ValueSource(chars = {';', ',', '\t', '|'})
        void detectsEveryCandidate(char delimiter) throws IOException {
            String content = "a" + delimiter + "b\nc" + delimiter + "d\n";

            assertThat(resolver.resolve(file(content), auto()).delimiter()).isEqualTo(delimiter);
            assertThat(resolver.resolve(file(content), auto()).delimiterSource()).isEqualTo(SelectionSource.SAMPLE);
        }

        @Test
        void otherCharactersAreNotCandidates() throws IOException {
            assertThat(failure(file("a:b\nc:d\n"), auto()).reason()).isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }

        @Test
        void explicitDelimiterIsNotCheckedAgainstTheStructure() throws IOException {
            assertThat(resolver.resolve(file("a;b\nc\n"), explicitDelimiter(':')))
                    .isEqualTo(new Format(UTF_8, ':', SelectionSource.SAMPLE, SelectionSource.EXPLICIT));
        }

        @Test
        void singleColumnFileWorksWithExplicitDelimiter() throws IOException {
            assertThat(resolver.resolve(file("a\n"), explicitDelimiter(',')))
                    .isEqualTo(new Format(UTF_8, ',', SelectionSource.SAMPLE, SelectionSource.EXPLICIT));
        }

        @Test
        void detectsDataMosRuFormatWithTwoHeadersAndTrailingDelimiter() throws IOException {
            String content = "\"id\";\"Name, full\";\r\n"
                    + "\"" + CYRILLIC + "\";\"" + CYRILLIC + "\";\r\n"
                    + "\"1\";\"Point; A\";\r\n";

            assertThat(resolver.resolve(file(content), auto()))
                    .isEqualTo(new Format(UTF_8, ';', SelectionSource.SAMPLE, SelectionSource.SAMPLE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"\n", "\r\n", "\r"})
        void supportsEveryLineEnding(String lineEnding) throws IOException {
            assertThat(resolver.resolve(file("a;b" + lineEnding + "c;d" + lineEnding), auto()).delimiter())
                    .isEqualTo(';');
        }

        @Test
        void blankLinesAreIgnored() throws IOException {
            assertThat(resolver.resolve(file("\na;b\n\n\r\nc;d\n\n"), auto()).delimiter()).isEqualTo(';');
        }

        @Test
        void lastRecordDoesNotNeedLineBreakAtEndOfFile() throws IOException {
            assertThat(resolver.resolve(file("a;b\nc;d"), auto()).delimiter()).isEqualTo(';');
        }

        @Test
        void delimitersInsideQuotesAreNotBoundaries() throws IOException {
            assertThat(resolver.resolve(file("\"a,b\";c\n\"d,e\";f\n"), auto()).delimiter()).isEqualTo(';');
        }

        @Test
        void lineBreaksInsideQuotesDoNotSplitRecords() throws IOException {
            assertThat(resolver.resolve(file("\"a\nb\";c\nd;e\n"), auto()).delimiter()).isEqualTo(';');
        }

        @Test
        void csvErrorExcludesOnlyTheAffectedCandidate() throws IOException {
            // With ',' the first field is "a"; followed by a stray character, so only ';' fits.
            assertThat(resolver.resolve(file("\"a\";b,c\n\"d\";e,f\n"), auto()).delimiter()).isEqualTo(';');
        }

        @Test
        void csvErrorsInEveryCandidateGiveInsufficientSample() throws IOException {
            assertThat(failure(file("\"a\",\"b\";c\n\"d\",\"e\";f\n"), auto()).reason())
                    .isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }

        @Test
        void severalFittingCandidatesAreAmbiguous() throws IOException {
            FormatResolutionException failure = failure(file("a;b,c\nd;e,f\n"), auto());

            assertThat(failure.reason()).isEqualTo(FailureReason.AMBIGUOUS_DELIMITER);
            assertThat(failure.sourceLine()).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "\n\n",
                "a\nb\n",
                "a;b\n",
                "a;b\nc;d;e\n",
        })
        void unsuitableContentGivesInsufficientSample(String content) throws IOException {
            FormatResolutionException failure = failure(file(content), auto());

            assertThat(failure.reason()).isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
            assertThat(failure.sourceLine()).isZero();
        }

        @Test
        void fileWithOnlyBomGivesInsufficientSample() throws IOException {
            assertThat(failure(file(UTF_8_BOM), auto()).reason()).isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }
    }

    @Nested
    class Bounds {

        @Test
        void onlyTheFirstRecordsAreAnalysed() throws IOException {
            Path path = file("a;b\nc;d\ne;f;g\n");

            assertThat(resolver.resolve(path, autoWith(PROBE, 2)).delimiter()).isEqualTo(';');
            assertThat(failure(path, autoWith(PROBE, 3)).reason()).isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }

        @Test
        void incompleteRecordAtProbeBoundaryIsNotCounted() throws IOException {
            String complete = "a;b\nc;d\n";
            Path path = file(complete + "e;f;g;h;i");

            assertThat(resolver.resolve(path, autoWith(complete.length() + 3, SAMPLE)).delimiter()).isEqualTo(';');
        }

        @Test
        void tooSmallProbeGivesInsufficientSample() throws IOException {
            Path path = file("a;b\nc;d\n");

            assertThat(failure(path, autoWith(5, SAMPLE)).reason()).isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }

        @Test
        void unclosedQuoteDoesNotExtendTheProbe() throws IOException {
            String complete = "a;b\nc;d\n";
            Path path = file(complete + "\"" + "x".repeat(1_000_000));

            assertThat(resolver.resolve(path, autoWith(complete.length() + 100, SAMPLE)).delimiter()).isEqualTo(';');
        }

        @Test
        void probeLimitIncludesBom() throws IOException {
            String content = "a;b\nc;d\n";
            Path path = file(join(UTF_8_BOM, content));

            assertThat(resolver.resolve(path, autoWith(UTF_8_BOM.length + content.length(), SAMPLE)).delimiter())
                    .isEqualTo(';');
            assertThat(failure(path, autoWith(UTF_8_BOM.length + content.length() - 1, SAMPLE)).reason())
                    .isEqualTo(FailureReason.INSUFFICIENT_SAMPLE);
        }
    }

    @Nested
    class Failures {

        @Test
        void missingFileIsAFileError() {
            Path missing = directory.resolve("missing.csv");

            assertThatThrownBy(() -> resolver.resolve(missing, auto()))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(FormatResolutionException.class);
        }

        @Test
        void fileIsClosedAfterSuccess() throws IOException {
            Path path = file("a;b\nc;d\n");

            resolver.resolve(path, auto());

            // On Windows a file that is still open can't be deleted.
            Files.delete(path);
            assertThat(path).doesNotExist();
        }

        @Test
        void fileIsClosedAfterFailure() throws IOException {
            Path path = file("a\n");

            assertThatThrownBy(() -> resolver.resolve(path, auto())).isInstanceOf(FormatResolutionException.class);

            Files.delete(path);
            assertThat(path).doesNotExist();
        }

        @Test
        void exceptionExposesItsDetails() {
            Path path = directory.resolve("input.csv");
            CharacterCodingException cause = new CharacterCodingException();

            FormatResolutionException failure =
                    new FormatResolutionException(path, FailureReason.INVALID_ENCODING, 7, cause);

            assertThat(failure).isInstanceOf(IOException.class).hasCause(cause);
            assertThat(failure.reason()).isEqualTo(FailureReason.INVALID_ENCODING);
            assertThat(failure.sourceLine()).isEqualTo(7);
            assertThat(failure.getMessage()).contains(path.toString());
        }

        @Test
        void messageNamesTheFileButNotTheInspectedText() throws IOException {
            Path path = file("marker\nmarker\n");

            FormatResolutionException failure = failure(path, auto());

            assertThat(failure.getMessage()).contains(path.toString()).doesNotContain("marker");
        }
    }
}
