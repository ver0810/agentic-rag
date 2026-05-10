package com.agenticrag.infra.ai.rag.parser;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private final Parser parser;

    public MarkdownDocumentParser() {
        this.parser = Parser.builder().build();
    }

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Node document = parser.parse(markdown);
            TextExtractor extractor = new TextExtractor();
            document.accept(extractor);
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse Markdown document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "md".equalsIgnoreCase(fileExtension) || "markdown".equalsIgnoreCase(fileExtension);
    }

    private static class TextExtractor extends AbstractVisitor {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
            super.visit(text);
        }

        public String getText() {
            return sb.toString();
        }
    }
}
