package com.agenticrag.controller;

import com.agenticrag.rageval.dto.RagEvalDatasetSummaryDTO;
import com.agenticrag.rageval.dto.RagEvalCompareDTO;
import com.agenticrag.rageval.dto.RagEvalReportDTO;
import com.agenticrag.rageval.dto.RagEvalRunSummaryDTO;
import com.agenticrag.rageval.service.RagEvaluationService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag/evals")
public class RagEvaluationController {

    private final RagEvaluationService ragEvaluationService;

    public RagEvaluationController(RagEvaluationService ragEvaluationService) {
        this.ragEvaluationService = ragEvaluationService;
    }

    @GetMapping("/datasets")
    public ResponseEntity<List<RagEvalDatasetSummaryDTO>> listDatasets() {
        return ResponseEntity.ok(ragEvaluationService.listDatasets());
    }

    @PostMapping("/run")
    public ResponseEntity<RagEvalReportDTO> run(@RequestBody RagEvalRunRequest request,
                                                @CurrentUser String userId) {
        return ResponseEntity.ok(ragEvaluationService.runDataset(
                request.dataset(),
                userId,
                request.kbIdOverride(),
                request.topKOverride()));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<RagEvalRunSummaryDTO>> listRuns(@CurrentUser String userId,
                                                               @RequestParam(name = "dataset", required = false) String dataset,
                                                               @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ragEvaluationService.listRuns(userId, dataset, limit));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RagEvalReportDTO> getRun(@CurrentUser String userId,
                                                   @PathVariable String runId) {
        return ResponseEntity.ok(ragEvaluationService.getRun(userId, runId));
    }

    @GetMapping("/compare")
    public ResponseEntity<RagEvalCompareDTO> compare(@CurrentUser String userId,
                                                     @RequestParam(name = "baseRunId") String baseRunId,
                                                     @RequestParam(name = "targetRunId") String targetRunId) {
        return ResponseEntity.ok(ragEvaluationService.compareRuns(userId, baseRunId, targetRunId));
    }

    public record RagEvalRunRequest(String dataset, String kbIdOverride, Integer topKOverride) {
    }
}
