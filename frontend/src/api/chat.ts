import axios from 'axios';

export interface Message {
  role: 'user' | 'assistant';
  content: string;
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
    axios.get<any[]>(`/chat/messages?conversationId=${conversationId}`),
  
  newSession: () => axios.post<ChatSessionResponse>('/chat/session/new'),
  
  renameSession: (conversationId: string, title: string) => 
    axios.put(`/chat/session/${conversationId}/title?title=${encodeURIComponent(title)}`),
  
  deleteSession: (conversationId: string) => 
    axios.delete(`/chat/session/${conversationId}`),

  streamChat: async (
    message: string, 
    conversationId: string, 
    scene?: string, 
    kbId?: string,
    onUpdate?: (text: string) => void
  ) => {
    const url = new URL('/chat/stream', window.location.origin);
    url.searchParams.append('message', message);
    url.searchParams.append('conversationId', conversationId);
    if (scene) url.searchParams.append('scene', scene);
    if (kbId) url.searchParams.append('kbId', kbId);

    const response = await fetch(url.toString(), {
      method: 'POST',
      headers: getAuthHeaders()
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

    while (!done) {
      const { value, done: doneReading } = await reader.read();
      done = doneReading;
      const chunkValue = decoder.decode(value);
      accumulatedContent += chunkValue;
      if (onUpdate) onUpdate(accumulatedContent);
    }

    return accumulatedContent;
  }
};
