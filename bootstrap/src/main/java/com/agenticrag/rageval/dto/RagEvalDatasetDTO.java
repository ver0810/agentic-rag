package com.agenticrag.rageval.dto;

import java.util.List;

public record RagEvalDatasetDTO(
        String name,
        String description,
        List<RagEvalCaseDTO> cases
) {
}
