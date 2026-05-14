package com.agenticrag.rag.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            StructuredPdfTextStripper stripper = createStripper();
            stripper.getText(document);
            return normalizePdfText(stripper.renderStructuredText());
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse PDF document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "pdf".equalsIgnoreCase(fileExtension);
    }

    private StructuredPdfTextStripper createStripper() throws IOException {
        StructuredPdfTextStripper stripper = new StructuredPdfTextStripper();
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

    private String normalizePdfText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String normalized = rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("(?m)[ ]{2,}", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private static final class StructuredPdfTextStripper extends PDFTextStripper {

        private final List<PdfLine> lines = new ArrayList<>();
        private int currentPage = 0;

        private StructuredPdfTextStripper() throws IOException {
            super();
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
            float averageFontSize = (float) textPositions.stream()
                    .mapToDouble(TextPosition::getFontSizeInPt)
                    .average()
                    .orElse(0d);
            String fontName = textPositions.get(0).getFont().getName();
            float y = textPositions.get(0).getYDirAdj();
            lines.add(new PdfLine(currentPage, normalized, averageFontSize, isBoldFont(fontName), y));
        }

        private String renderStructuredText() {
            if (lines.isEmpty()) {
                return "";
            }
            double baselineFontSize = resolveBaselineFontSize();
            StringBuilder output = new StringBuilder();
            StringBuilder paragraph = new StringBuilder();
            PdfLine previousBodyLine = null;
            int previousPage = lines.get(0).page();

            for (PdfLine line : lines) {
                if (line.page() != previousPage) {
                    flushParagraph(output, paragraph);
                    output.append("\n");
                    previousBodyLine = null;
                    previousPage = line.page();
                }

                if (isHeading(line, baselineFontSize)) {
                    flushParagraph(output, paragraph);
                    appendHeading(output, line, baselineFontSize);
                    previousBodyLine = null;
                    continue;
                }

                if (paragraph.isEmpty()) {
                    paragraph.append(line.text());
                } else if (shouldJoin(previousBodyLine, line)) {
                    if (paragraph.charAt(paragraph.length() - 1) == '-') {
                        paragraph.setLength(paragraph.length() - 1);
                    } else {
                        paragraph.append(" ");
                    }
                    paragraph.append(line.text());
                } else {
                    flushParagraph(output, paragraph);
                    paragraph.append(line.text());
                }
                previousBodyLine = line;
            }

            flushParagraph(output, paragraph);
            return output.toString();
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

        private void appendHeading(StringBuilder output, PdfLine line, double baselineFontSize) {
            int level = headingLevel(line.fontSize(), baselineFontSize);
            output.append("#".repeat(level)).append(" ").append(line.text()).append("\n\n");
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
            if (previous == null || previous.page() != current.page()) {
                return false;
            }
            double verticalGap = Math.abs(current.y() - previous.y());
            double gapThreshold = Math.max(previous.fontSize(), current.fontSize()) * 2.0d;
            if (verticalGap > gapThreshold) {
                return false;
            }
            return Math.abs(current.fontSize() - previous.fontSize()) <= 1.5d;
        }

        private void flushParagraph(StringBuilder output, StringBuilder paragraph) {
            if (paragraph.isEmpty()) {
                return;
            }
            output.append(paragraph).append("\n\n");
            paragraph.setLength(0);
        }

        private boolean isBoldFont(String fontName) {
            if (!StringUtils.hasText(fontName)) {
                return false;
            }
            String normalized = fontName.toLowerCase();
            return normalized.contains("bold") || normalized.contains("black") || normalized.contains("heavy");
        }
    }

    private record PdfLine(int page, String text, float fontSize, boolean bold, float y) {}
}
