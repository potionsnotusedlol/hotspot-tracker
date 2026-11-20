# feat(import): implement streaming CSV reader (FR-IMP-01)

Status: local PR draft; not published.
Branch: `feature/FR-IMP-01-stream-csv`.
Implementation base: `bfd6229` (existing reader contract).

## Requirement

Implement the CsvRecordReader contract for streaming CSV input, including its specified rejection handling, while preserving the public API.

## What was done

- Implemented immutable record snapshots, input validation, buffered strict decoding, and CSV parsing.
- Preserved quoted/multiline fields, escaped quotes, empty columns, headers, BOM handling, and physical line numbers.
- Enforced field and record limits and streamed rejected text through a private nested Reader with bounded recovery and callback-scoped lifetime.
- Preserved handler exceptions and updated Javadoc to describe the implementation.
- Added CsvRecordReaderTest. These tests do not require CsvFormatResolver or its implementation.

## How to verify

From `server`, run:

```sh
./gradlew test --tests '*CsvRecordReaderTest'
```

Review rejection-stream lifetime, raw CR/LF preservation, limit priorities, and recovery against the Javadoc.

## Tests

- All 16 reader tests passed in the standalone run after separation.
- Targeted tests were compiled with Java 27 and executed using local JUnit assertions through a standalone runner, independently in this branch.
- This split revision has not been run through Gradle in the assistant environment: its last invocation failed before tests with `Could not determine a usable wildcard IP for this machine`.
- The earlier combined revision had 24 passing tests in the user's Gradle XML report. That report is historical evidence, not a Gradle result for these split commits.
- The full application suite has not been verified.

- The unchanged reader implementation previously streamed 96 MiB of malformed text with a 32 MiB heap and read the following valid record.
- End-to-end import throughput has not been measured.

## Definition of Done

- Reader stubs are replaced without changing public signatures.
- Record and rejection processing use bounded memory.
- Relevant tests pass independently of the format resolver.
- Full Gradle verification of this split revision remains pending.
