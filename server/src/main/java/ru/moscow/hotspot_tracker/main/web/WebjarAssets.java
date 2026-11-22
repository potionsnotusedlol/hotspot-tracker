package ru.moscow.hotspot_tracker.main.web;

import org.springframework.stereotype.Component;
import org.webjars.WebJarAssetLocator;

/**
 * Путь к файлу из webjar с версией, которую знает classpath. В шаблонах версий нет:
 * их меняет только build.gradle.kts. В шаблоне: ${assets.path('htmx.org', 'dist/htmx.min.js')}.
 */
@Component
public class WebjarAssets {

    private static final String WEBJARS_PREFIX = "META-INF/resources/webjars/";

    private final WebJarAssetLocator locator = new WebJarAssetLocator();

    public String path(String library, String file) {
        // Locator возвращает путь внутри classpath, а URL в /webjars/ идёт без META-INF/resources/webjars/
        String inClasspath = locator.getFullPath(library, file);
        return "/webjars/" + inClasspath.substring(WEBJARS_PREFIX.length());
    }
}
