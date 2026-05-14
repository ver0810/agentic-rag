import { lazy, Suspense, useEffect, useRef, useState, type FormEvent } from 'react';
import axios from 'axios';
import { BrowserRouter as Router, Route, Routes, useNavigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import { ChatAPI, type Message, type MessageMetadata } from './api/chat';
import { KnowledgeAPI, type KnowledgeBase } from './api/knowledge';
import ChatSidebar from './components/chat/ChatSidebar';
import ChatHeader from './components/chat/ChatHeader';
import ChatContent from './components/chat/ChatContent';
import ChatComposer from './components/chat/ChatComposer';
import type {
  AiSettings,
  ChatSessionItem,
  ConfiguredModelOption,
  ObservabilityTab,
  SidebarTab,
} from './components/chat/types';
import { clearAuth } from './utils/auth';

const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const AddModelModal = lazy(() => import('./components/AddModelModal'));
const AddKbModal = lazy(() => import('./components/AddKbModal'));
const KnowledgeBaseView = lazy(() => import('./components/KnowledgeBaseView'));
const ObservabilityView = lazy(() => import('./components/ObservabilityView'));
const EvalView = lazy(() => import('./components/EvalView'));

function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showModelMenu, setShowModelMenu] = useState(false);
  const [showSceneMenu, setShowSceneMenu] = useState(false);
  const [modelSearchQuery, setModelSearchQuery] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [showAddKbModal, setShowAddKbModal] = useState(false);
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null);
  const [configuredModels, setConfiguredModels] = useState<ConfiguredModelOption[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [recentChats, setRecentChats] = useState<ChatSessionItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [activeMenuSessionId, setActiveMenuSessionId] = useState<string | null>(null);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editingTitle, setEditingTitle] = useState('');
  const [sidebarTab, setSidebarTab] = useState<SidebarTab>('chats');
  const [obsTab, setObsTab] = useState<ObservabilityTab>('trace');
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [activeKb, setActiveKb] = useState<KnowledgeBase | null>(null);
  const [selectedKbId, setSelectedKbId] = useState<string | null>(null);
  const [focusedTraceId, setFocusedTraceId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const userStr = localStorage.getItem('user');
  const user = userStr ? JSON.parse(userStr) : null;

  const parseMessageMetadata = (metadataJson?: string) => {
    if (!metadataJson) {
      return {};
    }
    try {
      return JSON.parse(metadataJson) as MessageMetadata;
    } catch {
      return {};
    }
  };

  useEffect(() => {
    if (!user) {
      return;
    }
    void fetchBootstrapData();
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const fetchBootstrapData = async () => {
    await Promise.all([
      fetchAiSettings(),
      fetchConfiguredModels(),
      fetchSessions(),
      fetchKnowledgeBases(),
    ]);
  };

  const fetchKnowledgeBases = async () => {
    try {
      const response = await KnowledgeAPI.list();
      setKnowledgeBases(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      console.error('Failed to fetch knowledge bases', err);
      setKnowledgeBases([]);
    }
  };

  const fetchAiSettings = async () => {
    try {
      const response = await axios.get('/user/ai-settings');
      setAiSettings(response.data);
    } catch (err) {
      console.error('Failed to fetch AI settings', err);
    }
  };

  const fetchConfiguredModels = async () => {
    try {
      const response = await axios.get('/user/ai-settings/options');
      setConfiguredModels(response.data);
    } catch (err) {
      console.error('Failed to fetch configured AI models', err);
    }
  };

  const fetchSessions = async () => {
    try {
      const response = await ChatAPI.getSessions();
      setRecentChats(
        response.data.map((item) => ({
          sessionId: item.conversationId,
          chatTitle: item.title,
        })),
      );
    } catch (err) {
      console.error('Failed to fetch sessions', err);
      setError('Failed to load sessions');
    }
  };

  const fetchMessages = async (sessionId: string) => {
    setIsLoadingMessages(true);
    setError(null);
    try {
      const response = await ChatAPI.getMessages(sessionId);
      setMessages(
        response.data.map((msg) => {
          const metadata = parseMessageMetadata(msg.metadataJson);
          return {
            role: msg.role,
            content: msg.content,
            sourceType: metadata.sourceType,
            scene: metadata.scene,
            kbId: metadata.kbId,
            traceId: metadata.traceId,
            rewrittenQuery: metadata.rewrittenQuery,
            citations: metadata.citations,
            retrievedChunks: metadata.retrievedChunks,
          };
        }) as Message[],
      );
    } catch (err) {
      console.error('Failed to fetch messages', err);
      setError('Failed to load messages');
    } finally {
      setIsLoadingMessages(false);
    }
  };

  const handleCreateSession = async () => {
    try {
      const response = await ChatAPI.newSession();
      setCurrentSessionId(response.data.sessionId);
      setMessages([]);
      setInput('');
      setActiveKb(null);
      setSidebarTab('chats');
      await fetchSessions();
    } catch (err) {
      console.error('Failed to create new session', err);
      setError('Failed to create new session');
    }
  };

  const handleSwitchSession = (sessionId: string) => {
    if (sessionId === currentSessionId) {
      return;
    }
    setActiveKb(null);
    setSidebarTab('chats');
    setCurrentSessionId(sessionId);
    void fetchMessages(sessionId);
  };

  const handleStartRename = (sessionId: string, currentTitle: string) => {
    setEditingSessionId(sessionId);
    setEditingTitle(currentTitle);
    setActiveMenuSessionId(null);
  };

  const handleInlineRename = async (sessionId: string) => {
    const currentTitle = recentChats.find((chat) => chat.sessionId === sessionId)?.chatTitle;
    const nextTitle = editingTitle.trim();

    if (nextTitle && nextTitle !== currentTitle) {
      try {
        await ChatAPI.renameSession(sessionId, nextTitle);
        await fetchSessions();
      } catch (err) {
        console.error('Failed to rename session', err);
        setError('Failed to rename session');
      }
    }

    setEditingSessionId(null);
  };

  const handleDeleteSession = async (sessionId: string) => {
    if (!window.confirm('Are you sure you want to delete this chat?')) {
      return;
    }
    try {
      await ChatAPI.deleteSession(sessionId);
      if (currentSessionId === sessionId) {
        setCurrentSessionId(null);
        setMessages([]);
      }
      await fetchSessions();
    } catch (err) {
      console.error('Failed to delete session', err);
      setError('Failed to delete session');
    } finally {
      setActiveMenuSessionId(null);
    }
  };

  const handleDeleteKb = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this Knowledge Base?')) {
      return;
    }
    try {
      await KnowledgeAPI.delete(id);
      if (activeKb?.id === id) {
        setActiveKb(null);
      }
      if (selectedKbId === id) {
        setSelectedKbId(null);
      }
      await fetchKnowledgeBases();
    } catch (err) {
      console.error('Failed to delete KB', err);
      setError('Failed to delete Knowledge Base');
    }
  };

  const handleLogout = () => {
    const refreshToken = localStorage.getItem('refreshToken');
    axios
      .post('/user/logout', { refreshToken })
      .catch(() => undefined)
      .finally(() => {
        clearAuth();
        navigate('/login');
      });
  };

  const handleSwitchModel = async (option: ConfiguredModelOption) => {
    try {
      await axios.post('/user/ai-settings/switch', {
        provider: option.provider,
        chatModel: option.chatModel,
        embeddingModel: option.embeddingModel,
      });
      setShowModelMenu(false);
      setModelSearchQuery('');
      await Promise.all([fetchAiSettings(), fetchConfiguredModels()]);
    } catch (err) {
      console.error('Failed to switch AI model', err);
    }
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!input.trim() || isLoading) {
      return;
    }

    const nextInput = input;
    setMessages((prev) => [
      ...prev,
      {
        role: 'user',
        content: nextInput,
        sourceType: selectedKbId ? 'rag' : 'chat',
        scene: selectedKbId ? 'rag_qa' : undefined,
        kbId: selectedKbId ?? undefined,
      },
      { role: 'assistant', content: '' },
    ]);
    setInput('');
    setIsLoading(true);

    try {
      let sessionId = currentSessionId;
      let isNewSession = false;

      if (!sessionId) {
        const response = await ChatAPI.newSession();
        sessionId = response.data.sessionId;
        setCurrentSessionId(sessionId);
        isNewSession = true;
        await fetchSessions();
      }

      if (selectedKbId) {
        const response = await ChatAPI.queryChat(nextInput, sessionId, 'rag_qa', selectedKbId);
        setMessages((prev) => {
          const next = [...prev];
          next[next.length - 1] = {
            ...next[next.length - 1],
            content: response.data.answer,
            sourceType: response.data.sourceType,
            scene: response.data.scene,
            kbId: response.data.kbId,
            traceId: response.data.traceId,
            rewrittenQuery: response.data.rewrittenQuery,
            citations: response.data.citations,
            retrievedChunks: response.data.retrievedChunks,
          };
          return next;
        });
      } else {
        await ChatAPI.streamChat(
          nextInput,
          sessionId,
          undefined,
          undefined,
          (accumulatedContent) => {
            setMessages((prev) => {
              const next = [...prev];
              next[next.length - 1] = {
                ...next[next.length - 1],
                content: accumulatedContent,
              };
              return next;
            });
          },
        );
      }

      if (isNewSession) {
        await fetchSessions();
      }
    } catch (err) {
      const error = err as Error;
      console.error('Error:', error);
      if (error.message === 'Unauthorized') {
        clearAuth();
        navigate('/login');
        return;
      }
      setMessages((prev) => {
        const next = [...prev];
        next[next.length - 1] = {
          ...next[next.length - 1],
          content: error.message || 'Error: Failed to get response.',
        };
        return next;
      });
    } finally {
      setIsLoading(false);
    }
  };

  const openSettingsModal = () => {
    setShowAddModal(true);
    setShowUserMenu(false);
    setShowModelMenu(false);
  };

  const selectSidebarTab = (tab: SidebarTab) => {
    setSidebarTab(tab);
    if (tab === 'observability') {
      setActiveKb(null);
    } else {
      setFocusedTraceId(null);
    }
  };

  const selectKnowledgeBase = (kb: KnowledgeBase) => {
    setSidebarTab('knowledge');
    setActiveKb(kb);
  };

  const selectSceneKb = (kbId: string | null) => {
    setSelectedKbId(kbId);
    setShowSceneMenu(false);
  };

  const handleTraceClick = (traceId: string) => {
    setFocusedTraceId(traceId);
    setActiveKb(null);
    setSidebarTab('observability');
    setObsTab('trace');
  };

  return (
    <div className="flex h-screen bg-white text-[#171717] font-sans selection:bg-[#E5E5E5] overflow-hidden">
      <Suspense fallback={null}>
        <AddModelModal
          isOpen={showAddModal}
          onClose={() => setShowAddModal(false)}
          onSuccess={() => {
            void Promise.all([fetchAiSettings(), fetchConfiguredModels()]);
          }}
        />
        <AddKbModal
          isOpen={showAddKbModal}
          onClose={() => setShowAddKbModal(false)}
          onSuccess={() => {
            void fetchKnowledgeBases();
          }}
        />
      </Suspense>

      {error ? (
        <div className="fixed top-4 right-4 z-50 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2">
          <span className="text-sm">{error}</span>
          <button onClick={() => setError(null)} className="text-red-500 hover:text-red-700">
            <span className="sr-only">Close</span>
            x
          </button>
        </div>
      ) : null}

      <ChatSidebar
        sidebarOpen={sidebarOpen}
        sidebarTab={sidebarTab}
        obsTab={obsTab}
        recentChats={recentChats}
        currentSessionId={currentSessionId}
        activeKb={activeKb}
        knowledgeBases={knowledgeBases}
        editingSessionId={editingSessionId}
        editingTitle={editingTitle}
        activeMenuSessionId={activeMenuSessionId}
        showUserMenu={showUserMenu}
        user={user}
        onToggleSidebarTab={selectSidebarTab}
        onSelectObsTab={setObsTab}
        onCreateSession={handleCreateSession}
        onSwitchSession={handleSwitchSession}
        onStartRename={handleStartRename}
        onCommitRename={(sessionId) => {
          void handleInlineRename(sessionId);
        }}
        onEditTitleChange={setEditingTitle}
        onDeleteSession={(sessionId) => {
          void handleDeleteSession(sessionId);
        }}
        onToggleSessionMenu={setActiveMenuSessionId}
        onCreateKb={() => setShowAddKbModal(true)}
        onSelectKb={selectKnowledgeBase}
        onOpenSettings={openSettingsModal}
        onToggleUserMenu={() => setShowUserMenu((prev) => !prev)}
        onCloseUserMenu={() => setShowUserMenu(false)}
        onLogout={handleLogout}
      />

      <main className="flex-1 flex flex-col relative h-full min-w-0">
        <ChatHeader
          sidebarOpen={sidebarOpen}
          showModelMenu={showModelMenu}
          showSceneMenu={showSceneMenu}
          modelSearchQuery={modelSearchQuery}
          aiSettings={aiSettings}
          configuredModels={configuredModels}
          selectedKbId={selectedKbId}
          knowledgeBases={knowledgeBases}
          onToggleSidebar={setSidebarOpen}
          onToggleModelMenu={() => {
            if (showModelMenu) {
              setModelSearchQuery('');
            }
            setShowModelMenu((prev) => !prev);
            setShowSceneMenu(false);
          }}
          onCloseModelMenu={() => {
            setModelSearchQuery('');
            setShowModelMenu(false);
          }}
          onToggleSceneMenu={() => {
            setShowSceneMenu((prev) => !prev);
            setShowModelMenu(false);
          }}
          onCloseSceneMenu={() => setShowSceneMenu(false)}
          onModelSearchChange={setModelSearchQuery}
          onSwitchModel={(option) => {
            void handleSwitchModel(option);
          }}
          onOpenSettings={openSettingsModal}
          onSelectSceneKb={selectSceneKb}
        />

        <div className="flex-1 overflow-y-auto scrollbar-hide pt-4">
          <Suspense fallback={<ContentFallback />}>
            {sidebarTab === 'observability' ? (
              obsTab === 'trace' ? <ObservabilityView focusTraceId={focusedTraceId} /> : <EvalView />
            ) : activeKb ? (
              <KnowledgeBaseView kb={activeKb} onDelete={() => void handleDeleteKb(activeKb.id)} />
            ) : (
              <ChatContent
                isLoadingMessages={isLoadingMessages}
                messages={messages}
                isLoading={isLoading}
                messagesEndRef={messagesEndRef}
                knowledgeBases={knowledgeBases}
                onTraceClick={handleTraceClick}
                onSuggestionClick={setInput}
              />
            )}
          </Suspense>
        </div>

        {sidebarTab !== 'observability' && !activeKb ? (
          <ChatComposer input={input} isLoading={isLoading} onInputChange={setInput} onSubmit={handleSubmit} />
        ) : null}
      </main>
    </div>
  );
}

function App() {
  return (
    <Router>
      <Suspense fallback={<PageFallback />}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <ChatInterface />
              </ProtectedRoute>
            }
          />
        </Routes>
      </Suspense>
    </Router>
  );
}

function PageFallback() {
  return <div className="min-h-screen bg-white" />;
}

function ContentFallback() {
  return (
    <div className="h-full flex items-center justify-center">
      <div className="w-8 h-8 border-2 border-gray-200 border-t-black rounded-full animate-spin" />
    </div>
  );
}

export default App;
