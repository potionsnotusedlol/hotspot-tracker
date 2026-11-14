package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/** Exits of MCD stations, next to the real stations. Some of them are outside Moscow, as in the real file. */
final class McdRows implements RowSource {
    private static final long ID_BASE = 6_000_000_000L;

    private final Values values;

    McdRows(Values values) {
        this.values = values;
    }

    @Override
    public List<String> next(long index) {
        Dictionaries.McdStation station = values.pick(Dictionaries.MCD_STATIONS);
        double[] point = values.near(station.lat(), station.lon(), 0.0008);
        String lat = Values.fixed(point[0], 6);
        String lon = Values.fixed(point[1], 6);
        List<String> row = new ArrayList<>(13);
        row.add(station.name() + " (\u043f\u0440\u043e\u0445\u043e\u0434 " + values.between(1, 6) + ")");
        row.add("nested data");
        row.add("\u0412\u0445\u043e\u0434-\u0432\u044b\u0445\u043e\u0434");
        row.add(station.inMoscow() ? "\u0434\u0430" : "\u043d\u0435\u0442");
        row.add(station.inMoscow() ? station.admArea() : "");
        row.add(station.inMoscow() ? station.district() : "");
        row.add(lat);
        row.add(lon);
        row.add(
                values.chance(0.02)
                        ? values.pick(Dictionaries.METRO_STATIONS).name()
                                + ", \u0432\u0445\u043e\u0434-\u0432\u044b\u0445\u043e\u0434 1 \u0432 \u0432\u0435\u0441\u0442\u0438\u0431\u044e\u043b\u044c"
                        : "");
        row.add("\u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442");
        row.add(String.valueOf(ID_BASE + index));
        row.add(Values.geo(lat, lon));
        // Always empty in the real file
        row.add("");
        return row;
    }
}
