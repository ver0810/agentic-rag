package com.agenticrag.rag.parser;

import java.io.InputStream;

public interface DocumentParser {

    String parse(InputStream inputStream, String fileExtension);

    default StructuredParseResult parseStructured(InputStream inputStream, String fileExtension) {
        return StructuredTextSegmenter.segment(parse(inputStream, fileExtension), fileExtension);
    }

    boolean supports(String fileExtension);
}
