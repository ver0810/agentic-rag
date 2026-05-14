package com.agenticrag.rag.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class HtmlDocumentParser implements DocumentParser {

    private static final List<String> BLOCK_TAGS = List.of("p", "div", "section", "article", "blockquote");

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        try {
            Document document = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            StringBuilder content = new StringBuilder();
            Element root = document.body() != null ? document.body() : document;
            appendChildren(content, root);
            return content.toString().replaceAll("\\n{3,}", "\n\n").trim();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse HTML document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "html".equalsIgnoreCase(fileExtension) || "htm".equalsIgnoreCase(fileExtension);
    }

    private void appendChildren(StringBuilder content, Element parent) {
        for (Node child : parent.childNodes()) {
            appendNode(content, child);
        }
    }

    private void appendNode(StringBuilder content, Node node) {
        if (node instanceof TextNode textNode) {
            String text = normalizeInlineWhitespace(textNode.text());
            if (StringUtils.hasText(text)) {
                content.append(text);
            }
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase();
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                ensureBlockSpacing(content);
                int level = Integer.parseInt(tag.substring(1));
                content.append("#".repeat(level)).append(" ").append(normalizeInlineWhitespace(element.text())).append("\n\n");
            }
            case "ul" -> {
                ensureBlockSpacing(content);
                for (Element li : element.children()) {
                    if ("li".equalsIgnoreCase(li.tagName())) {
                        content.append("- ").append(normalizeInlineWhitespace(li.text())).append("\n");
                    }
                }
                content.append("\n");
            }
            case "ol" -> {
                ensureBlockSpacing(content);
                int index = 1;
                for (Element li : element.children()) {
                    if ("li".equalsIgnoreCase(li.tagName())) {
                        content.append(index++).append(". ").append(normalizeInlineWhitespace(li.text())).append("\n");
                    }
                }
                content.append("\n");
            }
            case "pre" -> {
                ensureBlockSpacing(content);
                content.append("```\n").append(element.text()).append("\n```\n\n");
            }
            case "table" -> {
                ensureBlockSpacing(content);
                appendTable(content, element);
                content.append("\n");
            }
            case "br" -> content.append("\n");
            default -> {
                if (BLOCK_TAGS.contains(tag)) {
                    ensureBlockSpacing(content);
                    content.append(normalizeInlineWhitespace(element.text())).append("\n\n");
                } else {
                    appendChildren(content, element);
                }
            }
        }
    }

    private void appendTable(StringBuilder content, Element table) {
        List<Element> rows = table.select("tr");
        if (rows.isEmpty()) {
            return;
        }
        int columnCount = rows.stream().mapToInt(row -> row.select("th,td").size()).max().orElse(0);
        if (columnCount == 0) {
            return;
        }
        appendTableRow(content, rows.get(0), columnCount);
        content.append("|");
        for (int i = 0; i < columnCount; i++) {
            content.append(" --- |");
        }
        content.append("\n");
        for (int i = 1; i < rows.size(); i++) {
            appendTableRow(content, rows.get(i), columnCount);
        }
    }

    private void appendTableRow(StringBuilder content, Element row, int columnCount) {
        List<Element> cells = row.select("th,td");
        content.append("|");
        for (int i = 0; i < columnCount; i++) {
            String text = i < cells.size() ? normalizeInlineWhitespace(cells.get(i).text()) : "";
            content.append(" ").append(text.replace("|", "\\|")).append(" |");
        }
        content.append("\n");
    }

    private void ensureBlockSpacing(StringBuilder content) {
        if (!content.isEmpty() && !content.toString().endsWith("\n\n")) {
            if (content.charAt(content.length() - 1) != '\n') {
                content.append("\n");
            }
            content.append("\n");
        }
    }

    private String normalizeInlineWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
