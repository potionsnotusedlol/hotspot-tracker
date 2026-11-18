package ru.moscow.hotspot_tracker.main.load;

import java.time.Instant;
import java.util.List;

/** Параметры модели нагрузки, которые администратор меняет без пересборки (FR-LOAD-04, FR-ADM-02). */
public interface ModelParamService {

    List<ModelParam> findAll();

    /** Меняет значение и запоминает, кто и когда это сделал. */
    void update(String key, String value, String actor);

    record ModelParam(String key, String value, int version, String changedBy, Instant changedAt) {}
}
