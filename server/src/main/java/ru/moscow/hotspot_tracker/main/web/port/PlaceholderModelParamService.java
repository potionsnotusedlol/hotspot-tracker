package ru.moscow.hotspot_tracker.main.web.port;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** ЗАГЛУШКА веба: параметров пока нет, изменение честно отклоняем. Отключается с модулем нагрузки. */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderModelParamService implements ModelParamService {

    @Override
    public List<ModelParam> findAll() {
        return List.of();
    }

    @Override
    public void update(String key, String value, String actor) {
        throw new IllegalArgumentException("Параметры модели пока не подключены");
    }
}
