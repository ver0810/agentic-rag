import axios from 'axios';

export interface RagTraceNode {
  nodeId: string;
  nodeType: string;
  nodeName: string;
  status: string;
  errorMessage?: string;
  durationMs: number;
  extraData?: string;
  startTime: string;
  endTime?: string;
}

export interface RagTraceRun {
  traceId: string;
  traceName: string;
  entryMethod: string;
  conversationId: string;
  userId: string;
  status: string;
  errorMessage?: string;
  durationMs: number;
  extraData?: string;
  startTime: string;
  endTime?: string;
  nodes: RagTraceNode[];
}

export const TraceAPI = {
  list: (limit: number = 20) => axios.get<RagTraceRun[]>(`/api/rag/traces?limit=${limit}`),
  detail: (traceId: string) => axios.get<RagTraceRun>(`/api/rag/traces/${traceId}`),
};
