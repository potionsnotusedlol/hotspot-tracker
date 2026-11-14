package ru.moscow.hotspot_tracker.testdata;

import java.util.ArrayList;
import java.util.List;

/**
 * Houses of Russia in the format of houses.csv: a share of them in Moscow, the rest in other regions,
 * so the import has to filter by region. Dashes, empty values and thousands with spaces appear
 * as often as in the real file.
 */
final class HouseRows implements RowSource {
    /** The region and locality of Moscow in houses.csv. */
    static final String MOSCOW = "\u041c\u043e\u0441\u043a\u0432\u0430";

    private static final List<String> COMPANIES = List.of(
            "\u0416\u0438\u043b\u0438\u0449\u043d\u0438\u043a",
            "\u0423\u043f\u0440\u0430\u0432\u0434\u043e\u043c",
            "\u041a\u043e\u043c\u0444\u043e\u0440\u0442",
            "\u0416\u041a\u0425 \u0421\u0435\u0440\u0432\u0438\u0441",
            "\u0414\u043e\u043c\u043e\u0441\u0442\u0440\u043e\u0439");

    private final Values values;
    private final double moscowShare;
    private final List<Dictionaries.Region> otherRegions;
    private final double otherTotal;

    HouseRows(Values values, double moscowShare) {
        this.values = values;
        this.moscowShare = moscowShare;
        this.otherRegions = Dictionaries.REGIONS.stream()
                .filter(region -> !region.name().equals(MOSCOW))
                .toList();
        this.otherTotal =
                otherRegions.stream().mapToDouble(Dictionaries.Region::share).sum();
    }

    @Override
    public List<String> next(long index) {
        boolean moscow = values.chance(moscowShare);
        Dictionaries.Region region = moscow ? null : otherRegion();
        double[] point;
        if (moscow) {
            Dictionaries.District area = values.district();
            point = values.near(area.lat(), area.lon(), area.spread());
        } else {
            point = values.near(region.lat(), region.lon(), region.spread());
        }
        String regionName = moscow ? MOSCOW : region.name();
        String locality = moscow ? MOSCOW : region.locality();
        String year = year();
        String address = values.pick(Dictionaries.STREETS) + ", \u0434. " + values.between(1, 150)
                + (values.chance(0.3) ? ", \u043a. " + values.between(1, 9) : "");
        List<String> row = new ArrayList<>(12);
        row.add(String.valueOf(index + 1));
        row.add(coordinate(point[0]));
        row.add(coordinate(point[1]));
        row.add(year);
        row.add(values.chance(0.067) ? "\u2014" : Values.grouped(values.between(2_000, 3_000_000) / 100.0));
        row.add(population());
        row.add(regionName);
        row.add(locality);
        row.add(address);
        row.add((moscow ? MOSCOW : regionName + ", " + locality) + ", " + address);
        row.add(values.between(1, 35_000) + ".0");
        row.add("\u0416\u0438\u043b\u043e\u0439 \u0434\u043e\u043c \u0432 "
                + (moscow ? "\u041c\u043e\u0441\u043a\u0432\u0435" : "")
                + ", \u043f\u043e \u0430\u0434\u0440\u0435\u0441\u0443 " + address
                + (year.length() == 4
                        ? ", " + year
                                + " \u0433\u043e\u0434\u0430 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438"
                        : "")
                + ", \u043f\u043e\u0434 \u0443\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435\u043c \u0423\u041a \u00ab"
                + values.pick(COMPANIES) + "\u00bb.");
        return row;
    }

    private Dictionaries.Region otherRegion() {
        double point = values.uniform() * otherTotal;
        for (Dictionaries.Region region : otherRegions) {
            point -= region.share();
            if (point < 0) {
                return region;
            }
        }
        return otherRegions.get(otherRegions.size() - 1);
    }

    // The real file keeps floating point noise: 55.76534100000001
    private String coordinate(double value) {
        double rounded = Math.round(value * 1e6) / 1e6;
        return Double.toString(values.chance(0.35) ? Math.nextUp(rounded) : rounded);
    }

    private String year() {
        double roll = values.uniform();
        if (roll < 0.87) {
            return String.valueOf(values.between(1890, 2024));
        }
        if (roll < 0.98) {
            return "";
        }
        if (roll < 0.99) {
            return "1";
        }
        return roll < 0.995 ? "\u2014" : "\u0434\u043e 1945";
    }

    private String population() {
        double roll = values.uniform();
        if (roll < 0.77) {
            return Values.grouped(values.between(1, 1_500));
        }
        return roll < 0.98 ? "\u2014" : "";
    }
}
