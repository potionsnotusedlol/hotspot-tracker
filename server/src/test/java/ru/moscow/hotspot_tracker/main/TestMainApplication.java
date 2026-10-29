package ru.moscow.hotspot_tracker.main;

import org.springframework.boot.SpringApplication;

public class TestMainApplication { public static void main(String[] args) { SpringApplication.from(MainApplication::main).with(TestcontainersConfiguration.class).run(args); } }
