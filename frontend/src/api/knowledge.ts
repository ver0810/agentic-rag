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
    axios.post<{message: string}>(`/api/knowledge-base/documents/${docId}/process`)
};
