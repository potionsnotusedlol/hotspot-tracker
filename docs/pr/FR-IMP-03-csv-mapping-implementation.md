# feat(import): implement CSV column mapping (FR-IMP-03)

Status: local PR draft; not published.
Branch: `feature/FR-IMP-03-csv-mapping-implementation`.
Base: `feature/FR-IMP-03-csv-mapping-contract`, commit `3916a2066f74330790eacbd804331aa44fce6c63` (contract plus DevOps tests).
Implementation commit: see this file's Git history. PR number/URL: not assigned.
Open as a draft against the contract branch until the contract is approved and merged.

## Requirement

FR-IMP-03 — configurable mapping of CSV columns to database fields for each dataset.

Refs #32 (implementation), #23 (contract), and #12 (reader dependency).
Contract PR has not been published as of the last verification. Implementation starts before approval under the agreed ПЛАНН exception. Update it if review changes the contract.
Issue #32 remains open pending final verification, CI, review, and merge.

## What was done

- Implemented configuration constructors, header resolution, per-record mapping, and diagnostic exceptions without changing public signatures.
- Preserved exact names, original string values, source metadata, configured field order, and immutable snapshots.
- Implemented validation priorities, direct-plan validation, header-number checks, and technical-column checks.
- Preserved the original contract source in `docs/contracts/FR-IMP-03-contract-75f06d4.md`.

## How to verify

Compile the mapper and reader from the repository root:

```sh
javac --release 25 -Xlint:all -d /tmp/fr-imp-03-review server/src/main/java/ru/moscow/hotspot_tracker/main/importing/CsvRecordReader.java server/src/main/java/ru/moscow/hotspot_tracker/main/importing/CsvColumnMapper.java
```

From `server`, run mapper and reader behavioral tests:

```sh
./gradlew test --tests '*CsvColumnMapperTest' --tests '*CsvRecordReaderTest' --no-daemon
```

The remote contract branch now includes CsvColumnMapperTest.java from tsar_161, commit `3916a2066f74330790eacbd804331aa44fce6c63` (2026-11-09). This commit adds only the test file and does not change the contract. The exact remote commit has been integrated into both local contract and implementation branches by fast-forward, without a merge commit.

## Tests

- Reader and mapper compiled with `javac --release 25 -Xlint:all` without warnings.
- Reflection-based comparison confirmed unchanged public members across all nine mapper API types against contract commit `75f06d4`.
- A temporary standalone Java harness passed 94 checks covering configuration errors, header validation priorities, direct-plan validation, immutable snapshots, metadata, mapping, diagnostics, and reader integration with continuation after mapping rejection.
- The same harness completed 1,000,000 mappings without accumulating results in a JVM limited to `-Xmx32m`. This is a component check, not an end-to-end import benchmark.
- The harness was executed directly with Java, not Gradle or JUnit; it is local verification, not the separately maintained behavioral suite.
- Gradle failed before compilation/test execution: `Could not determine a usable wildcard IP for this machine`, including outside the sandbox. No Gradle test result or CI result is claimed.
- `git diff --check` passed.
- Fresh DevOps mapper tests from commit `3916a2066f74330790eacbd804331aa44fce6c63` and the existing reader tests passed together: 179 tests found, 179 started, 179 successful, none skipped or failed. Compiled with Java 27 using `--release 25` and executed directly through JUnit Platform Launcher 6.0.3 with dependencies from the local Gradle cache. The downloaded mapper test source was used without behavioral changes in a temporary directory. This is a JUnit result, not a Gradle or CI result.
- The DevOps test commit is now inherited from the updated contract branch. Test files were not edited by the implementation.

## Definition of Done

- [x] Mapper and reader behavioral tests pass through JUnit Platform, including reader/mapper integration. Gradle and CI verification remain pending.
- [x] No secrets in the code, no `TODO` without a task number.
- Access rights: not applicable to this in-memory mapper; it does not access user resources.
- Errors: contract exceptions only; no HTTP endpoint or logging added. Record values are excluded from diagnostic messages.
- [x] Documentation updated; public API and agreed behavior preserved.
- [x] I ran the component myself and checked the result.

Contract approval, Gradle/CI verification, review, and merge are not complete. Do not close the implementation Issue based on these local checks.
