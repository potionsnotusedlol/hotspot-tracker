# feat(import): implement CSV format selection (FR-IMP-02)

Status: local PR draft; not published.
Branch: `feature/FR-IMP-02-csv-format`.
Implementation base: `53e7f4a` (existing format resolver contract).
Related task: #17, as supplied by the user.

## Requirement

Implement independent explicit or automatic selection of CSV encoding and delimiter, supporting UTF-8 and Windows-1251, while preserving the CsvFormatResolver API.

## What was done

- Implemented options validation, diagnostic exceptions, and resolve(Path, Options).
- Added bounded prefix sampling, BOM checks, strict decoding, and UTF-8/Windows-1251 selection.
- Evaluated delimiter candidates with quoted fields, multiline records, complete-record boundaries, and consistent column counts.
- Reported ambiguous and insufficient samples without silently guessing.
- Kept fully explicit selection independent of file access and updated Javadoc.
- Added CsvFormatResolverTest. It verifies the resolver independently; CsvRecordReader remains the existing contract stub in this branch.

## How to verify

From `server`, run:

```sh
./gradlew test --tests '*CsvFormatResolverTest'
```

Review sampling limits, encoding failures, BOM conflicts, and artificial sample boundaries against the Javadoc.

## Tests

- All 10 resolver tests passed in the standalone run after separation, compiled against the reader contract stub.
- Targeted tests were compiled with Java 27 and executed using local JUnit assertions through a standalone runner, independently in this branch.
- This split revision has not been run through Gradle in the assistant environment: its last invocation failed before tests with `Could not determine a usable wildcard IP for this machine`.
- The earlier combined revision had 24 passing tests in the user's Gradle XML report. That report is historical evidence, not a Gradle result for these split commits.
- The full application suite has not been verified.

## Definition of Done

- Resolver stubs are replaced without changing public signatures.
- Encoding and delimiter selection follow the documented contract.
- Relevant tests pass without the reader implementation.
- Full Gradle verification of this split revision remains pending.
