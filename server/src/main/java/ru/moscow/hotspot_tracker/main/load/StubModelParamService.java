package ru.moscow.hotspot_tracker.main.load;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * ВРЕМЕННАЯ ЗАГЛУШКА. Хранит параметры в памяти процесса, после перезапуска они сбрасываются.
 * Удалить, когда модуль данных (№1) сохранит параметры в таблицу model_params.
 */
@Service
class StubModelParamService implements ModelParamService {

    private final Map<String, ModelParam> params = new ConcurrentHashMap<>();

    StubModelParamService() {
        params.put("building.radius.m", new ModelParam("building.radius.m", "300", 1, "system", Instant.now()));
        params.put("stop.radius.m", new ModelParam("stop.radius.m", "300", 1, "system", Instant.now()));
        params.put("weekend.factor", new ModelParam("weekend.factor", "0.8", 1, "system", Instant.now()));
    }

    @Override
    public List<ModelParam> findAll() {
        return params.values().stream().sorted(java.util.Comparator.comparing(ModelParam::key)).toList();
    }

    @Override
    public synchronized void update(String key, String value, String actor) {
        ModelParam current = params.get(key);
        if (current == null) {
            throw new NoSuchElementException("Параметр не найден: " + key);
        }
        params.put(key, new ModelParam(key, value, current.version() + 1, actor, Instant.now()));
    }
}
