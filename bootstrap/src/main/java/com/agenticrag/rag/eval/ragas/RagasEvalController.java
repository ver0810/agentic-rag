package com.agenticrag.rag.eval.ragas;

import com.agenticrag.user.auth.CurrentUser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/eval/ragas")
public class RagasEvalController {

    private final RagasEvalService ragasEvalService;

    public RagasEvalController(RagasEvalService ragasEvalService) {
        this.ragasEvalService = ragasEvalService;
    }

    @PostMapping("/run")
    public ResponseEntity<RagasReport> runEvaluation(@RequestBody EvalRequest request,
                                                     @CurrentUser String userId) {
        List<RagasSample> samples = request.getSamples().stream()
                .map(s -> new RagasSample(s.getId(), s.getQuestion(), s.getGroundTruth()))
                .toList();

        RagasReport report = ragasEvalService.evaluate(request.getKbId(), samples, userId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/sample")
    public ResponseEntity<RagasResult> evaluateSample(@RequestBody SampleRequest request,
                                                      @CurrentUser String userId) {
        RagasSample sample = new RagasSample(
                request.getId() != null ? request.getId() : "sample_1",
                request.getQuestion(),
                request.getGroundTruth()
        );

        RagasResult result = ragasEvalService.evaluateSample(request.getKbId(), sample, userId);
        return ResponseEntity.ok(result);
    }

    @Data
    public static class EvalRequest {
        private String kbId;
        private List<SampleInput> samples;

        @Data
        public static class SampleInput {
            private String id;
            private String question;
            private String groundTruth;
        }
    }

    @Data
    public static class SampleRequest {
        private String id;
        private String kbId;
        private String question;
        private String groundTruth;
    }
}
