package ru.moscow.hotspot_tracker.main.report;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;

/**
 * Параметры отчёта. В базе хранятся строкой запроса вида accessPointId=1&okrug=…,
 * чтобы не тянуть сериализатор JSON в модуль отчётов. Имена и значения кодируются,
 * поэтому символы &, =, +, % и # из названия округа не меняют разбор строки.
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
        StringJoiner query = new StringJoiner("&");
        if (accessPointId != null) {
            query.add(pair("accessPointId", accessPointId.toString()));
        }
        if (StringUtils.hasText(okrug)) {
            query.add(pair("okrug", okrug));
        }
        if (StringUtils.hasText(district)) {
            query.add(pair("district", district));
        }
        if (days != null) {
            query.add(pair("days", days.toString()));
        }
        // Без параметров (например, сводный отчёт по Москве) строка пустая: в базе это допустимо
        return query.toString();
    }

    static ReportParams fromQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (StringUtils.hasText(query)) {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                String key = decode(equals < 0 ? pair : pair.substring(0, equals));
                String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
                params.put(key, value);
            }
        }
        return new ReportParams(
                parseLong(params.get("accessPointId")),
                params.get("okrug"),
                params.get("district"),
                parseInt(params.get("days")));
    }

    private static String pair(String key, String value) {
        return encode(key) + "=" + encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private static Integer parseInt(String value) {
        return value == null ? null : Integer.valueOf(value);
    }
}
