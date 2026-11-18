package ru.moscow.hotspot_tracker.main.accesspoint;

/** Вид ближайшего транспорта (FR-STAT-03). */
public enum TransportType {
    BUS("Автобус"),
    TRAM("Трамвай"),
    ELECTRIC_BUS("Электробус"),
    MCC("МЦК"),
    MCD("МЦД"),
    METRO("Метро");

    private final String label;

    TransportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
