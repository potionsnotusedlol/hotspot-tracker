package ru.moscow.hotspot_tracker.main.report;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Параметры отчёта. В базе хранятся строкой запроса вида accessPointId=1&okrug=…,
 * чтобы не тянуть сериализатор JSON в модуль отчётов.
 */
public record ReportParams(Long accessPointId, String okrug, String district, Integer days) {

    /** Проверка до постановки в очередь, чтобы пользователь сразу видел ошибку. */
    public void validate(ReportType type) {
        switch (type) {
            case ACCESS_POINT -> {
                if (accessPointId == null) {
                    throw new IllegalArgumentException("Укажите идентификатор точки");
                }
            }
            case DISTRICT -> {
                if (!StringUtils.hasText(okrug)) {
                    throw new IllegalArgumentException("Выберите округ");
                }
            }
            case USAGE -> {
                if (days != null && (days < 1 || days > 365)) {
                    throw new IllegalArgumentException("Период задаётся от 1 до 365 дней");
                }
            }
            case MOSCOW -> {
                // Параметров нет
            }
        }
    }

    String toQuery() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (accessPointId != null) {
            params.add("accessPointId", accessPointId.toString());
        }
        if (StringUtils.hasText(okrug)) {
            params.add("okrug", okrug);
        }
        if (StringUtils.hasText(district)) {
            params.add("district", district);
        }
        if (days != null) {
            params.add("days", days.toString());
        }
        String query = UriComponentsBuilder.newInstance().queryParams(params).build().getQuery();
        // Без параметров (например, сводный отчёт по Москве) getQuery() возвращает null, а в базе нужна строка
        return query == null ? "" : query;
    }

    static ReportParams fromQuery(String query) {
        MultiValueMap<String, String> params = UriComponentsBuilder.newInstance()
                .query(query == null ? "" : query).build().getQueryParams();
        return new ReportParams(
                parseLong(params.getFirst("accessPointId")),
                params.getFirst("okrug"),
                params.getFirst("district"),
                parseInt(params.getFirst("days")));
    }

    private static Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private static Integer parseInt(String value) {
        return value == null ? null : Integer.valueOf(value);
    }
}
