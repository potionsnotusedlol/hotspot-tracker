package ru.moscow.hotspot_tracker.testdata;

import java.util.List;

/** The datasets the generator can produce, with the columns of the real files. */
enum Dataset {
    /** City Wi-Fi access points, data.mos.ru dataset 2756. */
    WIFI(
            "wifi",
            List.of(Dictionaries.WIFI_CODES, Dictionaries.WIFI_CAPTIONS),
            new Columns(
                    "global_id",
                    "Latitude_WGS84",
                    "Longitude_WGS84",
                    List.of("geoData", "geodata_center"),
                    "Location",
                    "NumberOfAccessPoints")),
    /** Ground transport stops, data.mos.ru dataset 752. */
    STOPS(
            "stops",
            List.of(Dictionaries.STOPS_CODES, Dictionaries.STOPS_CAPTIONS),
            new Columns(
                    "global_id",
                    "Latitude_WGS84",
                    "Longitude_WGS84",
                    List.of("geoData", "geodata_center"),
                    "Name",
                    "ID")),
    /** Metro and MCC exits, data.mos.ru dataset 624. */
    METRO(
            "metro",
            List.of(Dictionaries.METRO_CODES, Dictionaries.METRO_CAPTIONS),
            new Columns(
                    "global_id",
                    "Latitude_WGS84",
                    "Longitude_WGS84",
                    List.of("geoData", "geodata_center"),
                    "Name",
                    "BPAAmount")),
    /** MCD exits, data.mos.ru dataset 62207. */
    MCD(
            "mcd",
            List.of(Dictionaries.MCD_CODES, Dictionaries.MCD_CAPTIONS),
            new Columns("global_id", "Latitude_WGS84", "Longitude_WGS84", List.of("geoData"), "Name", "global_id")),
    /** Residential houses of Russia, houses.csv. */
    HOUSES(
            "houses",
            List.of(Dictionaries.HOUSES_HEADER),
            new Columns("house_id", "latitude", "longitude", List.of(), "address", "square"));

    /**
     * Columns the broken rows change.
     *
     * @param id identifier of the row
     * @param latitude latitude column
     * @param longitude longitude column
     * @param geo columns with the coordinates as {@code {coordinates=[lon, lat], type=Point}}
     * @param name free text column for payloads and line breaks
     * @param number numeric column for a dash
     */
    record Columns(String id, String latitude, String longitude, List<String> geo, String name, String number) {}

    private final String option;
    private final List<List<String>> header;
    private final Columns columns;

    Dataset(String option, List<List<String>> header, Columns columns) {
        this.option = option;
        this.header = header;
        this.columns = columns;
    }

    /** Name used on the command line. */
    String option() {
        return option;
    }

    List<List<String>> header() {
        return header;
    }

    Columns columns() {
        return columns;
    }

    /** Whether the file follows data.mos.ru: two header rows, all fields quoted, a trailing delimiter. */
    boolean dataMosRu() {
        return this != HOUSES;
    }

    char defaultDelimiter() {
        return dataMosRu() ? ';' : ',';
    }

    /** The line ending of the real file: LF for data.mos.ru, CRLF for houses.csv. */
    String defaultLineEnding() {
        return dataMosRu() ? "\n" : "\r\n";
    }

    /** Index of a column by its code in the first header row. */
    int column(String name) {
        int index = header.get(0).indexOf(name);
        if (index < 0) {
            throw new IllegalStateException(option + " has no column " + name);
        }
        return index;
    }

    RowSource rows(Options options, Values values) {
        return switch (this) {
            case WIFI -> new WifiRows(values);
            case STOPS -> new StopRows(values);
            case METRO -> new MetroRows(values);
            case MCD -> new McdRows(values);
            case HOUSES -> new HouseRows(values, options.moscowShare());
        };
    }

    static Dataset fromOption(String value) {
        for (Dataset dataset : values()) {
            if (dataset.option.equals(value)) {
                return dataset;
            }
        }
        throw new IllegalArgumentException("Unknown dataset: " + value);
    }
}
