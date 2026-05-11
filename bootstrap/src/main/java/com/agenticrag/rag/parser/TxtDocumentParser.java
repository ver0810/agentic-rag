package com.agenticrag.rag.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse TXT document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "txt".equalsIgnoreCase(fileExtension) || "text".equalsIgnoreCase(fileExtension);
    }
}
