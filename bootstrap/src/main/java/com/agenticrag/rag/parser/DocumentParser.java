package com.agenticrag.rag.parser;

import java.io.InputStream;

public interface DocumentParser {

    String parse(InputStream inputStream, String fileExtension);

    boolean supports(String fileExtension);
}
