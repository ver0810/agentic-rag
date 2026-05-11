package com.agenticrag.knowledge.service;

import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DocumentChunkingService {

    private static final int DEFAULT_FIXED_CHUNK_SIZE = 500;
    private static final int DEFAULT_FIXED_OVERLAP = 50;
    private static final int DEFAULT_PARAGRAPH_MAX_CHARS = 900;
    private static final int DEFAULT_PARAGRAPH_OVERLAP = 1;
    private static final int DEFAULT_PARAGRAPH_MIN_CHARS = 180;
    private static final int DEFAULT_RECURSIVE_MAX_CHARS = 1000;
    private static final int DEFAULT_RECURSIVE_OVERLAP = 100;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百千0-9]+[章节部分篇]\\s*.*|[一二三四五六七八九十]+[、.．].+|\\d+[、.．].+)$");

    private static final List<String> RECURSIVE_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", ".", "!", "?", "；", ";", "，", ","
    );

    private final ObjectMapper objectMapper;
    private final TokenCostEstimator tokenCostEstimator;

    public DocumentChunkingService(ObjectMapper objectMapper, TokenCostEstimator tokenCostEstimator) {
        this.objectMapper = objectMapper;
        this.tokenCostEstimator = tokenCostEstimator;
    }

    public List<String> chunk(String text, String strategy, String chunkConfig) {
        return chunkWithMetadata(text, strategy, chunkConfig).stream()
                .map(ChunkResult::content)
                .toList();
    }

    public List<ChunkResult> chunkWithMetadata(String text, String strategy, String chunkConfig) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalizedStrategy = StringUtils.hasText(strategy) ? strategy.trim().toLowerCase() : "paragraph";
        Map<String, Object> config = parseConfig(chunkConfig);
        int maxTokens = intValue(config, "maxTokens", DEFAULT_MAX_TOKENS);
        return switch (normalizedStrategy) {
            case "fixed" -> fixedChunksWithMeta(text,
                    intValue(config, "chunkSize", DEFAULT_FIXED_CHUNK_SIZE),
                    intValue(config, "overlap", DEFAULT_FIXED_OVERLAP),
                    maxTokens);
            case "paragraph", "smart" -> paragraphChunksWithMeta(text,
                    intValue(config, "maxChars", DEFAULT_PARAGRAPH_MAX_CHARS),
                    intValue(config, "overlapParagraphs", DEFAULT_PARAGRAPH_OVERLAP),
                    intValue(config, "minChunkChars", DEFAULT_PARAGRAPH_MIN_CHARS),
                    maxTokens);
            case "recursive" -> recursiveChunksWithMeta(text,
                    intValue(config, "maxChars", DEFAULT_RECURSIVE_MAX_CHARS),
                    intValue(config, "overlap", DEFAULT_RECURSIVE_OVERLAP),
                    maxTokens);
            default -> paragraphChunksWithMeta(text, DEFAULT_PARAGRAPH_MAX_CHARS, DEFAULT_PARAGRAPH_OVERLAP, DEFAULT_PARAGRAPH_MIN_CHARS, maxTokens);
        };
    }

    private List<String> fixedChunks(String text, int chunkSize, int overlap, int maxTokens) {
        return fixedChunksWithMeta(text, chunkSize, overlap, maxTokens).stream()
                .map(ChunkResult::content)
                .toList();
    }

    private List<ChunkResult> fixedChunksWithMeta(String text, int chunkSize, int overlap, int maxTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        int safeChunkSize = Math.max(100, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize - 1));
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + safeChunkSize, text.length());
            String chunk = text.substring(start, end);
            chunk = truncateToTokenLimit(chunk, maxTokens);
            chunks.add(new ChunkResult(chunk, null));
            start += safeChunkSize - safeOverlap;
        }
        return chunks;
    }

    private List<String> paragraphChunks(String text, int maxChars, int overlapParagraphs, int minChunkChars, int maxTokens) {
        return paragraphChunksWithMeta(text, maxChars, overlapParagraphs, minChunkChars, maxTokens).stream()
                .map(ChunkResult::content)
                .toList();
    }

    private List<ChunkResult> paragraphChunksWithMeta(String text, int maxChars, int overlapParagraphs, int minChunkChars, int maxTokens) {
        List<ParagraphWithHeading> paragraphs = normalizeParagraphsWithHeadings(text);
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<ChunkResult> chunks = new ArrayList<>();
        int safeMaxChars = Math.max(200, maxChars);
        int safeOverlap = Math.max(0, overlapParagraphs);
        int index = 0;
        while (index < paragraphs.size()) {
            StringBuilder current = new StringBuilder();
            String headingPath = null;
            int endExclusive = index;
            while (endExclusive < paragraphs.size()) {
                ParagraphWithHeading p = paragraphs.get(endExclusive);
                int nextLength = current.isEmpty() ? p.text.length() : current.length() + 2 + p.text.length();
                if (nextLength > safeMaxChars && current.length() >= minChunkChars) {
                    break;
                }
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                if (headingPath == null && p.heading != null) {
                    headingPath = p.heading;
                }
                current.append(p.text);
                endExclusive++;
                if (current.length() >= safeMaxChars) {
                    break;
                }
            }
            if (!current.isEmpty()) {
                chunks.add(new ChunkResult(truncateToTokenLimit(current.toString(), maxTokens), headingPath));
            }
            if (endExclusive >= paragraphs.size()) {
                break;
            }
            index = Math.max(index + 1, endExclusive - safeOverlap);
        }
        return chunks;
    }

    private List<String> recursiveChunks(String text, int maxChars, int overlap, int maxTokens) {
        return recursiveChunksWithMeta(text, maxChars, overlap, maxTokens).stream()
                .map(ChunkResult::content)
                .toList();
    }

    private List<ChunkResult> recursiveChunksWithMeta(String text, int maxChars, int overlap, int maxTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        recursiveSplitWithMeta(text, maxChars, overlap, maxTokens, 0, chunks);
        return chunks;
    }

    private void recursiveSplitWithMeta(String text, int maxChars, int overlap, int maxTokens, int separatorIndex, List<ChunkResult> result) {
        if (text.length() <= maxChars && estimateTokens(text) <= maxTokens) {
            result.add(new ChunkResult(text, null));
            return;
        }
        if (separatorIndex >= RECURSIVE_SEPARATORS.size()) {
            result.add(new ChunkResult(truncateToTokenLimit(text, maxTokens), null));
            return;
        }

        String separator = RECURSIVE_SEPARATORS.get(separatorIndex);
        String[] parts = text.split(Pattern.quote(separator), -1);

        if (parts.length <= 1) {
            recursiveSplitWithMeta(text, maxChars, overlap, maxTokens, separatorIndex + 1, result);
            return;
        }

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String candidate = current.isEmpty() ? part : current + separator + part;

            if (candidate.length() > maxChars && !current.isEmpty()) {
                String chunk = current.toString();
                if (chunk.length() <= maxChars && estimateTokens(chunk) <= maxTokens) {
                    result.add(new ChunkResult(chunk, null));
                } else {
                    recursiveSplitWithMeta(chunk, maxChars, overlap, maxTokens, separatorIndex + 1, result);
                }
                current = new StringBuilder(part);
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (!current.isEmpty()) {
            String chunk = current.toString();
            if (chunk.length() <= maxChars && estimateTokens(chunk) <= maxTokens) {
                result.add(new ChunkResult(chunk, null));
            } else {
                recursiveSplitWithMeta(chunk, maxChars, overlap, maxTokens, separatorIndex + 1, result);
            }
        }
    }

    private String truncateToTokenLimit(String text, int maxTokens) {
        if (estimateTokens(text) <= maxTokens) {
            return text;
        }
        int approxCharLimit = maxTokens * 4;
        if (text.length() <= approxCharLimit) {
            return text;
        }
        return text.substring(0, approxCharLimit);
    }

    private int estimateTokens(String text) {
        return tokenCostEstimator.estimateTokens(text);
    }

    private List<String> normalizeParagraphs(String text) {
        return normalizeParagraphsWithHeadings(text).stream()
                .map(p -> p.heading != null ? p.heading + "\n" + p.text : p.text)
                .toList();
    }

    private List<ParagraphWithHeading> normalizeParagraphsWithHeadings(String text) {
        String[] blocks = text.replace("\r\n", "\n").split("\\n\\s*\\n+");
        List<ParagraphWithHeading> paragraphs = new ArrayList<>();
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
            paragraphs.add(new ParagraphWithHeading(normalized, currentHeading));
        }
        if (paragraphs.isEmpty() && StringUtils.hasText(text)) {
            paragraphs.add(new ParagraphWithHeading(text.trim(), null));
        }
        return paragraphs;
    }

    private record ParagraphWithHeading(String text, String heading) {}

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
