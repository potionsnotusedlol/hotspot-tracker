package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exits of metro and MCC stations, next to the real stations. */
final class MetroRows implements RowSource {
    private static final long ID_BASE = 5_000_000_000L;
    private static final List<String> SIDES = List.of(
            "\u0441\u0435\u0432\u0435\u0440\u043d\u044b\u0439",
            "\u044e\u0436\u043d\u044b\u0439",
            "\u0432\u043e\u0441\u0442\u043e\u0447\u043d\u044b\u0439",
            "\u0437\u0430\u043f\u0430\u0434\u043d\u044b\u0439");

    private final Values values;

    MetroRows(Values values) {
        this.values = values;
    }

    @Override
    public List<String> next(long index) {
        Dictionaries.MetroStation station = values.pick(Dictionaries.METRO_STATIONS);
        double[] point = values.near(station.lat(), station.lon(), 0.0012);
        String lat = Values.fixed(point[0], 8);
        String lon = Values.fixed(point[1], 8);
        String geo = Values.geo(lat, lon);
        String vestibule = values.chance(0.6)
                ? " \u0432 " + values.pick(SIDES) + " \u0432\u0435\u0441\u0442\u0438\u0431\u044e\u043b\u044c"
                : "";
        String full = values.chance(0.28) ? "" : String.valueOf(values.between(1, 6));
        String little = values.chance(0.25) ? "" : String.valueOf(values.between(1, 8));
        List<String> row = new ArrayList<>(21);
        row.add(String.valueOf(index + 1));
        row.add(station.name() + ", \u0432\u0445\u043e\u0434-\u0432\u044b\u0445\u043e\u0434 " + values.between(1, 8)
                + vestibule);
        row.add(station.inMoscow() ? "\u0434\u0430" : "\u043d\u0435\u0442");
        row.add(station.inMoscow() ? station.admArea() : "");
        row.add(station.inMoscow() ? station.district() : "");
        row.add(lon);
        row.add(lat);
        row.add(vestibuleType());
        row.add(station.name());
        row.add(station.line());
        row.add(heritage());
        row.add(schedule());
        row.add(schedule());
        row.add(full);
        row.add(little);
        row.add(String.valueOf(count(full) + count(little) + (full.isEmpty() && little.isEmpty() ? 4 : 0)));
        row.add("nested data");
        row.add(
                values.chance(0.996)
                        ? "\u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442"
                        : "\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u0437\u0430\u043a\u0440\u044b\u0442");
        row.add(String.valueOf(ID_BASE + index));
        row.add(geo);
        row.add(geo);
        return row;
    }

    private String vestibuleType() {
        double roll = values.uniform();
        if (roll < 0.76) {
            return "\u043f\u043e\u0434\u0437\u0435\u043c\u043d\u044b\u0439";
        }
        return roll < 0.97
                ? "\u043d\u0430\u0437\u0435\u043c\u043d\u044b\u0439 \u043e\u0442\u0434\u0435\u043b\u044c\u043d\u043e\u0441\u0442\u043e\u044f\u0449\u0438\u0439"
                : "\u043d\u0430\u0437\u0435\u043c\u043d\u044b\u0439, \u0432\u0441\u0442\u0440\u043e\u0435\u043d\u043d\u044b\u0439 \u0432 \u0437\u0434\u0430\u043d\u0438\u0435";
    }

    private String heritage() {
        double roll = values.uniform();
        if (roll < 0.9) {
            return "\u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u043e\u0431\u044a\u0435\u043a\u0442\u043e\u043c \u043a\u0443\u043b\u044c\u0442\u0443\u0440\u043d\u043e\u0433\u043e \u043d\u0430\u0441\u043b\u0435\u0434\u0438\u044f";
        }
        return roll < 0.96
                ? "\u0432\u044b\u044f\u0432\u043b\u0435\u043d\u043d\u044b\u0439 \u043e\u0431\u044a\u0435\u043a\u0442 \u043a\u0443\u043b\u044c\u0442\u0443\u0440\u043d\u043e\u0433\u043e \u043d\u0430\u0441\u043b\u0435\u0434\u0438\u044f"
                : "\u043e\u0431\u044a\u0435\u043a\u0442 \u043a\u0443\u043b\u044c\u0442\u0443\u0440\u043d\u043e\u0433\u043e \u043d\u0430\u0441\u043b\u0435\u0434\u0438\u044f \u0440\u0435\u0433\u0438\u043e\u043d\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f";
    }

    private String schedule() {
        return String.format(
                Locale.ROOT,
                "\u043e\u0442\u043a\u0440\u044b\u0442\u0438\u0435 \u0432 05:30:00; \u0437\u0430\u043a\u0440\u044b\u0442\u0438\u0435 \u0432 01:00:00; \u043f\u0435\u0440\u0432\u044b\u0439 \u043f\u043e\u0435\u0437\u0434 \u043f\u043e 1 \u043f\u0443\u0442\u0438 \u0432 05:%02d:00, \u043f\u043e 2 \u043f\u0443\u0442\u0438 \u0432 05:%02d:00;"
                        + " \u043f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u043f\u043e\u0435\u0437\u0434 \u043f\u043e 1 \u043f\u0443\u0442\u0438 \u0432 01:%02d:00, \u043f\u043e 2 \u043f\u0443\u0442\u0438 \u0432 01:%02d:00",
                values.between(35, 55),
                values.between(35, 55),
                values.between(10, 40),
                values.between(10, 40));
    }

    private static int count(String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }
}
