package com.agenticrag.feedback;

import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackEntity> submit(@RequestBody FeedbackRequest request,
                                                  @CurrentUser String userId) {
        return ResponseEntity.ok(feedbackService.submit(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<FeedbackEntity>> list(
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) Integer limit,
            @CurrentUser String userId) {
        return ResponseEntity.ok(feedbackService.list(kbId, limit, userId));
    }

    @GetMapping("/summary")
    public ResponseEntity<FeedbackService.FeedbackSummaryDTO> summary(
            @RequestParam(required = false) String kbId,
            @CurrentUser String userId) {
        return ResponseEntity.ok(feedbackService.getSummary(kbId, userId));
    }
}
