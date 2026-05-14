package com.agenticrag.rag.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DocumentParserFactory {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserFactory.class);

    private final List<DocumentParser> parsers;
    private final PythonDocumentParser pythonParser;

    public DocumentParserFactory(List<DocumentParser> parsers, PythonDocumentParser pythonParser) {
        this.parsers = parsers;
        this.pythonParser = pythonParser;
    }

    public DocumentParser getParser(String fileExtension) {
        return getParser(fileExtension, null);
    }

    public DocumentParser getParser(String fileExtension, String strategy) {
        // Try Python service first if healthy
        if (pythonParser.isHealthy()) {
            if (pythonParser.supports(fileExtension, strategy)) {
                log.debug("Using Python service parser for: {} (strategy: {})", fileExtension, strategy);
                return pythonParser;
            }
        } else {
            log.warn("Python parser service is not healthy, falling back to legacy parsers");
        }

        // Fallback to other parsers
        return parsers.stream()
                .filter(p -> p != pythonParser) // Exclude PythonDocumentParser from fallback
                .filter(parser -> parser.supports(fileExtension, strategy))
                .sorted(Comparator.comparingInt(DocumentParser::order).reversed())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported file type: " + fileExtension +
                        ". Supported types: pdf, doc, docx, xlsx, xls, pptx, ppt, md, html, txt, png, jpg"));
    }

    public boolean isPythonServiceHealthy() {
        return pythonParser.isHealthy();
    }
}
