package com.agenticrag.rag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentParserTests {

    @Test
    void pdfParserShouldPreserveParagraphAndPageSpacing() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createSamplePdf();

        String parsed = parser.parse(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(parsed.contains("# Guide Title"));
        assertTrue(parsed.contains("First paragraph of the document."));
        assertTrue(parsed.contains("Second page content."));
        assertTrue(parsed.contains("\n\n"));
    }

    @Test
    void pdfParserShouldProduceStructuredSegmentsWithPageMetadata() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createSamplePdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertEquals(4, result.segments().size());
        assertEquals("heading", result.segments().get(0).type());
        assertEquals(1, result.segments().get(0).metadata().get("pageNum"));
        assertEquals("Guide Title", result.segments().get(1).headingPath());
        assertEquals(2, result.segments().get(3).metadata().get("pageNum"));
        assertNotNull(result.segments().get(1).metadata().get("bbox"));
        assertEquals(2, result.pages().size());
        assertEquals("Title", result.pages().get(0).blocks().get(0).type());
        assertEquals(1, result.pages().get(0).columnCount());
        assertEquals("layout_fallback", result.documentMetadata().get("parserMode"));
    }

    @Test
    void pdfParserShouldClassifyListTableAndCaptionBlocks() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createStructuredBlockPdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> "List".equals(block.type())));
        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> "Table".equals(block.type())));
        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> "Caption".equals(block.type())));
        assertTrue(result.segments().stream().anyMatch(segment -> "list".equals(segment.type())));
        assertTrue(result.segments().stream().anyMatch(segment -> "table".equals(segment.type())));
        assertTrue(result.segments().stream().anyMatch(segment -> "caption".equals(segment.type())));
    }

    @Test
    void pdfParserShouldFilterRepeatedHeadersAndFooters() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createHeaderFooterPdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(result.pages().stream().allMatch(page -> page.blocks().stream().anyMatch(block -> "Header".equals(block.type()))));
        assertTrue(result.pages().stream().allMatch(page -> page.blocks().stream().anyMatch(block -> "Footer".equals(block.type()))));
        assertTrue(result.segments().stream().noneMatch(segment -> segment.content().contains("Company Internal")));
        assertTrue(result.segments().stream().noneMatch(segment -> segment.content().contains("Page ")));
    }

    @Test
    void pdfParserShouldMarkContinuedParagraphAcrossPages() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createContinuedParagraphPdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(result.segments().stream()
                .filter(segment -> "paragraph".equals(segment.type()) && Integer.valueOf(2).equals(segment.metadata().get("pageNum")))
                .anyMatch(segment -> Boolean.TRUE.equals(segment.metadata().get("continued"))));
    }

    @Test
    void pdfParserShouldDetectTwoColumnLayout() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createTwoColumnPdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertEquals(2, result.pages().get(0).columnCount());
        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> block.columnIndex() == 0));
        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> block.columnIndex() == 1));
        assertTrue(result.segments().stream().anyMatch(segment -> Integer.valueOf(1).equals(segment.metadata().get("columnIndex"))));
    }

    @Test
    void pdfParserShouldMarkWideHeadingAsSpanningColumns() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createTwoColumnPdfWithWideHeading();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(result.pages().get(0).blocks().stream()
                .anyMatch(block -> "Title".equals(block.type()) && Boolean.TRUE.equals(block.attributes().get("spanColumns"))));
        assertTrue(result.segments().stream()
                .anyMatch(segment -> "heading".equals(segment.type()) && Boolean.TRUE.equals(segment.metadata().get("spanColumns"))));
    }

    @Test
    void pdfParserShouldExtractFigureBlocksAndSegments() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createFigurePdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> "Figure".equals(block.type())));
        assertTrue(result.segments().stream().anyMatch(segment -> "figure".equals(segment.type())));
        assertTrue(result.pages().get(0).blocks().stream().allMatch(block -> block.attributes().containsKey("readingOrder")));
        assertTrue(result.segments().stream().allMatch(segment -> segment.metadata().containsKey("readingOrder")));
        int figureIndex = indexOfSegmentType(result, "figure");
        assertTrue(figureIndex >= 0);
        assertTrue(figureIndex > 0);
    }

    @Test
    void pdfParserShouldFallbackToOcrForImageOnlyPage() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser(new DocumentOcrService() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String provider() {
                return "stub-ocr";
            }

            @Override
            public java.util.List<OcrTextBlock> recognizePage(BufferedImage pageImage, int pageNum, java.util.Map<String, Object> hints) {
                return java.util.List.of(
                        new OcrTextBlock(
                                "Scanned Title",
                                java.util.Map.of("x1", 0.10f, "y1", 0.10f, "x2", 0.55f, "y2", 0.14f),
                                0.99d,
                                java.util.Map.of("bold", true)),
                        new OcrTextBlock(
                                "Scanned paragraph from OCR.",
                                java.util.Map.of("x1", 0.10f, "y1", 0.24f, "x2", 0.85f, "y2", 0.27f),
                                0.96d,
                                java.util.Map.of()));
            }
        });
        byte[] pdfBytes = createImageOnlyPdf();

        StructuredParseResult result = parser.parseStructured(new ByteArrayInputStream(pdfBytes), "pdf");

        assertEquals("ocr_fallback", result.documentMetadata().get("parserMode"));
        assertEquals("stub-ocr", result.documentMetadata().get("ocrProvider"));
        assertTrue(result.segments().stream().anyMatch(segment -> "heading".equals(segment.type()) && "ocr".equals(segment.metadata().get("source"))));
        assertTrue(result.segments().stream().anyMatch(segment -> "ocr".equals(segment.metadata().get("source"))));
        assertTrue(result.pages().get(0).blocks().stream().anyMatch(block -> "OcrText".equals(block.type())));
        assertEquals(Boolean.TRUE, result.pages().get(0).metadata().get("hasOcrBlocks"));
        assertEquals(2L, result.pages().get(0).metadata().get("ocrBlockCount"));
        java.util.Set<String> ocrBlockIds = result.pages().get(0).blocks().stream()
                .filter(block -> "OcrText".equals(block.type()))
                .map(LayoutBlock::id)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(result.segments().stream()
                .filter(segment -> "ocr".equals(segment.metadata().get("source")))
                .anyMatch(segment -> {
                    Object sourceBlockIds = segment.metadata().get("sourceBlockIds");
                    return sourceBlockIds instanceof java.util.List<?> ids
                            && !ids.isEmpty()
                            && ids.stream().allMatch(id -> id instanceof String value && ocrBlockIds.contains(value));
                }));
    }

    private byte[] createSamplePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage firstPage = new PDPage();
            PDPage secondPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(secondPage);

            try (PDPageContentStream stream = new PDPageContentStream(document, firstPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(72, 720);
                stream.showText("Guide Title");
                stream.newLineAtOffset(0, -28);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("First paragraph of the document.");
                stream.newLineAtOffset(0, -36);
                stream.showText("Another paragraph follows here.");
                stream.endText();
            }

            try (PDPageContentStream stream = new PDPageContentStream(document, secondPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Second page content.");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createStructuredBlockPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("- First bullet item");
                stream.newLineAtOffset(0, -24);
                stream.showText("Name    Value    Status");
                stream.newLineAtOffset(0, -24);
                stream.showText("Table 1: Runtime metrics");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createHeaderFooterPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage firstPage = new PDPage();
            PDPage secondPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(secondPage);

            writeHeaderFooterPage(document, firstPage, "Main content on page one.", "Page 1");
            writeHeaderFooterPage(document, secondPage, "Main content on page two.", "Page 2");

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createContinuedParagraphPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage firstPage = new PDPage();
            PDPage secondPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(secondPage);

            try (PDPageContentStream stream = new PDPageContentStream(document, firstPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("This paragraph continues");
                stream.endText();
            }
            try (PDPageContentStream stream = new PDPageContentStream(document, secondPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("on the next page without ending punctuation");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeHeaderFooterPage(PDDocument document, PDPage page, String bodyText, String footerText) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            stream.newLineAtOffset(72, 770);
            stream.showText("Company Internal");
            stream.newLineAtOffset(0, -80);
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.showText(bodyText);
            stream.newLineAtOffset(0, -620);
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            stream.showText(footerText);
            stream.endText();
        }
    }

    private byte[] createTwoColumnPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Left column line one");
                stream.newLineAtOffset(0, -24);
                stream.showText("Left column line two");
                stream.newLineAtOffset(260, 24);
                stream.showText("Right column line one");
                stream.newLineAtOffset(0, -24);
                stream.showText("Right column line two");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createTwoColumnPdfWithWideHeading() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(72, 740);
                stream.showText("Wide Section Title");
                stream.newLineAtOffset(0, -36);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("Left column line one");
                stream.newLineAtOffset(0, -24);
                stream.showText("Left column line two");
                stream.newLineAtOffset(260, 24);
                stream.showText("Right column line one");
                stream.newLineAtOffset(0, -24);
                stream.showText("Right column line two");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createFigurePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, Color.ORANGE.getRGB());
                }
            }

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(document, image), 72, 680, 180, 80);
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(72, 760);
                stream.showText("Overview heading");
                stream.newLineAtOffset(0, -120);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("Figure 1: Sample image");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createImageOnlyPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, Color.WHITE.getRGB());
                }
            }

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(document, image), 72, 600, 300, 160);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private int indexOfSegmentType(StructuredParseResult result, String segmentType) {
        for (int i = 0; i < result.segments().size(); i++) {
            if (segmentType.equals(result.segments().get(i).type())) {
                return i;
            }
        }
        return -1;
    }
}
