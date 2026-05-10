import axios from 'axios';

export const EvalAPI = {
  listDatasets: () => axios.get<any>('/api/rag/evals/datasets'),
  run: (dataset: string, kbIdOverride?: string, topKOverride?: number) => 
    axios.post<any>('/api/rag/evals/run', { dataset, kbIdOverride, topKOverride }),
  listRuns: (dataset?: string, limit?: number) => 
    axios.get<any[]>('/api/rag/evals/runs', { params: { dataset, limit } }),
  getRun: (runId: string) => axios.get<any>(`/api/rag/evals/runs/${runId}`),
  compare: (baseRunId: string, targetRunId: string) => 
    axios.get<any>('/api/rag/evals/compare', { params: { baseRunId, targetRunId } }),
};
