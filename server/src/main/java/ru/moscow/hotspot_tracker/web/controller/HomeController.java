package ru.moscow.hotspot_tracker.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/catalog")
    public String catalog() {
        return "catalog";
    }
}
