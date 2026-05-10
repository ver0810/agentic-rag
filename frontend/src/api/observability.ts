import axios from 'axios';

export interface RagObservabilityMetrics {
  windowStart: string;
  windowEnd: string;
  totalQueries: number;
  successfulQueries: number;
  failedQueries: number;
  emptyRetrievalRate: number;
  refusalRate: number;
  averageResponseTimeMs: number;
  modelErrorRate: number;
  totalIngestionTasks: number;
  terminalIngestionTasks: number;
  documentProcessingSuccessRate: number;
  ingestionRetryRate: number;
  averageIngestionDurationMs: number;
  estimatedChatInputTokens: number;
  estimatedChatOutputTokens: number;
  estimatedQueryEmbeddingTokens: number;
  estimatedIngestionEmbeddingTokens: number;
  estimatedTotalTokens: number;
  estimatedChatCost: number;
  estimatedEmbeddingCost: number;
  estimatedTotalCost: number;
}

export interface RagObservabilityAlert {
  code: string;
  level: string;
  status: 'ACTIVE' | 'OK' | 'INSUFFICIENT_DATA';
  message: string;
  currentValue?: number;
  baselineValue?: number;
  thresholdValue?: number;
  details?: Record<string, unknown>;
}

export interface RagObservabilitySummary {
  metrics: RagObservabilityMetrics;
  alerts: RagObservabilityAlert[];
}

export interface RagAlertDispatchResult {
  notificationsEnabled: boolean;
  dispatched: boolean;
  activeAlertCount: number;
  dispatchedAt?: string;
  destination?: string;
  alerts: RagObservabilityAlert[];
}

export const ObservabilityAPI = {
  summary: (hours = 24, baselineHours = 24) =>
    axios.get<RagObservabilitySummary>(`/api/rag/observability/summary?hours=${hours}&baselineHours=${baselineHours}`),
  dispatchAlerts: (hours = 24, baselineHours = 24, force = false) =>
    axios.post<RagAlertDispatchResult>(`/api/rag/observability/alerts/dispatch?hours=${hours}&baselineHours=${baselineHours}&force=${force}`),
};
