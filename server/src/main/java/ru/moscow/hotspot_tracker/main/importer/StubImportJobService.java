package ru.moscow.hotspot_tracker.main.importer;

import java.util.List;
import org.springframework.stereotype.Service;

/** ВРЕМЕННАЯ ЗАГЛУШКА: импорт ещё не реализован, журнал пуст. Удалить с появлением модуля данных (№2). */
@Service
class StubImportJobService implements ImportJobService {

    @Override
    public List<ImportJob> recent(int limit) {
        return List.of();
    }
}
