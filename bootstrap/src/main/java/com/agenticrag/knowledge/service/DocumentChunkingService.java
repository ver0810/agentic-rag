package com.agenticrag.knowledge.service;

import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.rag.parser.LogicalSegment;
import com.agenticrag.rag.parser.StructuredParseResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("(?m)^\\|.+\\|\\s*$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("(?m)^(-|\\*|\\d+[.])\\s+.+$");

    private static final List<String> RECURSIVE_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", ".", "!", "?", "；", ";", "，", ","
    );

    private final ObjectMapper objectMapper;
    private final TokenCostEstimator tokenCostEstimator;
    private final SemanticChunkingService semanticChunkingService;

    @Autowired
    public DocumentChunkingService(ObjectMapper objectMapper,
                                   TokenCostEstimator tokenCostEstimator,
                                   SemanticChunkingService semanticChunkingService) {
        this.objectMapper = objectMapper;
        this.tokenCostEstimator = tokenCostEstimator;
        this.semanticChunkingService = semanticChunkingService;
    }

    public DocumentChunkingService(ObjectMapper objectMapper,
                                   TokenCostEstimator tokenCostEstimator) {
        this(objectMapper, tokenCostEstimator, new SemanticChunkingService(unsupportedEmbeddingPort(), tokenCostEstimator));
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

        if ("semantic".equals(normalizedStrategy)) {
            return semanticChunkingService.chunk(text, config);
        }

        return switch (normalizedStrategy) {
            case "fixed" -> fixedChunksWithMeta(text,
                    intValue(config, "chunkSize", DEFAULT_FIXED_CHUNK_SIZE),
                    intValue(config, "overlap", DEFAULT_FIXED_OVERLAP),
                    maxTokens);
            case "parent-child" -> parentChildChunksWithMeta(text, config, maxTokens);
            case "paper", "manual", "table" -> paragraphChunksWithMeta(text,
                    intValue(config, "maxChars", DEFAULT_PARAGRAPH_MAX_CHARS),
                    intValue(config, "overlapParagraphs", DEFAULT_PARAGRAPH_OVERLAP),
                    intValue(config, "minChunkChars", DEFAULT_PARAGRAPH_MIN_CHARS),
                    maxTokens).stream()
                    .map(chunk -> decorateChunkStrategy(chunk, normalizedStrategy))
                    .toList();
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

    public List<ChunkResult> chunkWithMetadata(StructuredParseResult parseResult, String strategy, String chunkConfig) {
        if (parseResult == null || parseResult.segments() == null || parseResult.segments().isEmpty()) {
            return List.of();
        }
        String normalizedStrategy = StringUtils.hasText(strategy) ? strategy.trim().toLowerCase() : "paragraph";
        Map<String, Object> config = parseConfig(chunkConfig);

        if ("semantic".equals(normalizedStrategy)) {
            return semanticChunkingService.chunk(parseResult, config);
        }

        if (List.of("paper", "manual", "table").contains(normalizedStrategy)) {
            return paragraphChunksWithStructuredSegments(
                    parseResult.segments(),
                    intValue(config, "maxChars", DEFAULT_PARAGRAPH_MAX_CHARS),
                    intValue(config, "overlapParagraphs", DEFAULT_PARAGRAPH_OVERLAP),
                    intValue(config, "minChunkChars", DEFAULT_PARAGRAPH_MIN_CHARS),
                    intValue(config, "maxTokens", DEFAULT_MAX_TOKENS),
                    normalizedStrategy);
        }
        if (!List.of("paragraph", "smart").contains(normalizedStrategy)) {
            return chunkWithMetadata(parseResult.asPlainText(), strategy, chunkConfig);
        }
        return paragraphChunksWithStructuredSegments(
                parseResult.segments(),
                intValue(config, "maxChars", DEFAULT_PARAGRAPH_MAX_CHARS),
                intValue(config, "overlapParagraphs", DEFAULT_PARAGRAPH_OVERLAP),
                intValue(config, "minChunkChars", DEFAULT_PARAGRAPH_MIN_CHARS),
                intValue(config, "maxTokens", DEFAULT_MAX_TOKENS),
                normalizedStrategy);
    }

    private List<ChunkResult> parentChildChunksWithMeta(String text, Map<String, Object> config, int maxTokens) {
        int parentSize = intValue(config, "parentSize", 1000);
        int childSize = intValue(config, "childSize", 300);
        int overlap = intValue(config, "overlap", 50);

        List<ChunkResult> allResults = new ArrayList<>();
        // First, split into big chunks (parents)
        List<ChunkResult> parents = fixedChunksWithMeta(text, parentSize, overlap, maxTokens);
        
        for (int i = 0; i < parents.size(); i++) {
            ChunkResult parent = parents.get(i);
            String parentId = "p-" + i + "-" + System.currentTimeMillis(); // Temporary ID until persisted
            
            // Add parent to results with a special marker
            Map<String, Object> parentMeta = new LinkedHashMap<>(parent.metadata());
            parentMeta.put("isParent", true);
            parentMeta.put("chunkId", parentId);
            allResults.add(new ChunkResult(parent.content(), parent.headingPath(), parentMeta));

            // Split parent into small chunks (children)
            List<ChunkResult> children = fixedChunksWithMeta(parent.content(), childSize, overlap, maxTokens);
            for (ChunkResult child : children) {
                Map<String, Object> childMeta = new LinkedHashMap<>(child.metadata());
                childMeta.put("parentId", parentId);
                childMeta.put("isChild", true);
                allResults.add(new ChunkResult(child.content(), child.headingPath(), childMeta));
            }
        }
        return allResults;
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
            chunks.add(buildChunkResult(chunk, null));
            start += safeChunkSize - safeOverlap;
        }
        return chunks;
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
                chunks.add(buildChunkResult(truncateToTokenLimit(current.toString(), maxTokens), headingPath));
            }
            if (endExclusive >= paragraphs.size()) {
                break;
            }
            index = Math.max(index + 1, endExclusive - safeOverlap);
        }
        return chunks;
    }

    private List<ChunkResult> paragraphChunksWithStructuredSegments(List<LogicalSegment> segments,
                                                                    int maxChars,
                                                                    int overlapParagraphs,
                                                                    int minChunkChars,
                                                                    int maxTokens,
                                                                    String strategy) {
        List<SegmentWithHeading> units = segments.stream()
                .filter(segment -> StringUtils.hasText(segment.content()))
                .filter(segment -> !"heading".equals(segment.type()))
                .map(segment -> new SegmentWithHeading(segment.type(), segment.content(), segment.headingPath(), segment.metadata()))
                .toList();
        if (units.isEmpty()) {
            return chunkWithMetadata(
                    segments.stream()
                            .map(LogicalSegment::content)
                            .filter(StringUtils::hasText)
                            .reduce((left, right) -> left + "\n\n" + right)
                            .orElse(""),
                    "paragraph",
                    null);
        }

        List<ChunkResult> chunks = new ArrayList<>();
        int safeMaxChars = Math.max(200, maxChars);
        int safeOverlap = Math.max(0, overlapParagraphs);
        int index = 0;
        while (index < units.size()) {
            SegmentWithHeading firstUnit = units.get(index);
            if (shouldIsolateSegment(firstUnit, strategy)) {
                ChunkResult isolated = buildChunkResult(
                        truncateToTokenLimit(firstUnit.text(), maxTokens),
                        firstUnit.headingPath());
                Map<String, Object> metadata = new LinkedHashMap<>(isolated.metadata());
                mergeSegmentMetadata(metadata, firstUnit.metadata());
                metadata.put("chunkStrategy", strategy);
                chunks.add(new ChunkResult(isolated.content(), isolated.headingPath(), metadata));
                index++;
                continue;
            }
            StringBuilder current = new StringBuilder();
            String headingPath = null;
            Integer headingLevel = null;
            Map<String, Object> mergedMetadata = new LinkedHashMap<>();
            int endExclusive = index;
            while (endExclusive < units.size()) {
                SegmentWithHeading unit = units.get(endExclusive);
                if (!current.isEmpty() && shouldIsolateSegment(unit, strategy)) {
                    break;
                }
                if (!current.isEmpty()
                        && StringUtils.hasText(headingPath)
                        && StringUtils.hasText(unit.headingPath)
                        && !headingPath.equals(unit.headingPath)
                        && current.length() >= minChunkChars) {
                    break;
                }
                if (shouldBreakForStrategy(strategy, headingPath, unit, current.length(), minChunkChars)) {
                    break;
                }
                int nextLength = current.isEmpty() ? unit.text.length() : current.length() + 2 + unit.text.length();
                if (nextLength > safeMaxChars && current.length() >= minChunkChars) {
                    break;
                }
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                if (headingPath == null && StringUtils.hasText(unit.headingPath)) {
                    headingPath = unit.headingPath;
                }
                if (headingLevel == null) {
                    Object candidate = unit.metadata.get("headingLevel");
                    if (candidate instanceof Number number) {
                        headingLevel = number.intValue();
                    }
                }
                mergeSegmentMetadata(mergedMetadata, unit.metadata);
                current.append(unit.text);
                endExclusive++;
                if (current.length() >= safeMaxChars) {
                    break;
                }
            }
            if (!current.isEmpty()) {
                ChunkResult base = buildChunkResult(truncateToTokenLimit(current.toString(), maxTokens), headingPath);
                Map<String, Object> metadata = new LinkedHashMap<>(base.metadata());
                metadata.putAll(mergedMetadata);
                if (headingLevel != null) {
                    metadata.put("headingLevel", headingLevel);
                }
                metadata.put("chunkStrategy", strategy);
                chunks.add(new ChunkResult(base.content(), headingPath, metadata));
            }
            if (endExclusive >= units.size()) {
                break;
            }
            index = Math.max(index + 1, endExclusive - safeOverlap);
        }
        return chunks;
    }

    private List<ChunkResult> recursiveChunksWithMeta(String text, int maxChars, int overlap, int maxTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        recursiveSplitWithMeta(text, maxChars, overlap, maxTokens, 0, chunks);
        return chunks;
    }

    private void recursiveSplitWithMeta(String text, int maxChars, int overlap, int maxTokens, int separatorIndex, List<ChunkResult> result) {
        if (text.length() <= maxChars && estimateTokens(text) <= maxTokens) {
            result.add(buildChunkResult(text, null));
            return;
        }
        if (separatorIndex >= RECURSIVE_SEPARATORS.size()) {
            result.add(buildChunkResult(truncateToTokenLimit(text, maxTokens), null));
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
                    result.add(buildChunkResult(chunk, null));
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
                result.add(buildChunkResult(chunk, null));
            } else {
                recursiveSplitWithMeta(chunk, maxChars, overlap, maxTokens, separatorIndex + 1, result);
            }
        }
    }

    private ChunkResult buildChunkResult(String content, String headingPath) {
        return new ChunkResult(content, headingPath, inferChunkMetadata(content, headingPath));
    }

    private ChunkResult decorateChunkStrategy(ChunkResult chunk, String strategy) {
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("chunkStrategy", strategy);
        return new ChunkResult(chunk.content(), chunk.headingPath(), metadata);
    }

    private void mergeSegmentMetadata(Map<String, Object> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> {
            if ("segmentType".equals(key) && value instanceof String text && StringUtils.hasText(text)) {
                target.put(key, text);
                return;
            }
            if (value instanceof Boolean boolValue && Boolean.TRUE.equals(boolValue)) {
                target.put(key, true);
            } else if (!target.containsKey(key) && value != null) {
                target.put(key, value);
            }
        });
    }

    private boolean shouldBreakForStrategy(String strategy,
                                           String currentHeadingPath,
                                           SegmentWithHeading unit,
                                           int currentLength,
                                           int minChunkChars) {
        if (!StringUtils.hasText(strategy) || currentLength < minChunkChars) {
            return false;
        }
        if ("manual".equals(strategy)) {
            return StringUtils.hasText(currentHeadingPath)
                    && StringUtils.hasText(unit.headingPath())
                    && !currentHeadingPath.equals(unit.headingPath());
        }
        if ("paper".equals(strategy)) {
            return ("caption".equals(unit.type()) || "table".equals(unit.type()) || "figure".equals(unit.type()))
                    || (StringUtils.hasText(currentHeadingPath)
                    && StringUtils.hasText(unit.headingPath())
                    && !currentHeadingPath.equals(unit.headingPath()));
        }
        return false;
    }

    private boolean shouldIsolateSegment(SegmentWithHeading unit, String strategy) {
        if (unit == null) {
            return false;
        }
        if ("table".equals(strategy)) {
            return "table".equals(unit.type()) || "caption".equals(unit.type());
        }
        return false;
    }

    private Map<String, Object> inferChunkMetadata(String content, String headingPath) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String normalized = content == null ? "" : content.trim();
        if (StringUtils.hasText(headingPath)) {
            metadata.put("headingLevel", resolveHeadingLevel(headingPath));
        }
        boolean hasCodeBlock = normalized.contains("```");
        boolean hasTable = TABLE_ROW_PATTERN.matcher(normalized).find();
        boolean hasList = LIST_ITEM_PATTERN.matcher(normalized).find();
        metadata.put("hasCodeBlock", hasCodeBlock);
        metadata.put("hasTable", hasTable);
        metadata.put("hasList", hasList);
        metadata.put("segmentType", inferSegmentType(normalized, hasCodeBlock, hasTable, hasList, headingPath));
        return metadata;
    }

    private String inferSegmentType(String content,
                                    boolean hasCodeBlock,
                                    boolean hasTable,
                                    boolean hasList,
                                    String headingPath) {
        if (!StringUtils.hasText(content)) {
            return "empty";
        }
        if (hasTable) {
            return "table";
        }
        if (hasCodeBlock) {
            return "code";
        }
        if (hasList) {
            return "list";
        }
        if (StringUtils.hasText(headingPath) && content.startsWith(headingPath)) {
            return "section";
        }
        return "paragraph";
    }

    private Integer resolveHeadingLevel(String headingPath) {
        Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(headingPath.trim());
        if (matcher.matches()) {
            return matcher.group(1).length();
        }
        return 1;
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

    private record SegmentWithHeading(String type, String text, String headingPath, Map<String, Object> metadata) {}

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

    private static KnowledgeEmbeddingPort unsupportedEmbeddingPort() {
        return new KnowledgeEmbeddingPort() {
            @Override
            public float[] embed(String text) {
                throw new UnsupportedOperationException("Semantic chunking is unavailable in this context");
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                throw new UnsupportedOperationException("Semantic chunking is unavailable in this context");
            }
        };
    }
}
