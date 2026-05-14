package com.agenticrag.rag.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Pattern LIST_PATTERN = Pattern.compile("^(([-*•])|(\\d+[.)]))\\s+.+");
    private static final Pattern CAPTION_PATTERN = Pattern.compile(
            "^(图|表|figure|table)\\s*[0-9一二三四五六七八九十IVXivx.:：-].*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(".*\\|.*\\|.*|.*\\s{2,}.*\\s{2,}.*");
    private static final Pattern CONTINUATION_END_PATTERN = Pattern.compile(".*[。！？.!?;；:：]$");
    private final DocumentOcrService documentOcrService;

    public PdfDocumentParser() {
        this(new NoopDocumentOcrService());
    }

    @Autowired
    public PdfDocumentParser(DocumentOcrService documentOcrService) {
        this.documentOcrService = documentOcrService;
    }

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        return parseStructured(inputStream, fileExtension).asPlainText();
    }

    @Override
    public StructuredParseResult parseStructured(InputStream inputStream, String fileExtension) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            Map<Integer, List<FigurePlacement>> figurePlacements = extractFigurePlacements(document);
            StructuredPdfTextStripper stripper = createStripper(figurePlacements);
            stripper.getText(document);
            stripper.addOcrBlocks(extractOcrBlocks(document, stripper.pagesWithoutText()));
            return stripper.toStructuredResult();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse PDF document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "pdf".equalsIgnoreCase(fileExtension);
    }

    private StructuredPdfTextStripper createStripper(Map<Integer, List<FigurePlacement>> figurePlacements) throws IOException {
        StructuredPdfTextStripper stripper = new StructuredPdfTextStripper(figurePlacements, documentOcrService.provider());
        stripper.setSortByPosition(true);
        stripper.setAddMoreFormatting(true);
        stripper.setLineSeparator("\n");
        stripper.setParagraphStart("\n\n");
        stripper.setParagraphEnd("\n\n");
        stripper.setPageStart("");
        stripper.setPageEnd("\n\n");
        stripper.setArticleStart("\n");
        stripper.setArticleEnd("\n");
        return stripper;
    }

    private Map<Integer, List<FigurePlacement>> extractFigurePlacements(PDDocument document) throws IOException {
        Map<Integer, List<FigurePlacement>> placements = new LinkedHashMap<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage page = document.getPage(i);
            FigureCollector collector = new FigureCollector(page, i + 1);
            collector.processPage(page);
            placements.put(i + 1, collector.figures());
        }
        return placements;
    }

    private Map<Integer, List<OcrTextBlock>> extractOcrBlocks(PDDocument document, List<Integer> pagesWithoutText) throws IOException {
        if (!documentOcrService.isAvailable() || pagesWithoutText.isEmpty()) {
            return Map.of();
        }
        PDFRenderer renderer = new PDFRenderer(document);
        Map<Integer, List<OcrTextBlock>> ocrBlocks = new LinkedHashMap<>();
        for (Integer pageNum : pagesWithoutText) {
            int pageIndex = pageNum - 1;
            PDPage page = document.getPage(pageIndex);
            BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 144);
            Map<String, Object> hints = new LinkedHashMap<>();
            hints.put("docType", "pdf");
            hints.put("pageNum", pageNum);
            hints.put("pageWidth", page.getMediaBox().getWidth());
            hints.put("pageHeight", page.getMediaBox().getHeight());
            hints.put("imageWidth", pageImage.getWidth());
            hints.put("imageHeight", pageImage.getHeight());
            List<OcrTextBlock> blocks = documentOcrService.recognizePage(pageImage, pageNum, hints);
            if (blocks != null && !blocks.isEmpty()) {
                ocrBlocks.put(pageNum, blocks);
            }
        }
        return ocrBlocks;
    }

    private static final class StructuredPdfTextStripper extends PDFTextStripper {

        private final List<PdfLine> lines = new ArrayList<>();
        private final Map<Integer, Float> pageWidths = new LinkedHashMap<>();
        private final Map<Integer, Float> pageHeights = new LinkedHashMap<>();
        private final Map<Integer, List<FigurePlacement>> figurePlacements;
        private final Map<Integer, List<OcrPageBlock>> ocrBlocksByPage = new LinkedHashMap<>();
        private final Map<Integer, ColumnLayout> pageColumnLayouts = new LinkedHashMap<>();
        private final List<Integer> ocrAugmentedPages = new ArrayList<>();
        private final String ocrProvider;
        private int currentPage = 0;

        private StructuredPdfTextStripper(Map<Integer, List<FigurePlacement>> figurePlacements, String ocrProvider) throws IOException {
            super();
            this.figurePlacements = figurePlacements == null ? Map.of() : figurePlacements;
            this.ocrProvider = StringUtils.hasText(ocrProvider) ? ocrProvider : "unknown";
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPage++;
            pageWidths.put(currentPage, page.getMediaBox().getWidth());
            pageHeights.put(currentPage, page.getMediaBox().getHeight());
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            String rawText = text == null ? "" : text.trim();
            String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (!StringUtils.hasText(normalized) || textPositions == null || textPositions.isEmpty()) {
                return;
            }
            float averageFontSize = (float) textPositions.stream()
                    .mapToDouble(TextPosition::getFontSizeInPt)
                    .average()
                    .orElse(0d);
            String fontName = textPositions.get(0).getFont().getName();
            float x = textPositions.get(0).getXDirAdj();
            float y = textPositions.get(0).getYDirAdj();
            float maxX = (float) textPositions.stream()
                    .mapToDouble(tp -> tp.getXDirAdj() + tp.getWidthDirAdj())
                    .max()
                    .orElse(x);
            float minY = (float) textPositions.stream()
                    .mapToDouble(TextPosition::getYDirAdj)
                    .min()
                    .orElse(y);
            float maxY = (float) textPositions.stream()
                    .mapToDouble(tp -> tp.getYDirAdj() + tp.getHeightDir())
                    .max()
                    .orElse(y + averageFontSize);
            lines.add(new PdfLine(
                    currentPage,
                    normalized,
                    averageFontSize,
                    isBoldFont(fontName),
                    y,
                    x,
                    maxX,
                    minY,
                    maxY,
                    0,
                    false,
                    LIST_PATTERN.matcher(normalized).matches(),
                    TABLE_ROW_PATTERN.matcher(rawText).matches(),
                    CAPTION_PATTERN.matcher(normalized).matches(),
                    "pdf_text",
                    1.0d,
                    List.of()));
        }

        private StructuredParseResult toStructuredResult() {
            if (lines.isEmpty()) {
                return new StructuredParseResult(List.of(), List.of(), Map.of(
                        "docType", "pdf",
                        "segmentCount", 0,
                        "pageCount", pageWidths.size(),
                        "parserMode", "empty"));
            }
            List<PdfLine> resolvedLines = applyColumnDetection(lines);
            double baselineFontSize = resolveBaselineFontSize();
            List<PdfLine> contentLines = resolvedLines.stream().filter(line -> !isHeaderFooter(resolvedLines, line, baselineFontSize)).toList();
            List<LogicalSegment> segments = new ArrayList<>();
            int order = 0;
            if (contentLines.isEmpty()) {
                List<PageDebugInfo> pages = buildPageDebugInfo(resolvedLines, baselineFontSize);
                return new StructuredParseResult(segments, pages, Map.of(
                        "docType", "pdf",
                        "segmentCount", 0,
                        "pageCount", pageWidths.size(),
                        "parserMode", resolveParserMode()));
            }
            ParagraphAccumulator paragraph = null;
            PdfLine previousBodyLine = null;
            ParagraphAccumulator previousPageParagraph = null;
            String currentHeadingPath = null;
            Integer currentPage = null;
            for (PageFlowItem item : buildPageFlow(contentLines)) {
                if (currentPage == null || item.pageNum() != currentPage) {
                    if (paragraph != null) {
                        segments.add(paragraph.toSegment(order++));
                        previousPageParagraph = paragraph;
                        paragraph = null;
                    }
                    previousBodyLine = null;
                    currentPage = item.pageNum();
                }
                if (item.figure() != null) {
                    if (paragraph != null) {
                        segments.add(paragraph.toSegment(order++));
                        paragraph = null;
                    }
                    previousBodyLine = null;
                    segments.add(toFigureSegment(item.figure(), order++));
                    continue;
                }

                PdfLine line = Objects.requireNonNull(item.line());
                if (isHeading(line, baselineFontSize)) {
                    if (paragraph != null) {
                        segments.add(paragraph.toSegment(order++));
                        paragraph = null;
                    }
                    int headingLevel = headingLevel(line.fontSize(), baselineFontSize);
                    currentHeadingPath = updateHeadingPath(currentHeadingPath, line.text(), headingLevel);
                    segments.add(toHeadingSegment(line, currentHeadingPath, headingLevel, order++));
                    previousBodyLine = null;
                    continue;
                }

                if (paragraph == null) {
                    boolean continued = previousPageParagraph != null && shouldContinueAcrossPage(previousPageParagraph, line);
                    paragraph = ParagraphAccumulator.from(line, currentHeadingPath, continued);
                    previousPageParagraph = null;
                } else if (shouldJoin(previousBodyLine, line)) {
                    paragraph.append(line);
                } else {
                    segments.add(paragraph.toSegment(order++));
                    paragraph = ParagraphAccumulator.from(line, currentHeadingPath, false);
                }
                previousBodyLine = line;
            }

            if (paragraph != null) {
                segments.add(paragraph.toSegment(order));
            }

            List<LogicalSegment> orderedSegments = applySegmentReadingOrder(segments);
            List<PageDebugInfo> pages = buildPageDebugInfo(resolvedLines, baselineFontSize);
            return new StructuredParseResult(orderedSegments, pages, Map.of(
                    "docType", "pdf",
                    "segmentCount", orderedSegments.size(),
                    "pageCount", pageWidths.size(),
                    "parserMode", resolveParserMode(),
                    "ocrPageCount", ocrAugmentedPages.size(),
                    "ocrProvider", ocrProvider));
        }

        private String resolveParserMode() {
            if (ocrAugmentedPages.isEmpty()) {
                return "layout_fallback";
            }
            return lines.stream().allMatch(line -> "ocr".equals(line.source())) ? "ocr_fallback" : "layout_fallback+ocr";
        }

        private List<Integer> pagesWithoutText() {
            List<Integer> missingPages = new ArrayList<>();
            for (Integer pageNum : pageWidths.keySet()) {
                boolean hasText = lines.stream().anyMatch(line -> line.page() == pageNum);
                if (!hasText) {
                    missingPages.add(pageNum);
                }
            }
            return missingPages;
        }

        private void addOcrBlocks(Map<Integer, List<OcrTextBlock>> ocrBlocksByPage) {
            if (ocrBlocksByPage == null || ocrBlocksByPage.isEmpty()) {
                return;
            }
            for (Map.Entry<Integer, List<OcrTextBlock>> entry : ocrBlocksByPage.entrySet()) {
                int pageNum = entry.getKey();
                float pageWidth = pageWidths.getOrDefault(pageNum, 0f);
                float pageHeight = pageHeights.getOrDefault(pageNum, 0f);
                List<OcrPageBlock> pageBlocks = new ArrayList<>();
                int index = 0;
                for (OcrTextBlock block : entry.getValue()) {
                    OcrPageBlock pageBlock = new OcrPageBlock("pdf-ocr-block-" + pageNum + "-" + index++, block);
                    pageBlocks.add(pageBlock);
                    PdfLine line = toOcrLine(pageNum, pageWidth, pageHeight, pageBlock);
                    if (line != null) {
                        lines.add(line);
                        if (!ocrAugmentedPages.contains(pageNum)) {
                            ocrAugmentedPages.add(pageNum);
                        }
                    }
                }
                this.ocrBlocksByPage.put(pageNum, List.copyOf(pageBlocks));
            }
        }

        private PdfLine toOcrLine(int pageNum, float pageWidth, float pageHeight, OcrPageBlock pageBlock) {
            OcrTextBlock block = pageBlock.block();
            if (block == null || !StringUtils.hasText(block.text()) || block.bbox() == null || pageWidth <= 0f || pageHeight <= 0f) {
                return null;
            }
            float x1 = pageWidth * block.bbox().getOrDefault("x1", 0f);
            float x2 = pageWidth * block.bbox().getOrDefault("x2", 0f);
            float minY = pageHeight * (1f - block.bbox().getOrDefault("y2", 1f));
            float maxY = pageHeight * (1f - block.bbox().getOrDefault("y1", 0f));
            float y = minY;
            float fontSize = Math.max(10f, maxY - minY);
            String normalized = block.text().replaceAll("\\s+", " ").trim();
            return new PdfLine(
                    pageNum,
                    normalized,
                    fontSize,
                    Boolean.TRUE.equals(block.attributes() == null ? null : block.attributes().get("bold")),
                    y,
                    x1,
                    x2,
                    minY,
                    maxY,
                    0,
                    false,
                    LIST_PATTERN.matcher(normalized).matches(),
                    TABLE_ROW_PATTERN.matcher(normalized).matches(),
                    CAPTION_PATTERN.matcher(normalized).matches(),
                    "ocr",
                    block.confidence(),
                    List.of(pageBlock.id()));
        }

        private List<PageFlowItem> buildPageFlow(List<PdfLine> contentLines) {
            Map<Integer, List<PdfLine>> linesByPage = new LinkedHashMap<>();
            for (PdfLine line : contentLines) {
                linesByPage.computeIfAbsent(line.page(), ignored -> new ArrayList<>()).add(line);
            }
            List<PageFlowItem> flow = new ArrayList<>();
            List<Integer> pages = new ArrayList<>(pageWidths.keySet());
            pages.sort(Comparator.naturalOrder());
            for (Integer pageNum : pages) {
                List<PageFlowItem> pageItems = new ArrayList<>();
                for (PdfLine line : linesByPage.getOrDefault(pageNum, List.of())) {
                    pageItems.add(PageFlowItem.forLine(pageNum, line));
                }
                for (FigurePlacement figure : figurePlacements.getOrDefault(pageNum, List.of())) {
                    pageItems.add(PageFlowItem.forFigure(pageNum, figure));
                }
                pageItems.sort(pageFlowComparator(pageColumnLayouts.getOrDefault(pageNum, new ColumnLayout(false, 0f))));
                flow.addAll(pageItems);
            }
            return flow;
        }

        private Comparator<PageFlowItem> pageFlowComparator(ColumnLayout layout) {
            return (left, right) -> {
                int byPage = Integer.compare(left.pageNum(), right.pageNum());
                if (byPage != 0) {
                    return byPage;
                }
                int byLane = Integer.compare(flowLane(left, layout), flowLane(right, layout));
                if (byLane != 0) {
                    return byLane;
                }
                int byY = Float.compare(left.sortY(), right.sortY());
                if (byY != 0) {
                    return byY;
                }
                int byX = Float.compare(left.sortX(), right.sortX());
                if (byX != 0) {
                    return byX;
                }
                return Integer.compare(left.figure() == null ? 1 : 0, right.figure() == null ? 1 : 0);
            };
        }

        private int flowLane(PageFlowItem item, ColumnLayout layout) {
            if (!layout.twoColumn()) {
                return 0;
            }
            if (item.spanColumns()) {
                return -1;
            }
            return item.columnIndex();
        }

        private List<PageDebugInfo> buildPageDebugInfo(List<PdfLine> resolvedLines, double baselineFontSize) {
            Map<Integer, List<LayoutBlock>> pageBlocks = new LinkedHashMap<>();
            int blockIndex = 0;
            for (PdfLine line : resolvedLines) {
                String type = inferLayoutBlockType(line, baselineFontSize);
                boolean filtered = isHeaderFooter(resolvedLines, line, baselineFontSize);
                boolean spanColumns = spansColumns(line);
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("fontSize", line.fontSize());
                attributes.put("isBold", line.bold());
                attributes.put("filtered", filtered);
                attributes.put("spanColumns", spanColumns);
                attributes.put("source", line.source());
                attributes.put("confidence", line.confidence());
                if ("Title".equals(type)) {
                    attributes.put("headingLevel", headingLevel(line.fontSize(), baselineFontSize));
                }
                attributes.put("readingOrder", -1);
                LayoutBlock block = new LayoutBlock(
                        "pdf-block-" + blockIndex++,
                        type,
                        Map.of(
                                "x1", line.x(),
                                "y1", line.minY(),
                                "x2", line.maxX(),
                                "y2", line.maxY()),
                        line.text(),
                        null,
                        line.page(),
                        line.columnIndex(),
                        1.0d,
                        attributes);
                pageBlocks.computeIfAbsent(line.page(), ignored -> new ArrayList<>()).add(block);
            }
            for (Map.Entry<Integer, List<FigurePlacement>> entry : figurePlacements.entrySet()) {
                for (FigurePlacement figure : entry.getValue()) {
                    pageBlocks.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(new LayoutBlock(
                            "pdf-block-" + blockIndex++,
                            "Figure",
                            Map.of(
                                    "x1", figure.x1(),
                                    "y1", figure.y1(),
                                    "x2", figure.x2(),
                                    "y2", figure.y2()),
                            "[Figure]",
                            null,
                            entry.getKey(),
                            figure.columnIndex(),
                            1.0d,
                            Map.of(
                                    "readingOrder", -1,
                                    "spanColumns", figure.spanColumns(),
                                    "width", figure.width(),
                                    "height", figure.height(),
                                    "source", "image_xobject")));
                }
            }
            for (Map.Entry<Integer, List<OcrPageBlock>> entry : ocrBlocksByPage.entrySet()) {
                int pageNum = entry.getKey();
                float pageWidth = pageWidths.getOrDefault(pageNum, 0f);
                float pageHeight = pageHeights.getOrDefault(pageNum, 0f);
                for (OcrPageBlock pageBlock : entry.getValue()) {
                    OcrTextBlock block = pageBlock.block();
                    Map<String, Float> bbox = denormalizeBlockBbox(block, pageWidth, pageHeight);
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    attributes.put("readingOrder", -1);
                    attributes.put("source", "ocr");
                    attributes.put("ocrProvider", ocrProvider);
                    attributes.put("ocrConfidence", block.confidence());
                    attributes.put("spanColumns", false);
                    if (block.attributes() != null && !block.attributes().isEmpty()) {
                        attributes.putAll(block.attributes());
                    }
                    pageBlocks.computeIfAbsent(pageNum, ignored -> new ArrayList<>()).add(new LayoutBlock(
                            pageBlock.id(),
                            "OcrText",
                            bbox,
                            block.text(),
                            null,
                            pageNum,
                            inferOcrColumnIndex(pageNum, bbox),
                            block.confidence(),
                            attributes));
                }
            }
            List<PageDebugInfo> pages = new ArrayList<>();
            for (Map.Entry<Integer, List<LayoutBlock>> entry : pageBlocks.entrySet()) {
                int pageNum = entry.getKey();
                List<LayoutBlock> blocks = applyBlockReadingOrder(entry.getValue(), pageColumnLayouts.getOrDefault(pageNum, new ColumnLayout(false, 0f)));
                ColumnLayout layout = pageColumnLayouts.getOrDefault(pageNum, new ColumnLayout(false, 0f));
                int columnCount = layout.twoColumn() ? 2 : 1;
                long ocrBlockCount = blocks.stream().filter(block -> "OcrText".equals(block.type())).count();
                pages.add(new PageDebugInfo(
                        pageNum,
                        columnCount,
                        blocks,
                        Map.of(
                                "width", pageWidths.getOrDefault(pageNum, 0f),
                                "splitX", layout.splitX(),
                                "twoColumn", layout.twoColumn(),
                                "ocrBlockCount", ocrBlockCount,
                                "hasOcrBlocks", ocrBlockCount > 0)));
            }
            return pages;
        }

        private Map<String, Float> denormalizeBlockBbox(OcrTextBlock block, float pageWidth, float pageHeight) {
            if (block.bbox() == null || pageWidth <= 0f || pageHeight <= 0f) {
                return Map.of("x1", 0f, "y1", 0f, "x2", 0f, "y2", 0f);
            }
            return Map.of(
                    "x1", pageWidth * block.bbox().getOrDefault("x1", 0f),
                    "y1", pageHeight * (1f - block.bbox().getOrDefault("y2", 1f)),
                    "x2", pageWidth * block.bbox().getOrDefault("x2", 0f),
                    "y2", pageHeight * (1f - block.bbox().getOrDefault("y1", 0f)));
        }

        private int inferOcrColumnIndex(int pageNum, Map<String, Float> bbox) {
            ColumnLayout layout = pageColumnLayouts.getOrDefault(pageNum, new ColumnLayout(false, 0f));
            if (!layout.twoColumn()) {
                return 0;
            }
            return bbox.getOrDefault("x1", 0f) >= layout.splitX() ? 1 : 0;
        }

        private List<LayoutBlock> applyBlockReadingOrder(List<LayoutBlock> blocks, ColumnLayout layout) {
            List<LayoutBlock> sortedBlocks = new ArrayList<>(blocks);
            sortedBlocks.sort(blockComparator(layout));
            List<LayoutBlock> orderedBlocks = new ArrayList<>(sortedBlocks.size());
            for (int i = 0; i < sortedBlocks.size(); i++) {
                LayoutBlock block = sortedBlocks.get(i);
                Map<String, Object> attributes = new LinkedHashMap<>(block.attributes());
                attributes.put("readingOrder", i);
                orderedBlocks.add(new LayoutBlock(
                        block.id(),
                        block.type(),
                        block.bbox(),
                        block.text(),
                        block.html(),
                        block.pageNum(),
                        block.columnIndex(),
                        block.confidence(),
                        attributes));
            }
            return orderedBlocks;
        }

        private Comparator<LayoutBlock> blockComparator(ColumnLayout layout) {
            return (left, right) -> {
                int byPage = Integer.compare(left.pageNum(), right.pageNum());
                if (byPage != 0) {
                    return byPage;
                }
                int byLane = Integer.compare(
                        layoutLane(left.columnIndex(), layout.twoColumn(), isSpanColumns(left)),
                        layoutLane(right.columnIndex(), layout.twoColumn(), isSpanColumns(right)));
                if (byLane != 0) {
                    return byLane;
                }
                int byY = Float.compare(bboxY2(left.bbox()), bboxY2(right.bbox()));
                if (byY != 0) {
                    return byY;
                }
                return Float.compare(bboxX1(left.bbox()), bboxX1(right.bbox()));
            };
        }

        private int layoutLane(int columnIndex, boolean twoColumn, boolean spanColumns) {
            if (!twoColumn) {
                return 0;
            }
            if (spanColumns) {
                return -1;
            }
            return columnIndex;
        }

        private boolean isSpanColumns(LayoutBlock block) {
            return Boolean.TRUE.equals(block.attributes().get("spanColumns"));
        }

        private float bboxY2(Map<String, Float> bbox) {
            return bbox.getOrDefault("y2", 0f);
        }

        private float bboxX1(Map<String, Float> bbox) {
            return bbox.getOrDefault("x1", 0f);
        }

        private LogicalSegment toFigureSegment(FigurePlacement figure, int order) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("docType", "pdf");
            metadata.put("pageNum", figure.pageNum());
            metadata.put("columnIndex", figure.columnIndex());
            metadata.put("spanColumns", figure.spanColumns());
            metadata.put("segmentType", "figure");
            metadata.put("layoutBlockType", "Figure");
            metadata.put("bbox", Map.of(
                    "x1", figure.x1(),
                    "y1", figure.y1(),
                    "x2", figure.x2(),
                    "y2", figure.y2()));
            metadata.put("readingOrder", order);
            return new LogicalSegment(
                    "pdf-seg-" + order,
                    "figure",
                    "[Figure]",
                    null,
                    order,
                    order,
                    metadata);
        }

        private List<LogicalSegment> applySegmentReadingOrder(List<LogicalSegment> segments) {
            List<LogicalSegment> ordered = new ArrayList<>(segments.size());
            for (int i = 0; i < segments.size(); i++) {
                LogicalSegment segment = segments.get(i);
                Map<String, Object> metadata = new LinkedHashMap<>(segment.metadata());
                metadata.put("readingOrder", i);
                ordered.add(new LogicalSegment(
                        segment.id(),
                        segment.type(),
                        segment.content(),
                        segment.headingPath(),
                        i,
                        i,
                        metadata));
            }
            return ordered;
        }

        private double resolveBaselineFontSize() {
            return lines.stream()
                    .map(PdfLine::fontSize)
                    .sorted(Comparator.naturalOrder())
                    .skip(Math.max(0, (lines.size() / 2) - 1L))
                    .findFirst()
                    .orElse(12f);
        }

        private boolean isHeading(PdfLine line, double baselineFontSize) {
            String text = line.text();
            if (!StringUtils.hasText(text) || text.length() > 120) {
                return false;
            }
            if (text.matches(".*[。！？.!?；;：:].*")) {
                return false;
            }
            double fontRatio = baselineFontSize <= 0 ? 1.0d : line.fontSize() / baselineFontSize;
            return fontRatio >= 1.25d || (line.bold() && fontRatio >= 1.1d);
        }

        private int headingLevel(float fontSize, double baselineFontSize) {
            double ratio = baselineFontSize <= 0 ? 1.0d : fontSize / baselineFontSize;
            if (ratio >= 1.6d) {
                return 1;
            }
            if (ratio >= 1.4d) {
                return 2;
            }
            return 3;
        }

        private boolean shouldJoin(PdfLine previous, PdfLine current) {
            if (previous == null || previous.page() != current.page() || previous.columnIndex() != current.columnIndex()) {
                return false;
            }
            if (!inferBodyType(previous).equals(inferBodyType(current))) {
                return false;
            }
            double verticalGap = Math.abs(current.y() - previous.y());
            double gapThreshold = Math.max(previous.fontSize(), current.fontSize()) * 2.0d;
            if (verticalGap > gapThreshold) {
                return false;
            }
            if (Math.abs(current.fontSize() - previous.fontSize()) > 1.5d) {
                return false;
            }
            return Math.abs(current.x() - previous.x()) <= Math.max(24d, previous.fontSize() * 2d);
        }

        private List<PdfLine> applyColumnDetection(List<PdfLine> rawLines) {
            Map<Integer, List<PdfLine>> byPage = new LinkedHashMap<>();
            for (PdfLine line : rawLines) {
                byPage.computeIfAbsent(line.page(), ignored -> new ArrayList<>()).add(line);
            }
            List<PdfLine> resolved = new ArrayList<>(rawLines.size());
            for (Map.Entry<Integer, List<PdfLine>> entry : byPage.entrySet()) {
                List<PdfLine> pageLines = entry.getValue();
                List<Float> xs = pageLines.stream()
                        .map(PdfLine::x)
                        .sorted()
                        .toList();
                float width = pageWidths.getOrDefault(entry.getKey(), 0f);
                boolean twoColumn = false;
                float splitX = width * 0.55f;
                if (xs.size() >= 4 && width > 0f) {
                    float maxGap = 0f;
                    int maxGapIndex = -1;
                    for (int i = 1; i < xs.size(); i++) {
                        float gap = xs.get(i) - xs.get(i - 1);
                        if (gap > maxGap) {
                            maxGap = gap;
                            maxGapIndex = i;
                        }
                    }
                    if (maxGapIndex > 0 && maxGap > width * 0.12f) {
                        float leftCount = maxGapIndex;
                        float rightCount = xs.size() - maxGapIndex;
                        if (leftCount >= 2 && rightCount >= 2) {
                            twoColumn = true;
                            splitX = (xs.get(maxGapIndex - 1) + xs.get(maxGapIndex)) / 2f;
                        }
                    }
                }
                pageColumnLayouts.put(entry.getKey(), new ColumnLayout(twoColumn, splitX));
                for (PdfLine line : pageLines) {
                    int columnIndex = twoColumn && line.x() >= splitX ? 1 : 0;
                    boolean spanColumns = twoColumn && line.x() < splitX && line.maxX() > splitX;
                    resolved.add(line.withLayout(columnIndex, spanColumns));
                }
            }
            return resolved;
        }

        private LogicalSegment toHeadingSegment(PdfLine line, String headingPath, int headingLevel, int order) {
            Map<String, Object> metadata = baseMetadata(line);
            metadata.put("segmentType", "heading");
            metadata.put("headingLevel", headingLevel);
            metadata.put("layoutBlockType", "title");
            metadata.put("readingOrder", order);
            return new LogicalSegment(
                    "pdf-seg-" + order,
                    "heading",
                    "#".repeat(headingLevel) + " " + line.text(),
                    headingPath,
                    order,
                    order,
                    metadata);
        }

        private Map<String, Object> baseMetadata(PdfLine line) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("docType", "pdf");
            metadata.put("pageNum", line.page());
            metadata.put("columnIndex", line.columnIndex());
            metadata.put("spanColumns", line.spanColumns());
            metadata.put("fontSize", line.fontSize());
            metadata.put("isBold", line.bold());
            metadata.put("source", line.source());
            metadata.put("confidence", line.confidence());
            if (!line.sourceBlockIds().isEmpty()) {
                metadata.put("sourceBlockIds", line.sourceBlockIds());
            }
            metadata.put("bbox", Map.of(
                    "x1", line.x(),
                    "y1", line.minY(),
                    "x2", line.maxX(),
                    "y2", line.maxY()));
            return metadata;
        }

        private String inferLayoutBlockType(PdfLine line, double baselineFontSize) {
            if (isHeaderFooter(lines, line, baselineFontSize)) {
                return isTopMargin(line) ? "Header" : "Footer";
            }
            if (isHeading(line, baselineFontSize)) {
                return "Title";
            }
            return switch (inferBodyType(line)) {
                case "list" -> "List";
                case "table" -> "Table";
                case "caption" -> "Caption";
                default -> "Text";
            };
        }

        private String inferBodyType(PdfLine line) {
            String text = line.text();
            if (!StringUtils.hasText(text)) {
                return "text";
            }
            if (line.captionLike()) {
                return "caption";
            }
            if (line.listLike()) {
                return "list";
            }
            if (line.tableLike()) {
                return "table";
            }
            return "text";
        }

        private String updateHeadingPath(String currentHeadingPath, String headingText, int headingLevel) {
            List<String> stack = new ArrayList<>();
            if (StringUtils.hasText(currentHeadingPath)) {
                stack.addAll(List.of(currentHeadingPath.split("\\s*>\\s*")));
            }
            while (stack.size() >= headingLevel) {
                stack.remove(stack.size() - 1);
            }
            stack.add(headingText);
            return String.join(" > ", stack);
        }

        private boolean isBoldFont(String fontName) {
            if (!StringUtils.hasText(fontName)) {
                return false;
            }
            String normalized = fontName.toLowerCase();
            return normalized.contains("bold") || normalized.contains("black") || normalized.contains("heavy");
        }

        private boolean isHeaderFooter(List<PdfLine> sourceLines, PdfLine line, double baselineFontSize) {
            if (pageHeights.size() < 2) {
                return false;
            }
            if (!isTopMargin(line) && !isBottomMargin(line)) {
                return false;
            }
            long repeatedCount = sourceLines.stream()
                    .filter(other -> other.page() != line.page())
                    .filter(other -> normalizeMarginText(other.text()).equals(normalizeMarginText(line.text())))
                    .filter(other -> (isTopMargin(line) && isTopMargin(other)) || (isBottomMargin(line) && isBottomMargin(other)))
                    .count();
            if (repeatedCount == 0) {
                return false;
            }
            if (isHeading(line, baselineFontSize)) {
                return false;
            }
            return true;
        }

        private boolean isTopMargin(PdfLine line) {
            float height = pageHeights.getOrDefault(line.page(), 0f);
            return height > 0f && line.minY() >= height * 0.88f;
        }

        private boolean isBottomMargin(PdfLine line) {
            float height = pageHeights.getOrDefault(line.page(), 0f);
            return height > 0f && line.maxY() <= height * 0.12f;
        }

        private String normalizeForComparison(String text) {
            return text == null ? "" : text.replaceAll("\\s+", " ").trim().toLowerCase();
        }

        private String normalizeMarginText(String text) {
            return normalizeForComparison(text)
                    .replaceAll("\\d+", "")
                    .replaceAll("\\b[ivxlcdm]+\\b", "")
                    .replaceAll("[.:：-]+", "")
                    .trim();
        }

        private boolean shouldContinueAcrossPage(ParagraphAccumulator previousParagraph, PdfLine currentLine) {
            String previousType = previousParagraph.inferBodyType();
            if (!"paragraph".equals(previousType)) {
                return false;
            }
            if (!"text".equals(inferBodyType(currentLine))) {
                return false;
            }
            if (previousParagraph.columnIndex != currentLine.columnIndex()) {
                return false;
            }
            return !CONTINUATION_END_PATTERN.matcher(previousParagraph.content.toString().trim()).matches();
        }

        private boolean spansColumns(PdfLine line) {
            return line.spanColumns();
        }
    }

    private static final class ParagraphAccumulator {
        private final int page;
        private final int columnIndex;
        private final String headingPath;
        private final float fontSize;
        private final StringBuilder content;
        private final boolean continued;
        private final boolean spanColumns;
        private final List<String> sourceBlockIds;
        private boolean listLike;
        private boolean tableLike;
        private boolean captionLike;
        private float minX;
        private float maxX;
        private float minY;
        private float maxY;

        private ParagraphAccumulator(PdfLine line, String headingPath, boolean continued) {
            this.page = line.page();
            this.columnIndex = line.columnIndex();
            this.headingPath = headingPath;
            this.fontSize = line.fontSize();
            this.continued = continued;
            this.spanColumns = line.spanColumns();
            this.sourceBlockIds = new ArrayList<>(line.sourceBlockIds());
            this.content = new StringBuilder(line.text());
            this.listLike = line.listLike();
            this.tableLike = line.tableLike();
            this.captionLike = line.captionLike();
            this.minX = line.x();
            this.maxX = line.maxX();
            this.minY = line.minY();
            this.maxY = line.maxY();
        }

        static ParagraphAccumulator from(PdfLine line, String headingPath, boolean continued) {
            return new ParagraphAccumulator(line, headingPath, continued);
        }

        void append(PdfLine line) {
            if (!content.isEmpty()) {
                if (content.charAt(content.length() - 1) == '-') {
                    content.setLength(content.length() - 1);
                } else {
                    content.append(" ");
                }
            }
            content.append(line.text());
            listLike = listLike || line.listLike();
            tableLike = tableLike || line.tableLike();
            captionLike = captionLike || line.captionLike();
            for (String blockId : line.sourceBlockIds()) {
                if (!sourceBlockIds.contains(blockId)) {
                    sourceBlockIds.add(blockId);
                }
            }
            minX = Math.min(minX, line.x());
            maxX = Math.max(maxX, line.maxX());
            minY = Math.min(minY, line.minY());
            maxY = Math.max(maxY, line.maxY());
        }

        LogicalSegment toSegment(int order) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            String bodyType = inferBodyType();
            metadata.put("docType", "pdf");
            metadata.put("pageNum", page);
            metadata.put("columnIndex", columnIndex);
            metadata.put("spanColumns", spanColumns);
            metadata.put("fontSize", fontSize);
            metadata.put("segmentType", bodyType);
            metadata.put("continued", continued);
            metadata.put("layoutBlockType", switch (bodyType) {
                case "list" -> "List";
                case "table" -> "Table";
                case "caption" -> "Caption";
                default -> "Text";
            });
            metadata.put("readingOrder", order);
            if (!sourceBlockIds.isEmpty()) {
                metadata.put("sourceBlockIds", List.copyOf(sourceBlockIds));
            }
            metadata.put("bbox", Map.of(
                    "x1", minX,
                    "y1", minY,
                    "x2", maxX,
                    "y2", maxY));
            return new LogicalSegment(
                    "pdf-seg-" + order,
                    bodyType,
                    content.toString(),
                    headingPath,
                    order,
                    order,
                    metadata);
        }

        private String inferBodyType() {
            String text = content.toString();
            if (!StringUtils.hasText(text)) {
                return "paragraph";
            }
            if (captionLike || CAPTION_PATTERN.matcher(text).matches()) {
                return "caption";
            }
            if (listLike || LIST_PATTERN.matcher(text).matches()) {
                return "list";
            }
            if (tableLike || TABLE_ROW_PATTERN.matcher(text).matches()) {
                return "table";
            }
            return "paragraph";
        }
    }

    private record PdfLine(int page,
                           String text,
                           float fontSize,
                           boolean bold,
                           float y,
                           float x,
                           float maxX,
                           float minY,
                           float maxY,
                           int columnIndex,
                           boolean spanColumns,
                           boolean listLike,
                           boolean tableLike,
                           boolean captionLike,
                           String source,
                           double confidence,
                           List<String> sourceBlockIds) {
        private PdfLine withLayout(int nextColumnIndex, boolean nextSpanColumns) {
            return new PdfLine(page, text, fontSize, bold, y, x, maxX, minY, maxY, nextColumnIndex, nextSpanColumns, listLike, tableLike, captionLike, source, confidence, sourceBlockIds);
        }
    }

    private record ColumnLayout(boolean twoColumn, float splitX) {}

    private record OcrPageBlock(String id, OcrTextBlock block) {}

    private record PageFlowItem(
            int pageNum,
            PdfLine line,
            FigurePlacement figure,
            int columnIndex,
            boolean spanColumns,
            float sortX,
            float sortY) {
        private static PageFlowItem forLine(int pageNum, PdfLine line) {
            return new PageFlowItem(pageNum, line, null, line.columnIndex(), line.spanColumns(), line.x(), line.maxY());
        }

        private static PageFlowItem forFigure(int pageNum, FigurePlacement figure) {
            return new PageFlowItem(pageNum, null, figure, figure.columnIndex(), figure.spanColumns(), figure.x1(), figure.y2());
        }
    }

    private record FigurePlacement(
            int pageNum,
            float x1,
            float y1,
            float x2,
            float y2,
            float width,
            float height,
            int columnIndex,
            boolean spanColumns) {}

    private static final class FigureCollector extends PDFGraphicsStreamEngine {
        private final int pageNum;
        private final float pageWidth;
        private final List<FigurePlacement> figures = new ArrayList<>();

        private FigureCollector(PDPage page, int pageNum) {
            super(page);
            this.pageNum = pageNum;
            this.pageWidth = page.getMediaBox().getWidth();
        }

        List<FigurePlacement> figures() {
            return figures;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            float x = ctm.getTranslateX();
            float y = ctm.getTranslateY();
            float width = Math.abs(ctm.getScalingFactorX());
            float height = Math.abs(ctm.getScalingFactorY());
            float x2 = x + width;
            float y2 = y + height;
            boolean spanColumns = width >= pageWidth * 0.6f;
            int columnIndex = x >= pageWidth * 0.5f ? 1 : 0;
            figures.add(new FigurePlacement(pageNum, x, y, x2, y2, width, height, columnIndex, spanColumns));
        }

        @Override
        public void clip(int windingRule) {}

        @Override
        public void moveTo(float x, float y) {}

        @Override
        public void lineTo(float x, float y) {}

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {}

        @Override
        public void endPath() {}

        @Override
        public void strokePath() {}

        @Override
        public void fillPath(int windingRule) {}

        @Override
        public void fillAndStrokePath(int windingRule) {}

        @Override
        public void shadingFill(COSName shadingName) {}

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
    }
}
