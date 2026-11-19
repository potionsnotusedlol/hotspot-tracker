# Test data generator

Writes synthetic CSV files in the formats of the data.mos.ru datasets and `houses.csv`. The real files are small (about 5 thousand Wi-Fi points), so the import requirements for 10⁶ and 10⁷ rows (FR-IMP-01, NFR-PERF-04) and the load tests are checked on these files. They also contain broken rows for the validation tests (FR-IMP-05) and payloads for the web interface and the chat bot.

The files have no personal data: names, addresses and coordinates are made up from dictionaries of streets, districts and stations.

## Run

From `ops/testdata-generator`:

```sh
./gradlew run --args="--dataset wifi --rows 1000000 --output build/data/wifi.csv"
```

A relative `--output` is resolved from `ops/testdata-generator`. To run it many times without Gradle:

```sh
./gradlew installDist
build/install/testdata-generator/bin/testdata-generator --dataset houses --rows 10000000 --output /tmp/houses.csv
```

On Windows use `gradlew.bat` and `build\install\testdata-generator\bin\testdata-generator.bat`.

`--help` lists all options.

| Option | Default | |
|---|---|---|
| `--dataset` | — | `wifi`, `stops`, `metro`, `mcd`, `houses` |
| `--rows` | — | data rows, without the header |
| `--output` | — | file to write; an existing file is replaced |
| `--seed` | `1` | the same seed and options give the same file, byte for byte |
| `--charset` | `utf-8` | or `windows-1251` |
| `--bom` | off | UTF-8 byte order mark at the start |
| `--delimiter` | as in the real file | one character, or `tab` |
| `--line-ending` | as in the real file | `lf` or `crlf` |
| `--dirty-rate` | `0` | share of broken rows, from 0 to 1 |
| `--dirty` | all kinds | comma-separated kinds, see below |
| `--moscow-share` | `0.0446` | houses only: share of Moscow houses |

## Formats

The defaults repeat the real files:

| Dataset | Real file | Delimiter | Quotes | Header rows | Line ending |
|---|---|---|---|---|---|
| `wifi` | data.mos.ru 2756, Wi-Fi access points | `;` | every field | 2 (codes and captions) | LF |
| `stops` | data.mos.ru 752, ground transport stops | `;` | every field | 2 | LF |
| `metro` | data.mos.ru 624, metro and MCC exits | `;` | every field | 2 | LF |
| `mcd` | data.mos.ru 62207, MCD exits | `;` | every field | 2 | LF |
| `houses` | `houses.csv`, houses of Russia | `,` | only when needed | 1 | CRLF |

- data.mos.ru rows end with `;`, so the last column is empty. `geoData` repeats the coordinates as `{coordinates=[lon, lat], type=Point}`. Some columns hold `nested data`, as in the real files.
- Points are spread around the real districts as often as the real stops are, so the spatial density is close to the real city. Metro and MCD exits are next to the real stations; some MCD stations are outside Moscow.
- `houses` has Moscow and seven other regions with their real shares, so the import has to filter by region. Area and population use spaces between thousands (`2 655.30`), and `—`, empty values and years written as text ("before 1945" in Russian) appear as often as in the real file.
- Identifiers (`global_id`) start above the real ones, so synthetic rows can be loaded next to the real data.

## Broken rows

With `--dirty-rate R`, each row is broken with probability `R` in one of the chosen ways:

| Kind | What changes |
|---|---|
| `bad-coordinates` | comma as decimal separator, swapped latitude and longitude, text or out of range |
| `outside-moscow` | valid coordinates around Saint Petersburg |
| `duplicate-id` | the identifier of the previous row |
| `missing-required` | empty identifier or empty coordinates |
| `dash-number` | `—` in a numeric column |
| `newline-in-quotes` | a line break inside a quoted value (valid CSV) |
| `broken-quote` | an unescaped quote: the row is invalid CSV |
| `xss` | `<script>alert('xss')</script>` appended to a name |
| `prompt-injection` | "Ignore all previous instructions…" at the start of a name |

Other encodings, delimiters, BOM and line endings come from the format options. Example of a dirty file in Windows-1251:

```sh
./gradlew run --args="--dataset stops --rows 100000 --output build/data/stops-dirty.csv --charset windows-1251 --dirty-rate 0.05"
```

## Size and speed

One million rows take 2–3 seconds and about 400–500 MiB on disk; ten million rows about 4–5 GiB. Rows are written one by one, so memory does not depend on the size (64 MiB of heap is enough). Generated files are not committed: keep them in `build/` or outside the repository.

## Dictionaries

`Dictionaries.java` holds the headers and the values taken from the real files: districts with the centres of their stops, metro and MCD stations, streets, stop names and regions of `houses.csv`. After updating the archives in `data/`, rebuild it from the repository root:

```sh
python ops/testdata-generator/tools/build_dictionaries.py
```

Non-ASCII characters in Java sources are unicode escapes: the repository keeps sources in English.
