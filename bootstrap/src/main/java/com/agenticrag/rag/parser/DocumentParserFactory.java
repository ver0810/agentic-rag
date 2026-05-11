package com.agenticrag.rag.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String fileExtension) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileExtension))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported file type: " + fileExtension +
                        ". Supported types: pdf, doc, docx, md, txt"));
    }
}
