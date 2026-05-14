package com.agenticrag.rag.parser;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

@Component
public class NoopDocumentOcrService implements DocumentOcrService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String provider() {
        return "noop";
    }

    @Override
    public List<OcrTextBlock> recognizePage(BufferedImage pageImage, int pageNum, Map<String, Object> hints) {
        return List.of();
    }
}
