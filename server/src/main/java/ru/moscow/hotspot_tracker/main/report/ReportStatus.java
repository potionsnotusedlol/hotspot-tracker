package ru.moscow.hotspot_tracker.main.report;

/** Жизненный цикл задачи: PENDING → RUNNING → DONE или FAILED (FR-REP-06). */
public enum ReportStatus {
    PENDING("В очереди"),
    RUNNING("Готовится"),
    DONE("Готов"),
    FAILED("Ошибка");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
