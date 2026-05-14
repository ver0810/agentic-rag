import axios from 'axios';

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  collectionName: string;
  createdBy: string;
  createTime: string;
}

export interface KnowledgeDocument {
  id: string;
  kbId: string;
  docName?: string;
  fileName?: string;
  fileType: string;
  fileSize: number;
  fileUrl?: string;
  storagePath?: string;
  status: string;
  createdBy: string;
  createTime: string;
  chunkCount?: number;
}

export interface IngestionTaskNode {
  nodeId: string;
  nodeType: string;
  nodeOrder: number;
  status: string;
  durationMs: number;
  message?: string;
  errorMessage?: string;
  outputJson?: string;
}

export interface IngestionTask {
  id: string;
  sourceType: string;
  sourceLocation: string;
  sourceFileName: string;
  status: string;
  chunkCount?: number;
  errorMessage?: string;
  metadataJson?: string;
  retryCount?: number;
  maxRetries?: number;
  nextRunAt?: string;
  leaseOwner?: string;
  leaseUntil?: string;
  startedAt?: string;
  completedAt?: string;
  createTime: string;
  updateTime: string;
  nodes: IngestionTaskNode[];
}

export interface RagCitation {
  chunkId: string;
  docId: string;
  docName: string;
  chunkIndex?: number;
  headingPath?: string;
  segmentType?: string;
  headingLevel?: number;
  score: number;
  snippet: string;
}

export interface RagRetrievedChunk {
  chunkId: string;
  docId: string;
  docName: string;
  chunkIndex?: number;
  headingPath?: string;
  segmentType?: string;
  headingLevel?: number;
  score: number;
  content: string;
}

export interface RagQueryResult {
  answer: string;
  traceId: string;
  rewrittenQuery: string;
  citations: RagCitation[];
  retrievedChunks: RagRetrievedChunk[];
}

export const KnowledgeAPI = {
  list: () => axios.get<KnowledgeBase[]>('/api/knowledge-base'),

  create: (name: string, description: string) =>
    axios.post<KnowledgeBase>('/api/knowledge-base', { name, description }),

  getById: (id: string) => axios.get<KnowledgeBase>(`/api/knowledge-base/${id}`),

  delete: (id: string) => axios.delete(`/api/knowledge-base/${id}`),

  listDocuments: (kbId: string) =>
    axios.get<KnowledgeDocument[]>(`/api/knowledge-base/${kbId}/documents`),

  uploadDocument: (kbId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post<KnowledgeDocument>(`/api/knowledge-base/${kbId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },

  deleteDocument: (docId: string) =>
    axios.delete(`/api/knowledge-base/documents/${docId}`),

  processDocument: (docId: string) =>
    axios.post<{ message: string; taskId: string }>(`/api/knowledge-base/documents/${docId}/process`),

  listDocumentTasks: (docId: string) =>
    axios.get<IngestionTask[]>(`/api/knowledge-base/documents/${docId}/tasks`),

  getTask: (taskId: string) =>
    axios.get<IngestionTask>(`/api/ingestion/tasks/${taskId}`),

  retryTask: (taskId: string) =>
    axios.post<{ message: string; taskId: string }>(`/api/ingestion/tasks/${taskId}/retry`),

  queryRag: (query: string, kbId: string, topK = 5) =>
    axios.post<RagQueryResult>('/api/rag/query', { query, kbId, topK })
};
