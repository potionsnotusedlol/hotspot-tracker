package ru.moscow.hotspot_tracker.testdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Random values and formatting shared by the datasets. One instance per file, so a seed gives the same file. */
final class Values {
    private static final int[] DISTRICT_WEIGHTS = cumulative(Dictionaries.DISTRICTS.stream()
            .mapToInt(Dictionaries.District::weight)
            .toArray());

    private final SplittableRandom random;

    Values(SplittableRandom random) {
        this.random = random;
    }

    /** A number from {@code from} to {@code to}, both included. */
    int between(int from, int to) {
        return random.nextInt(from, to + 1);
    }

    boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    /** A number from 0 (included) to 1 (excluded). */
    double uniform() {
        return random.nextDouble();
    }

    double gaussian() {
        return random.nextGaussian();
    }

    <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /** A district of Moscow, as often as it has stops: this keeps the spatial density of the real city. */
    Dictionaries.District district() {
        int point = random.nextInt(DISTRICT_WEIGHTS[DISTRICT_WEIGHTS.length - 1]);
        int index = Arrays.binarySearch(DISTRICT_WEIGHTS, point + 1);
        return Dictionaries.DISTRICTS.get(index >= 0 ? index : -index - 1);
    }

    /**
     * A point around a centre with a normal distribution, cut at three spreads.
     *
     * @param spread standard deviation in degrees of latitude; longitude is scaled to the same distance
     * @return latitude and longitude
     */
    double[] near(double lat, double lon, double spread) {
        double lonSpread = spread / Math.cos(Math.toRadians(lat));
        return new double[] {lat + clamp(gaussian()) * spread, lon + clamp(gaussian()) * lonSpread};
    }

    /** A coordinate with a fixed number of decimals, as in the coordinate columns of data.mos.ru. */
    static String fixed(double value, int decimals) {
        return BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** The geoData value for coordinate columns: longitude first, trailing zeros dropped. */
    static String geo(String lat, String lon) {
        return "{coordinates=[" + plain(lon) + ", " + plain(lat) + "], type=Point}";
    }

    /** A number with spaces between thousands, as in houses.csv: {@code 2 655.30}. */
    static String grouped(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    static String grouped(double value) {
        return String.format(java.util.Locale.ROOT, "%,.2f", value).replace(',', ' ');
    }

    private static String plain(String number) {
        return new BigDecimal(number).stripTrailingZeros().toPlainString();
    }

    private static double clamp(double value) {
        return Math.max(-3, Math.min(3, value));
    }

    private static int[] cumulative(int[] weights) {
        int[] result = new int[weights.length];
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i];
            result[i] = sum;
        }
        return result;
    }
}
