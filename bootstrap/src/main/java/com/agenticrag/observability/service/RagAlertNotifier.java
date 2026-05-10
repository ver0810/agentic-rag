package com.agenticrag.observability.service;

import com.agenticrag.observability.dto.RagAlertDispatchResultDTO;
import com.agenticrag.observability.dto.RagObservabilityAlertDTO;
import com.agenticrag.observability.dto.RagObservabilityMetricsDTO;

import java.util.List;

public interface RagAlertNotifier {

    RagAlertDispatchResultDTO notify(String userId,
                                     RagObservabilityMetricsDTO metrics,
                                     List<RagObservabilityAlertDTO> alerts,
                                     boolean forceDispatch);
}
