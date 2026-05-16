package com.agenticrag.rag.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PythonPdfDocumentParser implements DocumentParser {

    private static final List<String> SUPPORTED_STRATEGIES = List.of("paragraph", "smart", "paper", "manual", "table", "naive", "one");

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final String scriptPath;
    private final int renderDpi;

    public PythonPdfDocumentParser(ObjectMapper objectMapper,
                                   @Value("${agenticrag.parser.pdf.python.command:python}") String pythonCommand,
                                   @Value("${agenticrag.parser.pdf.python.script:scripts/pdf_pipeline/main.py}") String scriptPath,
                                   @Value("${agenticrag.parser.pdf.python.render-dpi:216}") int renderDpi) {
        this.objectMapper = objectMapper;
        this.pythonCommand = pythonCommand;
        this.scriptPath = scriptPath;
        this.renderDpi = renderDpi;
    }

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        return parseStructured(inputStream, fileExtension).asPlainText();
    }

    @Override
    public StructuredParseResult parseStructured(InputStream inputStream, String fileExtension) {
        return parseStructured(inputStream, fileExtension, null);
    }

    @Override
    public StructuredParseResult parseStructured(InputStream inputStream, String fileExtension, String strategy) {
        try {
            return invokePythonPipeline(inputStream, strategy);
        } catch (Exception ex) {
            throw new DocumentParseException("Python PDF pipeline failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "pdf".equalsIgnoreCase(fileExtension);
    }

    @Override
    public boolean supports(String fileExtension, String strategy) {
        if (!supports(fileExtension)) {
            return false;
        }
        return !StringUtils.hasText(strategy) || SUPPORTED_STRATEGIES.contains(strategy.trim().toLowerCase());
    }

    @Override
    public int order() {
        return 100;
    }

    private StructuredParseResult invokePythonPipeline(InputStream inputStream, String strategy) throws Exception {
        Path tempDir = Files.createTempDirectory("agenticrag-python-pdf-");
        try {
            Path pdfPath = tempDir.resolve("input.pdf");
            try (inputStream) {
                Files.copy(inputStream, pdfPath);
            }
            Path imagesDir = tempDir.resolve("pages");
            Files.createDirectories(imagesDir);
            Map<String, Object> payload = extractNativePayload(pdfPath, imagesDir);
            Path payloadPath = tempDir.resolve("payload.json");
            objectMapper.writeValue(payloadPath.toFile(), payload);
            Path outputPath = tempDir.resolve("result.json");

            List<String> command = new ArrayList<>();
            command.add(pythonCommand);
            command.add(scriptPath);
            command.add("--input-json");
            command.add(payloadPath.toAbsolutePath().toString());
            command.add("--output-json");
            command.add(outputPath.toAbsolutePath().toString());
            command.add("--strategy");
            command.add(StringUtils.hasText(strategy) ? strategy.trim().toLowerCase() : "naive");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Python process exited with " + exitCode + ": " + stdout);
            }
            if (!Files.exists(outputPath)) {
                throw new IllegalStateException("Python pipeline did not produce output JSON. stdout=" + stdout);
            }

            Map<String, Object> result = objectMapper.readValue(outputPath.toFile(), new TypeReference<>() {});
            return toStructuredParseResult(result);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private Map<String, Object> extractNativePayload(Path pdfPath, Path imagesDir) throws IOException {
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(document);
            NativeTextCollector collector = new NativeTextCollector();
            collector.getText(document);
            Map<Integer, List<Map<String, Object>>> figures = extractFigures(document);

            List<Map<String, Object>> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                int pageNum = i + 1;
                PDPage page = document.getPage(i);
                BufferedImage image = renderer.renderImageWithDPI(i, renderDpi, ImageType.RGB);
                Path imagePath = imagesDir.resolve("page-" + pageNum + ".png");
                javax.imageio.ImageIO.write(image, "png", imagePath.toFile());
                Map<String, Object> pagePayload = new LinkedHashMap<>();
                pagePayload.put("page_num", pageNum);
                pagePayload.put("page_width", page.getMediaBox().getWidth());
                pagePayload.put("page_height", page.getMediaBox().getHeight());
                pagePayload.put("image_width", image.getWidth());
                pagePayload.put("image_height", image.getHeight());
                pagePayload.put("image_path", imagePath.toAbsolutePath().toString());
                pagePayload.put("native_boxes", collector.boxesByPage.getOrDefault(pageNum, List.of()));
                pagePayload.put("figures", figures.getOrDefault(pageNum, List.of()));
                pages.add(pagePayload);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("doc_type", "pdf");
            payload.put("page_count", document.getNumberOfPages());
            payload.put("render_dpi", renderDpi);
            payload.put("pages", pages);
            return payload;
        }
    }

    private Map<Integer, List<Map<String, Object>>> extractFigures(PDDocument document) throws IOException {
        Map<Integer, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage page = document.getPage(i);
            FigureCollector collector = new FigureCollector(page, i + 1);
            collector.processPage(page);
            result.put(i + 1, collector.figures);
        }
        return result;
    }

    private StructuredParseResult toStructuredParseResult(Map<String, Object> payload) {
        List<LogicalSegment> segments = objectMapper.convertValue(
                payload.getOrDefault("segments", List.of()),
                new TypeReference<List<LogicalSegment>>() {});
        List<PageDebugInfo> pages = objectMapper.convertValue(
                payload.getOrDefault("pages", List.of()),
                new TypeReference<List<PageDebugInfo>>() {});
        Map<String, Object> metadata = objectMapper.convertValue(
                payload.getOrDefault("documentMetadata", Map.of()),
                new TypeReference<Map<String, Object>>() {});
        metadata = new LinkedHashMap<>(metadata);
        metadata.put("parserEngine", "python_module");
        return new StructuredParseResult(segments, pages, metadata);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static final class NativeTextCollector extends PDFTextStripper {

        private final Map<Integer, List<Map<String, Object>>> boxesByPage = new LinkedHashMap<>();
        private int currentPage = 0;

        private NativeTextCollector() throws IOException {
            super();
            setSortByPosition(true);
            setAddMoreFormatting(true);
            setLineSeparator("\n");
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPage++;
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (!StringUtils.hasText(normalized) || textPositions == null || textPositions.isEmpty()) {
                return;
            }
            float x1 = textPositions.get(0).getXDirAdj();
            float maxX = (float) textPositions.stream()
                    .mapToDouble(tp -> tp.getXDirAdj() + tp.getWidthDirAdj())
                    .max()
                    .orElse(x1);
            float minY = (float) textPositions.stream().mapToDouble(TextPosition::getYDirAdj).min().orElse(0f);
            float maxY = (float) textPositions.stream()
                    .mapToDouble(tp -> tp.getYDirAdj() + tp.getHeightDir())
                    .max()
                    .orElse(minY + 10f);
            float avgFont = (float) textPositions.stream().mapToDouble(TextPosition::getFontSizeInPt).average().orElse(12d);
            String fontName = textPositions.get(0).getFont().getName();

            Map<String, Object> box = new LinkedHashMap<>();
            box.put("text", normalized);
            box.put("x1", x1);
            box.put("y1", minY);
            box.put("x2", maxX);
            box.put("y2", maxY);
            box.put("fontSize", avgFont);
            box.put("bold", fontName != null && fontName.toLowerCase().matches(".*(bold|black|heavy).*"));
            box.put("source", "pdf_native");
            boxesByPage.computeIfAbsent(currentPage, ignored -> new ArrayList<>()).add(box);
        }
    }

    private static final class FigureCollector extends PDFGraphicsStreamEngine {

        private final int pageNum;
        private final List<Map<String, Object>> figures = new ArrayList<>();

        private FigureCollector(PDPage page, int pageNum) {
            super(page);
            this.pageNum = pageNum;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            float x = ctm.getTranslateX();
            float y = ctm.getTranslateY();
            float width = Math.abs(ctm.getScalingFactorX());
            float height = Math.abs(ctm.getScalingFactorY());
            Map<String, Object> figure = new LinkedHashMap<>();
            figure.put("page_num", pageNum);
            figure.put("x1", x);
            figure.put("y1", y);
            figure.put("x2", x + width);
            figure.put("y2", y + height);
            figure.put("width", width);
            figure.put("height", height);
            figures.add(figure);
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        }
    }
}
