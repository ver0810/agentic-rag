package com.agenticrag.rag.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        if ("docx".equalsIgnoreCase(fileExtension)) {
            return parseDocx(inputStream);
        } else if ("doc".equalsIgnoreCase(fileExtension)) {
            return parseDoc(inputStream);
        }
        throw new DocumentParseException("Unsupported Word format: " + fileExtension);
    }

    private String parseDocx(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = normalizeParagraphText(paragraph);
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                Integer headingLevel = resolveHeadingLevel(paragraph);
                if (headingLevel != null) {
                    content.append("#".repeat(headingLevel)).append(" ").append(text).append("\n\n");
                } else {
                    content.append(text).append("\n\n");
                }
            }

            for (XWPFTable table : document.getTables()) {
                List<List<String>> rows = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(normalizeInlineWhitespace(cell.getText()));
                    }
                    rows.add(cells);
                }
                appendMarkdownTable(content, rows);
                if (!rows.isEmpty()) {
                    content.append("\n");
                }
            }

            return content.toString().trim();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse DOCX document", e);
        }
    }

    private String parseDoc(InputStream inputStream) {
        try (HWPFDocument document = new HWPFDocument(inputStream)) {
            return document.getDocumentText();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse DOC document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "docx".equalsIgnoreCase(fileExtension) || "doc".equalsIgnoreCase(fileExtension);
    }

    private String normalizeParagraphText(XWPFParagraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.text();
            if (runText != null) {
                text.append(runText);
            }
        }
        if (text.isEmpty()) {
            return normalizeInlineWhitespace(paragraph.getText());
        }
        return normalizeInlineWhitespace(text.toString());
    }

    private Integer resolveHeadingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (!StringUtils.hasText(style)) {
            return null;
        }
        String normalized = style.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("heading")) {
            String suffix = normalized.substring("heading".length()).trim();
            try {
                return Math.min(6, Math.max(1, Integer.parseInt(suffix)));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        if (normalized.contains("title") || normalized.contains("标题")) {
            return 1;
        }
        return null;
    }

    private void appendMarkdownTable(StringBuilder content, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columnCount == 0) {
            return;
        }
        appendMarkdownTableRow(content, rows.get(0), columnCount);
        content.append("|");
        for (int i = 0; i < columnCount; i++) {
            content.append(" --- |");
        }
        content.append("\n");
        for (int i = 1; i < rows.size(); i++) {
            appendMarkdownTableRow(content, rows.get(i), columnCount);
        }
    }

    private void appendMarkdownTableRow(StringBuilder content, List<String> row, int columnCount) {
        content.append("|");
        for (int i = 0; i < columnCount; i++) {
            String cell = i < row.size() ? row.get(i) : "";
            content.append(" ").append(cell.replace("|", "\\|")).append(" |");
        }
        content.append("\n");
    }

    private String normalizeInlineWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
