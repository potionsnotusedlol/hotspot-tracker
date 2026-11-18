package ru.moscow.hotspot_tracker.main.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Значения, которые нужны всем страницам: путь для подсветки меню и флаг заглушек
 * (если данные демонстрационные, layout покажет предупреждение).
 */
@ControllerAdvice
class SharedModelAdvice {

    private final boolean placeholders;

    SharedModelAdvice(@Value("${hotspot.web.placeholders:true}") boolean placeholders) {
        this.placeholders = placeholders;
    }

    @ModelAttribute("placeholders")
    boolean placeholders() {
        return placeholders;
    }

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
