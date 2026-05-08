package com.agenticrag.config;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Load optional .env from the project root for local development.
 */
public class DotenvEnvironmentPostProcessor  implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";
    private static final Path DOTENV_PATH = Path.of(".env");


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Files.isRegularFile(DOTENV_PATH)) {
            return;
        }

        Map<String, Object> properties = loadDotenv(DOTENV_PATH);
        if (properties.isEmpty()) {
            return;
        }

        MutablePropertySources propertySources = environment.getPropertySources();
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);

        if (propertySource.containsProperty(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }
        propertySources.addFirst(propertySource);
    }

    private Map<String, Object> loadDotenv(Path dotenvPath) {
        try{
            List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
            Map<String, Object> values = new LinkedHashMap<>();
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) continue;
                String key = line.substring(0, separatorIndex).trim();
                if (key.isEmpty()) continue;
                String value = line.substring(separatorIndex + 1).trim();
                values.put(key, stripWrappingQuotes(value));
            }
            return values;
        } catch (IOException ignored) {
            return Map.of();
        }
    }
    private String stripWrappingQuotes(String value) {
        if (value.length() < 2) return value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


}
