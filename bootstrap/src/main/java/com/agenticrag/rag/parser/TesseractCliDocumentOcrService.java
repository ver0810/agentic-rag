package com.agenticrag.rag.parser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Primary
public class TesseractCliDocumentOcrService implements DocumentOcrService {

    private final boolean enabled;
    private final String command;
    private final String language;
    private final int pageSegMode;
    private final Duration timeout;

    public TesseractCliDocumentOcrService(
            @Value("${agenticrag.parser.pdf.ocr.tesseract.enabled:false}") boolean enabled,
            @Value("${agenticrag.parser.pdf.ocr.tesseract.command:tesseract}") String command,
            @Value("${agenticrag.parser.pdf.ocr.tesseract.language:eng}") String language,
            @Value("${agenticrag.parser.pdf.ocr.tesseract.page-seg-mode:3}") int pageSegMode,
            @Value("${agenticrag.parser.pdf.ocr.tesseract.timeout-seconds:30}") long timeoutSeconds) {
        this.enabled = enabled;
        this.command = StringUtils.hasText(command) ? command.trim() : "tesseract";
        this.language = StringUtils.hasText(language) ? language.trim() : "eng";
        this.pageSegMode = pageSegMode;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public String provider() {
        return "tesseract_cli";
    }

    @Override
    public List<OcrTextBlock> recognizePage(BufferedImage pageImage, int pageNum, Map<String, Object> hints) {
        if (!enabled || pageImage == null) {
            return List.of();
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("agenticrag-pdf-ocr-");
            Path inputFile = tempDir.resolve("page-" + pageNum + ".png");
            Path outputBase = tempDir.resolve("page-" + pageNum + "-ocr");
            ImageIO.write(pageImage, "png", inputFile.toFile());
            List<String> commandLine = List.of(
                    command,
                    inputFile.toString(),
                    outputBase.toString(),
                    "--psm",
                    Integer.toString(pageSegMode),
                    "-l",
                    language,
                    "tsv");
            Process process = new ProcessBuilder(commandLine)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return List.of();
            }
            if (process.exitValue() != 0) {
                return List.of();
            }
            Path tsvFile = outputBase.resolveSibling(outputBase.getFileName() + ".tsv");
            if (!Files.exists(tsvFile)) {
                return List.of();
            }
            return parseTsv(Files.readString(tsvFile, StandardCharsets.UTF_8), pageImage.getWidth(), pageImage.getHeight());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        } finally {
            if (tempDir != null) {
                deleteIfExists(tempDir);
            }
        }
    }

    static List<OcrTextBlock> parseTsv(String tsv, int imageWidth, int imageHeight) {
        if (!StringUtils.hasText(tsv) || imageWidth <= 0 || imageHeight <= 0) {
            return List.of();
        }
        String[] rows = tsv.split("\\R");
        if (rows.length <= 1) {
            return List.of();
        }
        Map<String, OcrLineAccumulator> accumulators = new LinkedHashMap<>();
        for (int i = 1; i < rows.length; i++) {
            String row = rows[i];
            if (!StringUtils.hasText(row)) {
                continue;
            }
            String[] columns = row.split("\\t", -1);
            if (columns.length < 12) {
                continue;
            }
            String text = columns[11] == null ? "" : columns[11].trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            int level = parseInt(columns[0], -1);
            if (level != 5) {
                continue;
            }
            int pageNum = parseInt(columns[1], 1);
            int blockNum = parseInt(columns[2], 0);
            int parNum = parseInt(columns[3], 0);
            int lineNum = parseInt(columns[4], 0);
            int left = parseInt(columns[6], 0);
            int top = parseInt(columns[7], 0);
            int width = parseInt(columns[8], 0);
            int height = parseInt(columns[9], 0);
            double conf = parseDouble(columns[10], 0d);
            String key = pageNum + ":" + blockNum + ":" + parNum + ":" + lineNum;
            accumulators.computeIfAbsent(key, ignored -> new OcrLineAccumulator(pageNum))
                    .append(text, left, top, width, height, conf);
        }
        return accumulators.values().stream()
                .sorted(Comparator.comparingInt(OcrLineAccumulator::top).thenComparingInt(OcrLineAccumulator::left))
                .map(acc -> acc.toBlock(imageWidth, imageHeight))
                .toList();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.toList().forEach(TesseractCliDocumentOcrService::deleteIfExists);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignore) {
        }
    }

    private static final class OcrLineAccumulator {
        private final int pageNum;
        private final StringBuilder text = new StringBuilder();
        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE;
        private int bottom = Integer.MIN_VALUE;
        private double confidenceSum = 0d;
        private int confidenceCount = 0;

        private OcrLineAccumulator(int pageNum) {
            this.pageNum = pageNum;
        }

        void append(String nextText, int x, int y, int width, int height, double confidence) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(nextText.trim());
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + height);
            if (confidence >= 0d) {
                confidenceSum += confidence;
                confidenceCount++;
            }
        }

        int top() {
            return top == Integer.MAX_VALUE ? 0 : top;
        }

        int left() {
            return left == Integer.MAX_VALUE ? 0 : left;
        }

        OcrTextBlock toBlock(int imageWidth, int imageHeight) {
            Map<String, Float> bbox = Map.of(
                    "x1", normalize(left(), imageWidth),
                    "y1", normalize(top(), imageHeight),
                    "x2", normalize(Math.max(left(), right), imageWidth),
                    "y2", normalize(Math.max(top(), bottom), imageHeight));
            Map<String, Object> attributes = Map.of(
                    "pageNum", pageNum,
                    "source", "tesseract_tsv");
            double confidence = confidenceCount == 0 ? 0d : confidenceSum / confidenceCount / 100d;
            return new OcrTextBlock(text.toString(), bbox, confidence, attributes);
        }

        private float normalize(int value, int limit) {
            if (limit <= 0) {
                return 0f;
            }
            float normalized = (float) value / (float) limit;
            if (normalized < 0f) {
                return 0f;
            }
            return Math.min(1f, normalized);
        }
    }
}
