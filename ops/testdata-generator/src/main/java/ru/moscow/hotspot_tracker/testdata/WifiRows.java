package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/** City Wi-Fi access points: around the districts of Moscow, some at metro stations. */
final class WifiRows implements RowSource {
    /** Above the real global_id values, so synthetic rows can be loaded next to the real ones. */
    private static final long ID_BASE = 3_000_000_000L;

    private final Values values;

    WifiRows(Values values) {
        this.values = values;
    }

    @Override
    public List<String> next(long index) {
        String admArea;
        String district;
        String location;
        double[] point;
        Dictionaries.MetroStation station = values.pick(Dictionaries.METRO_STATIONS);
        if (station.inMoscow() && values.chance(0.15)) {
            admArea = station.admArea();
            district = station.district();
            location = "\u0421\u0442\u0430\u043d\u0446\u0438\u044f \u043c\u0435\u0442\u0440\u043e \u00ab"
                    + station.name() + "\u00bb";
            point = values.near(station.lat(), station.lon(), 0.0008);
        } else {
            Dictionaries.District area = values.district();
            admArea = area.admArea();
            district = area.name();
            location = "\u0433\u043e\u0440\u043e\u0434 \u041c\u043e\u0441\u043a\u0432\u0430, "
                    + values.pick(Dictionaries.STREETS) + ", \u0434\u043e\u043c " + values.between(1, 120)
                    + (values.chance(0.25) ? ", \u043a\u043e\u0440\u043f\u0443\u0441 " + values.between(1, 5) : "");
            point = values.near(area.lat(), area.lon(), area.spread());
        }
        int decimals = values.between(6, 9);
        String lat = Values.fixed(point[0], decimals);
        String lon = Values.fixed(point[1], decimals);
        String geo = Values.geo(lat, lon);
        List<String> row = new ArrayList<>(15);
        row.add(lat);
        row.add("\u0413\u043e\u0440\u043e\u0434\u0441\u043a\u043e\u0439 Wi-Fi");
        row.add(admArea);
        row.add(district);
        row.add(location);
        row.add(accessPoints());
        row.add("Moscow_WiFi_Free");
        row.add("50");
        row.add("\u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442");
        row.add("\u043e\u0442\u043a\u0440\u044b\u0442\u0430\u044f \u0441\u0435\u0442\u044c");
        row.add("");
        row.add(lon);
        row.add(String.valueOf(ID_BASE + index));
        row.add(geo);
        row.add(geo);
        return row;
    }

    /** Number of access points with the shares of the real file: 86% one, 12% two, the rest from three to nine. */
    private String accessPoints() {
        double roll = values.uniform();
        if (roll < 0.86) {
            return "1";
        }
        return roll < 0.98 ? "2" : String.valueOf(values.between(3, 9));
    }
}
