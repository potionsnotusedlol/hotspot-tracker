package ru.moscow.hotspot_tracker.main.web.port;

/** Относительная шкала расчётной нагрузки по квантилям (FR-LOAD-03). */
public enum LoadLevel {
    LOW("Низкая"),
    MEDIUM("Средняя"),
    HIGH("Высокая");

    private final String label;

    LoadLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
