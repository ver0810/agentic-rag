package com.agenticrag.rag.parser;

import java.util.List;
import java.util.Map;

public record PageDebugInfo(
        int pageNum,
        int columnCount,
        List<LayoutBlock> blocks,
        Map<String, Object> metadata
) {}
