package ru.moscow.hotspot_tracker.main.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Во все страницы передаём флаг: если данные заглушечные, layout покажет предупреждение. */
@ControllerAdvice
class PlaceholderBannerAdvice {

    private final boolean placeholders;

    PlaceholderBannerAdvice(@Value("${hotspot.web.placeholders:true}") boolean placeholders) {
        this.placeholders = placeholders;
    }

    @ModelAttribute("placeholders")
    boolean placeholders() {
        return placeholders;
    }
}
