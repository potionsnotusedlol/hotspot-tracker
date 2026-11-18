package ru.moscow.hotspot_tracker.main.report;

/** Виды отчётов (FR-REP-02…05). USAGE доступен только администратору. */
public enum ReportType {
    ACCESS_POINT("Карточка точки доступа", false),
    DISTRICT("Точки округа или района", false),
    MOSCOW("Сводный отчёт по Москве", false),
    USAGE("Использование системы", true);

    private final String label;
    private final boolean adminOnly;

    ReportType(String label, boolean adminOnly) {
        this.label = label;
        this.adminOnly = adminOnly;
    }

    public String label() {
        return label;
    }

    public boolean adminOnly() {
        return adminOnly;
    }
}
