# CSV column mapping contract (FR-IMP-03)

Status: local contract draft for Issue #23; not reviewed or published.
Branch: `feature/FR-IMP-03-csv-mapping-contract`.
Current base: `feature/FR-IMP-01-stream-csv`, commit `bd27ae63fabd9891819dd576a0db0186cb01c85b` (the local FR-IMP-01 branch, matching the fetched remote-tracking ref). The draft is stacked on FR-IMP-01. Latest live GitHub state has not been verified because DNS access failed.

The authoritative API draft is `server/src/main/java/ru/moscow/hotspot_tracker/main/importing/CsvColumnMapper.java` and its Javadoc.

## API

- `ColumnMapping`: exact source header name and target field key.
- `Options`: dataset identifier, mappings, one or two header records, and explicit trailing technical-column handling.
- `ResolvedMapping`: immutable per-file header and resolved source indexes in configuration order.
- `MappedRecord`: immutable ordered string values with the original record number and source line.
- `resolve(Options, CsvRecordReader.Record)`: validate the header and prepare a reusable mapping plan.
- `map(ResolvedMapping, CsvRecordReader.Record)`: map one data record.
- `ConfigurationException`: preparation failure; fix configuration/header before processing data.
- `RecordMappingException`: record-level mapping failure; caller may report the rejection and continue.

All executable mapping operations, validating record constructors, and diagnostic constructors/accessors are stubs throwing UnsupportedOperationException. There is no mapping implementation or behavioral test.

## Integration and dependencies

FR-IMP-03 depends directly on the FR-IMP-01 `CsvRecordReader.Record` type. The mapper receives header and data records without decomposing them into separate arguments. It preserves number() and sourceLine() and uses fields() for mapping. FR-IMP-02 determines file encoding/delimiter before reading and is not called by the mapper.

For dependent review, open the FR-IMP-03 PR with base `feature/FR-IMP-01-stream-csv` and head `feature/FR-IMP-03-csv-mapping-contract`. This shows only FR-IMP-03 changes. Do not merge the child PR into the parent feature branch merely to close Issue #23. First merge FR-IMP-01 into master; then update the child branch to that master, preserving only its own commits if the parent was squash/rebase merged, change the PR base to master, verify the diff and CI, and merge FR-IMP-03. Close Issue #23 after that review/CI/merge sequence. Keep the implementation Issue separate.

The caller resolves the first header, skips the configured number of header records, and maps subsequent records. One new plan is required for each file. Mapping is not CSV parsing, configuration-file loading, type conversion, business validation, SQL generation, or database persistence.

## Defined behavior

- Exact, case-sensitive header matching without trimming.
- Unique target field keys; one source may feed multiple distinct targets.
- All configured source columns must exist; unused named columns are allowed.
- Blank/duplicate header names are errors, except an explicitly configured final empty technical header.
- Data records must have the full header width; an expected technical value must be exactly empty.
- Empty mapped values and whitespace are preserved for later validation.
- Output order follows configuration, original numbering is preserved, and records are not accumulated.
- Errors describe dataset, category, and record metadata where applicable, without logging field contents.

## Verification

- `javac --release 25 -Xlint:all`: passed for the new contract.
- A temporary compile-only adapter using Record directly compiled with the reader currently inherited from `bd27ae6`. No behavioral mapping tests were run.
- `git diff --check`: passed.
- The javadoc executable is unavailable in this environment; HTML generation/doclint was not completed.
- No behavioral tests or Gradle test run were performed for this contract-only task.
