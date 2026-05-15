package com.agenticrag.knowledge.service;

import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.rag.parser.LogicalSegment;
import com.agenticrag.rag.parser.StructuredParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SemanticChunkingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunkingService.class);

    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile(
            "(?<=[。！？.!?;；])\\s*|\\n\\s*\\n+");

    private static final int DEFAULT_MIN_SENTENCE_CHARS = 10;
    private static final int DEFAULT_MAX_SENTENCE_CHARS = 500;
    private static final int DEFAULT_MIN_CHUNK_CHARS = 100;
    private static final int DEFAULT_MAX_CHUNK_CHARS = 1500;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_BATCH_SIZE = 20;

    private final KnowledgeEmbeddingPort embeddingPort;
    private final TokenCostEstimator tokenCostEstimator;

    public SemanticChunkingService(KnowledgeEmbeddingPort embeddingPort,
                                   TokenCostEstimator tokenCostEstimator) {
        this.embeddingPort = embeddingPort;
        this.tokenCostEstimator = tokenCostEstimator;
    }

    public List<ChunkResult> chunk(String text, Map<String, Object> config) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        int minSentenceChars = intValue(config, "minSentenceChars", DEFAULT_MIN_SENTENCE_CHARS);
        int maxSentenceChars = intValue(config, "maxSentenceChars", DEFAULT_MAX_SENTENCE_CHARS);
        int minChunkChars = intValue(config, "minChunkChars", DEFAULT_MIN_CHUNK_CHARS);
        int maxChunkChars = intValue(config, "maxChunkChars", DEFAULT_MAX_CHUNK_CHARS);
        int maxTokens = intValue(config, "maxTokens", DEFAULT_MAX_TOKENS);
        double similarityThreshold = doubleValue(config, "similarityThreshold", DEFAULT_SIMILARITY_THRESHOLD);
        int batchSize = intValue(config, "batchSize", DEFAULT_BATCH_SIZE);

        List<String> sentences = splitSentences(text, minSentenceChars, maxSentenceChars);
        if (sentences.isEmpty()) {
            return List.of();
        }

        if (sentences.size() == 1) {
            return List.of(buildChunk(sentences.get(0), null, maxTokens));
        }

        try {
            return semanticChunk(sentences, similarityThreshold, minChunkChars, maxChunkChars, maxTokens, batchSize);
        } catch (Exception e) {
            log.warn("Semantic chunking failed, falling back to paragraph chunking: {}", e.getMessage());
            return fallbackChunk(sentences, maxChunkChars, maxTokens);
        }
    }

    public List<ChunkResult> chunk(StructuredParseResult parseResult, Map<String, Object> config) {
        if (parseResult == null || parseResult.segments() == null || parseResult.segments().isEmpty()) {
            return List.of();
        }

        int minChunkChars = intValue(config, "minChunkChars", DEFAULT_MIN_CHUNK_CHARS);
        int maxChunkChars = intValue(config, "maxChunkChars", DEFAULT_MAX_CHUNK_CHARS);
        int maxTokens = intValue(config, "maxTokens", DEFAULT_MAX_TOKENS);
        double similarityThreshold = doubleValue(config, "similarityThreshold", DEFAULT_SIMILARITY_THRESHOLD);
        int batchSize = intValue(config, "batchSize", DEFAULT_BATCH_SIZE);

        List<SemanticUnit> units = extractSemanticUnits(parseResult.segments());
        if (units.isEmpty()) {
            return List.of();
        }

        if (units.size() == 1) {
            return List.of(buildChunk(units.get(0).text, units.get(0).headingPath, units.get(0).metadata, maxTokens));
        }

        try {
            return semanticChunkWithUnits(units, similarityThreshold, minChunkChars, maxChunkChars, maxTokens, batchSize);
        } catch (Exception e) {
            log.warn("Semantic chunking with segments failed, falling back: {}", e.getMessage());
            return fallbackChunkUnits(units, maxChunkChars, maxTokens);
        }
    }

    private List<String> splitSentences(String text, int minChars, int maxChars) {
        String[] rawParts = SENTENCE_SPLIT_PATTERN.split(text);
        List<String> sentences = new ArrayList<>();

        for (String part : rawParts) {
            String trimmed = part.trim();
            if (trimmed.length() < minChars) {
                continue;
            }
            if (trimmed.length() > maxChars) {
                sentences.addAll(splitLongSentence(trimmed, maxChars));
            } else {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> splitLongSentence(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    private List<SemanticUnit> extractSemanticUnits(List<LogicalSegment> segments) {
        List<SemanticUnit> units = new ArrayList<>();
        String currentHeading = null;

        for (LogicalSegment segment : segments) {
            if (!StringUtils.hasText(segment.content())) {
                continue;
            }

            if ("heading".equals(segment.type())) {
                currentHeading = segment.content();
                continue;
            }

            Map<String, Object> metadata = segment.metadata() != null
                    ? new LinkedHashMap<>(segment.metadata())
                    : new LinkedHashMap<>();
            metadata.put("segmentType", segment.type());
            metadata.put("segmentId", segment.id());

            units.add(new SemanticUnit(
                    segment.content(),
                    StringUtils.hasText(segment.headingPath()) ? segment.headingPath() : currentHeading,
                    metadata
            ));
        }
        return units;
    }

    private List<ChunkResult> semanticChunk(List<String> sentences,
                                            double threshold,
                                            int minChunkChars,
                                            int maxChunkChars,
                                            int maxTokens,
                                            int batchSize) {
        List<Double> similarities = computeSimilarities(computeEmbeddings(sentences, batchSize));
        List<ChunkResult> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (shouldBreakForSimilarity(current.length(), sentence.length(), i, similarities, threshold, minChunkChars, maxChunkChars)) {
                chunks.add(buildChunk(current.toString(), null, maxTokens));
                current = new StringBuilder();
            }
            appendWithSeparator(current, sentence, " ");
        }

        if (current.length() > 0) {
            chunks.add(buildChunk(current.toString(), null, maxTokens));
        }
        return chunks;
    }

    private List<ChunkResult> semanticChunkWithUnits(List<SemanticUnit> units,
                                                     double threshold,
                                                     int minChunkChars,
                                                     int maxChunkChars,
                                                     int maxTokens,
                                                     int batchSize) {
        List<String> texts = units.stream().map(u -> u.text).toList();
        List<Double> similarities = computeSimilarities(computeEmbeddings(texts, batchSize));
        List<ChunkResult> chunks = new ArrayList<>();
        ChunkAccumulator current = new ChunkAccumulator();

        for (int i = 0; i < units.size(); i++) {
            SemanticUnit unit = units.get(i);
            if (shouldBreakForSimilarity(current.length(), unit.text.length(), i, similarities, threshold, minChunkChars, maxChunkChars)
                    || shouldBreakForHeading(current.headingPath(), unit.headingPath)) {
                chunks.add(buildChunk(current.content(), current.headingPath(), current.metadata(), maxTokens));
                current = new ChunkAccumulator();
            }
            current.append(unit, "\n\n");
        }

        if (!current.isEmpty()) {
            chunks.add(buildChunk(current.content(), current.headingPath(), current.metadata(), maxTokens));
        }
        return chunks;
    }

    private List<float[]> computeEmbeddings(List<String> texts, int batchSize) {
        List<float[]> allEmbeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                List<float[]> batchEmbeddings = embeddingPort.embedAll(new ArrayList<>(batch));
                allEmbeddings.addAll(batchEmbeddings);
            } catch (Exception e) {
                log.error("Failed to compute embeddings for batch {}-{}: {}", i, end, e.getMessage());
                throw e;
            }

            if (i + batchSize < texts.size()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
            }
        }

        return allEmbeddings;
    }

    private List<Double> computeSimilarities(List<float[]> embeddings) {
        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            similarities.add(cosineSimilarity(embeddings.get(i), embeddings.get(i + 1)));
        }
        return similarities;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    private List<ChunkResult> fallbackChunk(List<String> sentences, int maxChunkChars, int maxTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > maxChunkChars && current.length() > 0) {
                chunks.add(buildChunk(current.toString(), null, maxTokens));
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            chunks.add(buildChunk(current.toString(), null, maxTokens));
        }

        return chunks;
    }

    private List<ChunkResult> fallbackChunkUnits(List<SemanticUnit> units, int maxChunkChars, int maxTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = null;

        for (SemanticUnit unit : units) {
            if (current.length() + unit.text.length() > maxChunkChars && current.length() > 0) {
                chunks.add(buildChunk(current.toString(), currentHeading, null, maxTokens));
                current = new StringBuilder();
                currentHeading = null;
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(unit.text);
            if (currentHeading == null && StringUtils.hasText(unit.headingPath)) {
                currentHeading = unit.headingPath;
            }
        }

        if (current.length() > 0) {
            chunks.add(buildChunk(current.toString(), currentHeading, null, maxTokens));
        }

        return chunks;
    }

    private boolean shouldBreakForSimilarity(int currentLength,
                                             int nextLength,
                                             int index,
                                             List<Double> similarities,
                                             double threshold,
                                             int minChunkChars,
                                             int maxChunkChars) {
        if (currentLength == 0) {
            return false;
        }
        if (currentLength + nextLength > maxChunkChars && currentLength >= minChunkChars) {
            return true;
        }
        return index > 0
                && index - 1 < similarities.size()
                && currentLength >= minChunkChars
                && similarities.get(index - 1) < threshold;
    }

    private boolean shouldBreakForHeading(String currentHeading, String nextHeading) {
        return StringUtils.hasText(currentHeading)
                && StringUtils.hasText(nextHeading)
                && !currentHeading.equals(nextHeading);
    }

    private void appendWithSeparator(StringBuilder current, String text, String separator) {
        if (current.length() > 0) {
            current.append(separator);
        }
        current.append(text);
    }

    private ChunkResult buildChunk(String content, String headingPath, int maxTokens) {
        return buildChunk(content, headingPath, null, maxTokens);
    }

    private ChunkResult buildChunk(String content, String headingPath, Map<String, Object> extraMetadata, int maxTokens) {
        String truncated = truncateToTokenLimit(content, maxTokens);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkStrategy", "semantic");
        metadata.put("charCount", truncated.length());
        metadata.put("tokenCount", estimateTokens(truncated));
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        return new ChunkResult(truncated, headingPath, metadata);
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

    private int intValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private double doubleValue(Map<String, Object> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static final class ChunkAccumulator {
        private final StringBuilder content = new StringBuilder();
        private String headingPath;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        private void append(SemanticUnit unit, String separator) {
            if (content.length() > 0) {
                content.append(separator);
            }
            content.append(unit.text);
            if (!StringUtils.hasText(headingPath) && StringUtils.hasText(unit.headingPath)) {
                headingPath = unit.headingPath;
            }
            if (unit.metadata != null) {
                metadata.putAll(unit.metadata);
            }
        }

        private boolean isEmpty() {
            return content.isEmpty();
        }

        private int length() {
            return content.length();
        }

        private String content() {
            return content.toString();
        }

        private String headingPath() {
            return headingPath;
        }

        private Map<String, Object> metadata() {
            return metadata;
        }
    }

    private record SemanticUnit(String text, String headingPath, Map<String, Object> metadata) {}
}
