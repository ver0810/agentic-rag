package com.agenticrag.rageval.service;

import com.agenticrag.rageval.dto.RagEvalDatasetSummaryDTO;
import com.agenticrag.rageval.dto.RagEvalCompareDTO;
import com.agenticrag.rageval.dto.RagEvalReportDTO;
import com.agenticrag.rageval.dto.RagEvalRunSummaryDTO;

import java.util.List;

public interface RagEvaluationService {

    List<RagEvalDatasetSummaryDTO> listDatasets();

    RagEvalReportDTO runDataset(String datasetName, String userId, String kbIdOverride, Integer topKOverride);

    List<RagEvalRunSummaryDTO> listRuns(String userId, String datasetName, Integer limit);

    RagEvalReportDTO getRun(String userId, String runId);

    RagEvalCompareDTO compareRuns(String userId, String baseRunId, String targetRunId);
}
