package com.agenticrag.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DocumentChunkingService {

    private static final int DEFAULT_FIXED_CHUNK_SIZE = 500;
    private static final int DEFAULT_FIXED_OVERLAP = 50;
    private static final int DEFAULT_PARAGRAPH_MAX_CHARS = 900;
    private static final int DEFAULT_PARAGRAPH_OVERLAP = 1;
    private static final int DEFAULT_PARAGRAPH_MIN_CHARS = 180;
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百千0-9]+[章节部分篇]\\s*.*|[一二三四五六七八九十]+[、.．].+|\\d+[、.．].+)$");

    private final ObjectMapper objectMapper;

    public DocumentChunkingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> chunk(String text, String strategy, String chunkConfig) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalizedStrategy = StringUtils.hasText(strategy) ? strategy.trim().toLowerCase() : "paragraph";
        Map<String, Object> config = parseConfig(chunkConfig);
        return switch (normalizedStrategy) {
            case "fixed" -> fixedChunks(text, intValue(config, "chunkSize", DEFAULT_FIXED_CHUNK_SIZE),
                    intValue(config, "overlap", DEFAULT_FIXED_OVERLAP));
            case "paragraph", "smart" -> paragraphChunks(text,
                    intValue(config, "maxChars", DEFAULT_PARAGRAPH_MAX_CHARS),
                    intValue(config, "overlapParagraphs", DEFAULT_PARAGRAPH_OVERLAP),
                    intValue(config, "minChunkChars", DEFAULT_PARAGRAPH_MIN_CHARS));
            default -> paragraphChunks(text, DEFAULT_PARAGRAPH_MAX_CHARS, DEFAULT_PARAGRAPH_OVERLAP, DEFAULT_PARAGRAPH_MIN_CHARS);
        };
    }

    private List<String> fixedChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int safeChunkSize = Math.max(100, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize - 1));
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + safeChunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += safeChunkSize - safeOverlap;
        }
        return chunks;
    }

    private List<String> paragraphChunks(String text, int maxChars, int overlapParagraphs, int minChunkChars) {
        List<String> paragraphs = normalizeParagraphs(text);
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int safeMaxChars = Math.max(200, maxChars);
        int safeOverlap = Math.max(0, overlapParagraphs);
        int index = 0;
        while (index < paragraphs.size()) {
            StringBuilder current = new StringBuilder();
            int endExclusive = index;
            while (endExclusive < paragraphs.size()) {
                String paragraph = paragraphs.get(endExclusive);
                int nextLength = current.isEmpty() ? paragraph.length() : current.length() + 2 + paragraph.length();
                if (nextLength > safeMaxChars && current.length() >= minChunkChars) {
                    break;
                }
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(paragraph);
                endExclusive++;
                if (current.length() >= safeMaxChars) {
                    break;
                }
            }
            if (!current.isEmpty()) {
                chunks.add(current.toString());
            }
            if (endExclusive >= paragraphs.size()) {
                break;
            }
            index = Math.max(index + 1, endExclusive - safeOverlap);
        }
        return chunks;
    }

    private List<String> normalizeParagraphs(String text) {
        String[] blocks = text.replace("\r\n", "\n").split("\\n\\s*\\n+");
        List<String> paragraphs = new ArrayList<>();
        String currentHeading = null;
        for (String block : blocks) {
            String normalized = block.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (isHeading(normalized)) {
                currentHeading = normalized;
                continue;
            }
            if (currentHeading != null) {
                paragraphs.add(currentHeading + "\n" + normalized);
            } else {
                paragraphs.add(normalized);
            }
        }
        if (paragraphs.isEmpty() && StringUtils.hasText(text)) {
            paragraphs.add(text.trim());
        }
        return paragraphs;
    }

    private boolean isHeading(String block) {
        return block.length() <= 80 && HEADING_PATTERN.matcher(block).matches();
    }

    private Map<String, Object> parseConfig(String chunkConfig) {
        if (!StringUtils.hasText(chunkConfig)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(chunkConfig, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private int intValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
