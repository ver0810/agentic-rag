import type { RagCitation, RagRetrievedChunk } from './knowledge';
import axios from 'axios';

export interface MessageMetadata {
  sourceType?: 'rag' | 'chat';
  scene?: string;
  kbId?: string;
  traceId?: string;
  rewrittenQuery?: string;
  citations?: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  verification?: {
    faithful: boolean;
    score: number;
    reason: string;
  };
}

export interface Message {
  role: 'user' | 'assistant';
  content: string;
  sourceType?: 'rag' | 'chat';
  scene?: string;
  kbId?: string;
  traceId?: string;
  rewrittenQuery?: string;
  citations?: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  verification?: {
    faithful: boolean;
    score: number;
    reason: string;
  };
}

export interface ChatMessageResponse {
  conversationId: string;
  userId: string;
  role: 'user' | 'assistant';
  content: string;
  metadataJson?: string;
}

export interface Conversation {
  conversationId: string;
  title: string;
  lastTime: string;
}

export interface ChatSessionResponse {
  sessionId: string;
  message: string;
}

export interface ChatResult {
  answer: string;
  sourceType: 'rag' | 'chat';
  scene?: string;
  kbId?: string;
  traceId?: string;
  rewrittenQuery?: string;
  citations: RagCitation[];
  retrievedChunks: RagRetrievedChunk[];
}

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');
  const userId = localStorage.getItem('userId');
  return {
    'Authorization': `Bearer ${token || ''}`,
    'X-User-Id': userId || ''
  };
};

export const ChatAPI = {
  getSessions: () => axios.get<any[]>('/chat/sessions'),
  
  getMessages: (conversationId: string) => 
    axios.get<ChatMessageResponse[]>(`/chat/messages?conversationId=${conversationId}`),
  
  newSession: () => axios.post<ChatSessionResponse>('/chat/session/new'),

  queryChat: (message: string, conversationId: string, scene?: string, kbId?: string) =>
    axios.post<ChatResult>('/chat/query', { message, conversationId, scene, kbId }),
  
  renameSession: (conversationId: string, title: string) => 
    axios.put(`/chat/session/${conversationId}/title?title=${encodeURIComponent(title)}`),
  
  deleteSession: (conversationId: string) => 
    axios.delete(`/chat/session/${conversationId}`),

  streamChat: async (
    message: string, 
    conversationId: string, 
    scene?: string, 
    kbId?: string,
    onUpdate?: (text: string, metadata?: ChatResult, verification?: any) => void,
    signal?: AbortSignal
  ) => {
    const url = new URL('/chat/stream', window.location.origin);
    url.searchParams.append('message', message);
    url.searchParams.append('conversationId', conversationId);
    if (scene) url.searchParams.append('scene', scene);
    if (kbId) url.searchParams.append('kbId', kbId);

    const response = await fetch(url.toString(), {
      method: 'POST',
      headers: getAuthHeaders(),
      signal
    });

    if (response.status === 401) {
      throw new Error('Unauthorized');
    }

    if (!response.ok) {
      const errorText = await response.text().catch(() => '');
      throw new Error(`Failed to fetch (${response.status}): ${errorText}`);
    }

    const reader = response.body?.getReader();
    if (!reader) throw new Error('No reader');

    const decoder = new TextDecoder();
    let done = false;
    let accumulatedContent = '';
    let currentMetadata: ChatResult | undefined;
    let buffer = '';

    while (!done) {
      const { value, done: doneReading } = await reader.read();
      done = doneReading;
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() || '';

      for (const event of events) {
        let dataLines: string[] = [];
        for (const line of event.split('\n')) {
          if (line.startsWith('data:')) {
            dataLines.push(line.substring(5).trim());
          }
        }
        if (dataLines.length === 0) continue;

        const dataStr = dataLines.join('\n');
        if (!dataStr) continue;

        try {
          const parsed = JSON.parse(dataStr);
          if (parsed.type === 'metadata') {
            currentMetadata = parsed.data;
            if (onUpdate) onUpdate(accumulatedContent, currentMetadata);
          } else if (parsed.type === 'chunk') {
            accumulatedContent += parsed.data;
            if (onUpdate) onUpdate(accumulatedContent, currentMetadata);
          } else if (parsed.type === 'verification') {
            if (onUpdate) onUpdate(accumulatedContent, currentMetadata, parsed.data);
          } else if (parsed.type === 'error') {
            throw new Error(parsed.data);
          } else if (parsed.type === 'done') {
            done = true;
          }
        } catch (e) {
          if (e instanceof Error && e.message !== 'No reader' && !e.message.startsWith('Failed to fetch')) {
            console.warn('Failed to parse SSE event:', e, dataStr);
          } else {
            throw e;
          }
        }
      }
    }

    return accumulatedContent;
  }
};
