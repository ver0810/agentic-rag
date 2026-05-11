package com.agenticrag.feedback;

import java.time.LocalDateTime;

public record FeedbackRequest(
        String traceId,
        String kbId,
        String query,
        String answer,
        Integer rating,
        String comment
) {
}
