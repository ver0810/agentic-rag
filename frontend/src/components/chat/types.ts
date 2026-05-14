import type { ReactNode } from 'react';
import type { KnowledgeBase } from '../../api/knowledge';

export interface AiSettings {
  provider: string;
  providerName: string;
  chatModel: string;
  embeddingModel: string;
  hasApiKey: boolean;
  verified: boolean;
}

export interface ConfiguredModelOption {
  provider: string;
  providerName: string;
  chatModel: string;
  embeddingModel: string;
  active: boolean;
  verified: boolean;
  recommended: boolean;
}

export interface ChatSessionItem {
  chatTitle: string;
  sessionId: string;
}

export interface ChatUser {
  username?: string;
}

export type SidebarTab = 'chats' | 'knowledge' | 'observability';
export type ObservabilityTab = 'trace' | 'eval';

export interface ChatSuggestion {
  text: string;
  icon: ReactNode;
}

export interface SceneOption {
  selectedKbId: string | null;
  knowledgeBases: KnowledgeBase[];
}
