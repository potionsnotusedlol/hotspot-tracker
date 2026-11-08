# [CONTRACT] Define CSV column mapping API (FR-IMP-03)

Status: local PR draft; not published. Source branch: `feature/FR-IMP-03-csv-mapping-contract`.
Initial review base: `feature/FR-IMP-01-stream-csv`, parent commit `bd27ae6`. Final merge target: master after FR-IMP-01 is merged and the child branch/base are updated.

## Requirement

FR-IMP-03 — configurable mapping of CSV columns to database fields for each dataset.

Closes #23 after the contract is approved, CI passes, and the PR is merged. The contract depends directly on CsvRecordReader.Record from FR-IMP-01 (Refs #12). Review this as a dependent PR against the reader branch. After FR-IMP-01 merges into master, update this branch and change the PR base to master before final merge. Confirm that its diff still contains only FR-IMP-03 changes.

## What was done

- Defined CsvColumnMapper with configuration, a per-file resolved plan, mapped record metadata, and diagnostic error categories.
- Documented exact header matching, missing/duplicate columns, conflicting target mappings, unused columns, empty values, and mismatched record widths.
- Defined configuration-driven handling of the two data.mos.ru headers and the trailing technical empty column.
- Separated fatal configuration/header failures from individual record mapping failures.
- Defined resolve and map entry points accepting CsvRecordReader.Record directly and documented preservation of its fields and metadata, immutable results, bounded record processing, and boundaries with validation and staging-table loading.
- Added API stubs and Javadoc only; mapping implementation and behavioral tests are deferred.

## How to verify

- Review CsvColumnMapper.java against Issue #23 and FR-IMP-03.
- Compile both production types with `javac --release 25 -Xlint:all -d /tmp/fr-imp-03-contract server/src/main/java/ru/moscow/hotspot_tracker/main/importing/CsvRecordReader.java server/src/main/java/ru/moscow/hotspot_tracker/main/importing/CsvColumnMapper.java`.
- Verify that operations, validating constructors, and diagnostic accessors remain stubs.
- Review the adapter contract: pass the first Record to resolve; skip configured header records; pass each data Record directly to map.

## Tests

- Contract compilation passed for Java 25 without compiler warnings.
- A temporary compile-only adapter compiled against the reader inherited from the parent branch. Both entry points accept Record directly.
- No behavioral tests were added or run for this task.
- Javadoc HTML/doclint could not run because the javadoc executable is unavailable.
- Gradle and CI checks have not been run for this branch.

## Definition of Done

- [x] Dataset-specific mapping configuration and public API are defined.
- [x] Header resolution, data.mos.ru headers, and technical-column handling are documented.
- [x] Mapping output preserves the source record number and source line.
- [x] Edge cases, error categories, and continuation/termination rules are documented.
- [x] Integration with the reader and scope boundaries are documented.
- [x] The contract compiles; methods remain stubs, with implementation and behavioral tests deferred.

Issue #23 remains open until review, successful CI checks, and merge. Create a separate implementation Issue before implementing this API. Reviewers and affected test/import developers should be notified when this PR is opened; no notifications have been sent.
