package com.agenticrag.rag.parser;

import java.io.InputStream;

public interface DocumentParser {

    String parse(InputStream inputStream, String fileExtension);

    default StructuredParseResult parseStructured(InputStream inputStream, String fileExtension) {
        return StructuredTextSegmenter.segment(parse(inputStream, fileExtension), fileExtension);
    }

    default StructuredParseResult parseStructured(InputStream inputStream, String fileExtension, String strategy) {
        return parseStructured(inputStream, fileExtension);
    }

    boolean supports(String fileExtension);

    default boolean supports(String fileExtension, String strategy) {
        return supports(fileExtension);
    }

    default int order() {
        return 0;
    }
}
