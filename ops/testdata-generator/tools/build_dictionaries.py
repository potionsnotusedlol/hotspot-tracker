"""Builds Dictionaries.java for the test data generator from the data.mos.ru archives in data/.

The generator must not depend on the real files at run time, so the values it needs (headers,
districts with their centres, metro and MCD stations, street and stop names, regions of houses.csv)
are extracted once and stored as Java constants. Non-ASCII characters are written as \\uXXXX
escapes, because the repository keeps source files in English.

Run from the repository root after the archives in data/ are updated:

    python ops/testdata-generator/tools/build_dictionaries.py
"""

import collections
import csv
import io
import statistics
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / "data"
OUTPUT = (ROOT / "ops/testdata-generator/src/main/java/ru/moscow/hotspot_tracker/testdata/Dictionaries.java")

# Archive name prefixes (the archive names are in Russian)
ARCHIVES = {
    "wifi": "\u0413\u043e\u0440\u043e\u0434\u0441\u043a\u043e\u0439 WiFi",
    "stops": "\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u043e\u0447\u043d\u044b",
    "metro": "\u041c\u0435\u0442\u0440\u043e",
    "mcd": "\u041c\u0426\u0414",
}
REGIONS = 8
STREETS = 80
STOP_NAMES = 80
EXIT_SUFFIX = " (\u043f\u0440\u043e\u0445\u043e\u0434"  # " (exit" in MCD exit names
HOUSE_SUFFIX = ", \u0434."  # ", house" in addresses
YES = "\u0434\u0430"


def read_archive(prefix):
    matches = [p for p in DATA.glob("*.zip") if p.name.startswith(prefix)]
    if len(matches) != 1:
        sys.exit(f"Expected one archive starting with {prefix!r}, found {len(matches)}")
    with zipfile.ZipFile(matches[0]) as archive:
        entry = archive.infolist()[0]
        text = archive.read(entry).decode("utf-8")
    return list(csv.reader(io.StringIO(text, newline=""), delimiter=";"))


def java(value):
    out = []
    for ch in value:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ord(ch) < 0x20 or ord(ch) > 0x7E:
            out.append(f"\\u{ord(ch):04x}")
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


def strings(values):
    return "List.of(\n" + ",\n".join("            " + java(v) for v in values) + ")"


def spread(values):
    if len(values) < 2:
        return 0.005
    return min(max(statistics.pstdev(values), 0.003), 0.03)


def column(rows, name):
    return rows[0].index(name)


def districts(stops):
    lat, lon = column(stops, "Latitude_WGS84"), column(stops, "Longitude_WGS84")
    area, district = column(stops, "AdmArea"), column(stops, "District")
    points = collections.defaultdict(list)
    for row in stops[2:]:
        points[(row[area], row[district])].append((float(row[lat]), float(row[lon])))
    result = []
    for (adm_area, name), values in sorted(points.items()):
        lats = [p[0] for p in values]
        lons = [p[1] for p in values]
        result.append((adm_area, name, statistics.fmean(lats), statistics.fmean(lons),
                       spread(lats), len(values)))
    return result


def metro_stations(metro):
    idx = {name: column(metro, name) for name in
           ("NameOfStation", "Line", "OnTerritoryOfMoscow", "AdmArea", "District",
            "Latitude_WGS84", "Longitude_WGS84")}
    groups = collections.defaultdict(list)
    for row in metro[2:]:
        groups[(row[idx["NameOfStation"]], row[idx["Line"]])].append(row)
    result = []
    for (station, line), rows in sorted(groups.items()):
        first = rows[0]
        result.append((station, line, first[idx["OnTerritoryOfMoscow"]] == YES,
                       first[idx["AdmArea"]], first[idx["District"]],
                       statistics.fmean(float(r[idx["Latitude_WGS84"]]) for r in rows),
                       statistics.fmean(float(r[idx["Longitude_WGS84"]]) for r in rows)))
    return result


def mcd_stations(mcd):
    idx = {name: column(mcd, name) for name in
           ("Name", "OnTerritoryOfMoscow", "AdmArea", "District", "Latitude_WGS84", "Longitude_WGS84")}
    groups = collections.defaultdict(list)
    for row in mcd[2:]:
        groups[row[idx["Name"]].split(EXIT_SUFFIX)[0]].append(row)
    result = []
    for station, rows in sorted(groups.items()):
        first = rows[0]
        result.append((station, first[idx["OnTerritoryOfMoscow"]] == YES, first[idx["AdmArea"]],
                       first[idx["District"]],
                       statistics.fmean(float(r[idx["Latitude_WGS84"]]) for r in rows),
                       statistics.fmean(float(r[idx["Longitude_WGS84"]]) for r in rows)))
    return result


def streets(stops):
    place = column(stops, "PlaceDescription")
    counter = collections.Counter(row[place].split(HOUSE_SUFFIX)[0] for row in stops[2:]
                                  if HOUSE_SUFFIX in row[place])
    return sorted(name for name, _ in counter.most_common(STREETS))


def stop_names(stops):
    name = column(stops, "StationName")
    return sorted(n for n, _ in collections.Counter(r[name] for r in stops[2:]).most_common(STOP_NAMES))


def regions():
    with zipfile.ZipFile(DATA / "houses.zip") as archive, archive.open(archive.infolist()[0]) as raw:
        reader = csv.reader(io.TextIOWrapper(raw, encoding="utf-8", newline=""))
        header = next(reader)
        lat, lon = header.index("latitude"), header.index("longitude")
        region, locality = header.index("region"), header.index("locality_name")
        points = collections.defaultdict(list)
        localities = collections.defaultdict(collections.Counter)
        for row in reader:
            try:
                points[row[region]].append((float(row[lat]), float(row[lon])))
            except ValueError:
                continue
            localities[row[region]][row[locality]] += 1
    top = sorted(points, key=lambda r: -len(points[r]))[:REGIONS]
    total = sum(len(points[r]) for r in points)
    result = []
    for name in top:
        lats = [p[0] for p in points[name]]
        lons = [p[1] for p in points[name]]
        result.append((name, localities[name].most_common(1)[0][0], statistics.fmean(lats),
                       statistics.fmean(lons), min(statistics.pstdev(lats), 1.0),
                       len(points[name]) / total))
    return header, result


def main():
    wifi = read_archive(ARCHIVES["wifi"])
    stops = read_archive(ARCHIVES["stops"])
    metro = read_archive(ARCHIVES["metro"])
    mcd = read_archive(ARCHIVES["mcd"])
    houses_header, house_regions = regions()

    def header(rows):
        # The trailing delimiter of data.mos.ru files produces an empty last column
        return [strings(rows[0][:-1]), strings(rows[1][:-1])]

    lines = [
        "package ru.moscow.hotspot_tracker.testdata;",
        "",
        "import java.util.List;",
        "",
        "/**",
        " * Values taken from the real data.mos.ru datasets and houses.csv.",
        " *",
        " * <p>Generated by {@code ops/testdata-generator/tools/build_dictionaries.py}; do not edit by hand.",
        " * Non-ASCII characters are unicode escapes because the repository keeps sources in English.",
        " */",
        "final class Dictionaries {",
        "    private Dictionaries() {}",
        "",
        "    /** A district with the centre and the spread (degrees of latitude) of its stops. */",
        "    record District(String admArea, String name, double lat, double lon, double spread, int weight) {}",
        "",
        "    /** A metro or MCC station on one line, with the centre of its exits. */",
        "    record MetroStation(String name, String line, boolean inMoscow, String admArea, String district,",
        "            double lat, double lon) {}",
        "",
        "    /** An MCD station with the centre of its exits. */",
        "    record McdStation(String name, boolean inMoscow, String admArea, String district, double lat, double lon) {}",
        "",
        "    /** A region of houses.csv with its centre, spread and share of the rows. */",
        "    record Region(String name, String locality, double lat, double lon, double spread, double share) {}",
        "",
    ]
    for name, rows in (("WIFI", wifi), ("STOPS", stops), ("METRO", metro), ("MCD", mcd)):
        codes, captions = header(rows)
        lines.append(f"    static final List<String> {name}_CODES = {codes};")
        lines.append("")
        lines.append(f"    static final List<String> {name}_CAPTIONS = {captions};")
        lines.append("")
    lines.append(f"    static final List<String> HOUSES_HEADER = {strings(houses_header)};")
    lines.append("")
    lines.append("    static final List<District> DISTRICTS = List.of(")
    lines.append(",\n".join(
        f"            new District({java(a)}, {java(n)}, {lat:.6f}, {lon:.6f}, {s:.4f}, {w})"
        for a, n, lat, lon, s, w in districts(stops)) + ");")
    lines.append("")
    lines.append("    static final List<MetroStation> METRO_STATIONS = List.of(")
    lines.append(",\n".join(
        f"            new MetroStation({java(n)}, {java(l)}, {str(m).lower()}, {java(a)}, {java(d)}, {lat:.6f}, {lon:.6f})"
        for n, l, m, a, d, lat, lon in metro_stations(metro)) + ");")
    lines.append("")
    lines.append("    static final List<McdStation> MCD_STATIONS = List.of(")
    lines.append(",\n".join(
        f"            new McdStation({java(n)}, {str(m).lower()}, {java(a)}, {java(d)}, {lat:.6f}, {lon:.6f})"
        for n, m, a, d, lat, lon in mcd_stations(mcd)) + ");")
    lines.append("")
    lines.append("    static final List<Region> REGIONS = List.of(")
    lines.append(",\n".join(
        f"            new Region({java(n)}, {java(loc)}, {lat:.6f}, {lon:.6f}, {s:.4f}, {share:.4f})"
        for n, loc, lat, lon, s, share in house_regions) + ");")
    lines.append("")
    lines.append(f"    static final List<String> STREETS = {strings(streets(stops))};")
    lines.append("")
    lines.append(f"    static final List<String> STOP_NAMES = {strings(stop_names(stops))};")
    lines.append("}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
