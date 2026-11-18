package ru.moscow.hotspot_tracker.main.accesspoint;

/** Тип дня для профиля нагрузки (FR-LOAD-01). */
public enum DayType {
    WEEKDAY("Будни"),
    WEEKEND("Выходные");

    private final String label;

    DayType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
