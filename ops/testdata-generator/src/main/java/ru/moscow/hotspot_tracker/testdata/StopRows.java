package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/** Ground transport stops around the districts of Moscow. */
final class StopRows implements RowSource {
    private static final long ID_BASE = 4_000_000_000L;
    private static final List<String> ROUTE_PREFIXES =
            List.of("", "\u0410", "\u0422", "\u0422\u043c", "\u041d", "\u0421", "\u0410\u0441");

    private final Values values;

    StopRows(Values values) {
        this.values = values;
    }

    @Override
    public List<String> next(long index) {
        Dictionaries.District area = values.district();
        double[] point = values.near(area.lat(), area.lon(), area.spread());
        int decimals = values.between(7, 8);
        String lat = Values.fixed(point[0], decimals);
        String lon = Values.fixed(point[1], decimals);
        String geo = Values.geo(lat, lon);
        String id = String.valueOf(index + 1);
        String stopName = values.pick(Dictionaries.STOP_NAMES);
        String street = values.pick(Dictionaries.STREETS);
        String direction = values.chance(0.5)
                ? "\u043e\u0442 \u0446\u0435\u043d\u0442\u0440\u0430"
                : "\u0432 \u0446\u0435\u043d\u0442\u0440";
        boolean pavilion = values.chance(0.75);
        List<String> row = new ArrayList<>(17);
        row.add(id);
        row.add("\u00ab" + stopName + "\u00bb, " + street + " (" + direction + ") (" + id + ")");
        row.add(lon);
        row.add(lat);
        row.add(area.admArea());
        row.add(area.name());
        row.add(routes());
        row.add(stopName);
        row.add(direction);
        row.add(pavilion ? "\u0434\u0430" : "\u043d\u0435\u0442");
        row.add(pavilion ? operator() : "");
        row.add("\u0430\u043a\u0442\u0438\u0432\u043d\u0430");
        row.add(String.valueOf(ID_BASE + index));
        row.add(values.chance(0.065) ? "" : street + ", \u0434." + values.between(1, 150));
        row.add("nested data");
        row.add(geo);
        row.add(geo);
        return row;
    }

    private String routes() {
        int count = values.between(1, 4);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                result.append("; ");
            }
            result.append(values.pick(ROUTE_PREFIXES)).append(values.between(1, 999));
        }
        return result.toString();
    }

    private String operator() {
        return values.chance(0.95)
                ? "\u0413\u0423\u041f \u00ab\u041c\u043e\u0441\u0433\u043e\u0440\u0442\u0440\u0430\u043d\u0441\u00bb"
                : "\u0413\u0423\u041f \u00ab\u041c\u043e\u0441\u043a\u043e\u0432\u0441\u043a\u0438\u0439 \u041c\u0435\u0442\u0440\u043e\u043f\u043e\u043b\u0438\u0442\u0435\u043d\u00bb";
    }
}
