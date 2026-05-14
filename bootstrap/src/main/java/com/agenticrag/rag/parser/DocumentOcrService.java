package com.agenticrag.rag.parser;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public interface DocumentOcrService {

    boolean isAvailable();

    String provider();

    List<OcrTextBlock> recognizePage(BufferedImage pageImage, int pageNum, Map<String, Object> hints);
}
