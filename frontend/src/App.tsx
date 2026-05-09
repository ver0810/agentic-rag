import { useState, useRef, useEffect } from 'react'
import axios from 'axios'
import {
  BrowserRouter as Router,
  Routes,
  Route,
  useNavigate,
  Link
} from 'react-router-dom'
import {
  Send,
  Plus,
  User,
  Bot,
  Copy,
  ThumbsUp,
  Menu,
  ChevronLeft,
  SquareTerminal,
  Search,
  Mail,
  Bug,
  Map,
  LogOut,
  LogIn,
  UserPlus,
  Settings,
  Sparkles,
  ChevronDown,
  MoreHorizontal,
  Pencil,
  Trash2
} from 'lucide-react'
import Login from './pages/Login'
import Register from './pages/Register'
import ProtectedRoute from './components/ProtectedRoute'
import AddModelModal from './components/AddModelModal'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

interface AiSettings {
  provider: string
  providerName: string
  chatModel: string
  embeddingModel: string
  hasApiKey: boolean
  verified: boolean
}

interface ConfiguredModelOption {
  provider: string
  providerName: string
  chatModel: string
  embeddingModel: string
  active: boolean
  verified: boolean
  recommended: boolean
}

interface Chat {
  chatTitle: string
  sessionId: string
}

function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const [showModelMenu, setShowModelMenu] = useState(false)
  const [modelSearchQuery, setModelSearchQuery] = useState('')
  const [showAddModal, setShowAddModal] = useState(false)
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null)
  const [configuredModels, setConfiguredModels] = useState<ConfiguredModelOption[]>([])
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [recentChats, setRecentChats] = useState<Chat[]>([])
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const [activeMenuSessionId, setActiveMenuSessionId] = useState<string | null>(null)
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null)
  const [editingTitle, setEditingTitle] = useState('')
  const navigate = useNavigate();

  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null

  useEffect(() => {
    if (user) {
      fetchAiSettings()
      fetchConfiguredModels()
      fetchSessions()
    }
  }, [])

  const fetchAiSettings = async () => {
    try {
      const response = await axios.get('/user/ai-settings')
      setAiSettings(response.data)
    } catch (err) {
      console.error('Failed to fetch AI settings', err)
    }
  }

  const fetchConfiguredModels = async () => {
    try {
      const response = await axios.get('/user/ai-settings/options')
      setConfiguredModels(response.data)
    } catch (err) {
      console.error('Failed to fetch configured AI models', err)
    }
  }

  const fetchSessions = async () => {
    try {
      const response = await axios.get('/chat/sessions')
      const chats = response.data.map((item: any) => ({
        sessionId: item.conversationId,
        chatTitle: item.title
      }))
      setRecentChats(chats)
    } catch (err) {
      console.error('Failed to fetch sessions', err)
    }
  }

  const fetchMessages = async (sessionId: string) => {
    try {
      const response = await axios.get(`/chat/messages?conversationId=${sessionId}`)
      const history = response.data.map((msg: any) => ({
        role: msg.role,
        content: msg.content
      }))
      setMessages(history)
    } catch (err) {
      console.error('Failed to fetch messages', err)
    }
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])


  const handleCreateSession = async () => {
    try {
      const response = await axios.post("/chat/session/new");
      const sessionId = response.data.sessionId;

      setCurrentSessionId(sessionId);

      setMessages([]);
      setInput('');
      fetchSessions();
      console.log("Create a new session successful");
    } catch (error) {
      console.error("Failed to create new session", error);
    }
  }

  const handleSwitchSession = (sessionId: string) => {
    setCurrentSessionId(sessionId)
    fetchMessages(sessionId)
  }

  const handleStartRename = (sessionId: string, currentTitle: string) => {
    setEditingSessionId(sessionId)
    setEditingTitle(currentTitle)
    setActiveMenuSessionId(null)
  }

  const handleInlineRename = async (sessionId: string) => {
    if (editingTitle.trim() && editingTitle !== recentChats.find(c => c.sessionId === sessionId)?.chatTitle) {
      try {
        await axios.put(`/chat/session/${sessionId}/title?title=${encodeURIComponent(editingTitle.trim())}`)
        fetchSessions()
      } catch (err) {
        console.error('Failed to rename session', err)
      }
    }
    setEditingSessionId(null)
  }

  const handleRenameSession = async (sessionId: string, currentTitle: string) => {
    handleStartRename(sessionId, currentTitle)
  }

  const handleDeleteSession = async (sessionId: string) => {
    console.log('Attempting to delete session:', sessionId);
    if (confirm('Are you sure you want to delete this chat?')) {
      try {
        const response = await axios.delete(`/chat/session/${sessionId}`)
        console.log('Delete response:', response.status);
        if (currentSessionId === sessionId) {
          setCurrentSessionId(null)
          setMessages([])
        }
        fetchSessions()
      } catch (err) {
        console.error('Failed to delete session', err)
      }
    }
    setActiveMenuSessionId(null)
  }
  const handleLogout = () => {
    const refreshToken = localStorage.getItem('refreshToken')
    axios.post('/user/logout', { refreshToken }).catch(() => undefined).finally(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
      localStorage.removeItem('userId')
      navigate('/login')
    })
  }

  const handleSwitchModel = async (option: ConfiguredModelOption) => {
    try {
      await axios.post('/user/ai-settings/switch', {
        provider: option.provider,
        chatModel: option.chatModel,
        embeddingModel: option.embeddingModel
      })
      setShowModelMenu(false)
      await Promise.all([fetchAiSettings(), fetchConfiguredModels()])
    } catch (err) {
      console.error('Failed to switch AI model', err)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || isLoading) return

    const userMessage: Message = { role: 'user', content: input }
    setMessages(prev => [...prev, userMessage])
    setInput('')
    setIsLoading(true)

    const assistantMessage: Message = { role: 'assistant', content: '' }
    setMessages(prev => [...prev, assistantMessage])

    try {
      const url = new URL('/chat/stream', window.location.origin)
      url.searchParams.append('message', input)

      let sessionId = currentSessionId;
      let isNewSession = false;
      if (!sessionId) {
        const res = await axios.post("/chat/session/new");
        sessionId = res.data.sessionId;
        setCurrentSessionId(sessionId);
        isNewSession = true;
        fetchSessions(); // Show "New Chat" in sidebar immediately
      }

      url.searchParams.append('conversationId', sessionId);

      const response = await fetch(url.toString(), {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
          'X-User-Id': user?.id || ''
        }
      })

      if (response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        localStorage.removeItem('userId')
        navigate('/login')
        throw new Error('Unauthorized')
      }

      if (!response.ok) {
        const errorText = await response.text().catch(() => '')
        throw new Error(`Failed to fetch (${response.status}): ${errorText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('No reader')

      const decoder = new TextDecoder()
      let done = false
      let accumulatedContent = ''

      while (!done) {
        const { value, done: doneReading } = await reader.read()
        done = doneReading
        const chunkValue = decoder.decode(value)
        accumulatedContent += chunkValue

        setMessages(prev => {
          const newMessages = [...prev]
          newMessages[newMessages.length - 1] = {
            ...newMessages[newMessages.length - 1],
            content: accumulatedContent
          }
          return newMessages
        })
      }

      // If it was the first message of a new session, refresh sessions to get the auto-generated title
      if (isNewSession) {
        fetchSessions();
      }
    } catch (error: any) {
      console.error('Error:', error)
      setMessages(prev => {
        const newMessages = [...prev]
        newMessages[newMessages.length - 1] = {
          ...newMessages[newMessages.length - 1],
          content: error.message || 'Error: Failed to get response.'
        }
        return newMessages
      })
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex h-screen bg-white text-[#171717] font-sans selection:bg-[#E5E5E5] overflow-hidden">
      <AddModelModal
        isOpen={showAddModal}
        onClose={() => setShowAddModal(false)}
        onSuccess={() => {
          fetchAiSettings()
          fetchConfiguredModels()
        }}
      />

      <aside
        className={`${
          sidebarOpen ? 'w-[260px]' : 'w-0'
        } transition-all duration-300 ease-in-out flex flex-col bg-[#f9f9f9] border-r border-[#e5e5e5] overflow-hidden`}
      >
        <div className="p-3 flex flex-col h-full w-[260px]">
          <button className="flex items-center justify-between w-full p-2 text-sm font-medium hover:bg-[#ececec] rounded-lg transition-colors group"
          onClick={handleCreateSession}
          >
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 bg-white border border-[#e5e5e5] rounded-full flex items-center justify-center shadow-sm">
                <Plus size={16} />
              </div>
              <span>New Chat</span>
            </div>
            <SquareTerminal size={16} className="text-gray-400 group-hover:text-black" />
          </button>

          <div className="flex-1 mt-4 overflow-y-auto space-y-1">
            <div className="px-2 py-1 text-[11px] font-semibold text-gray-500 uppercase tracking-wider flex items-center justify-between">
              <span>Recent</span>
            </div>
            {recentChats.length === 0 ? (
              <div className="px-3 py-2 text-sm text-gray-400 italic">No recent chats</div>
            ) : (
              recentChats.map((chat) => (
                <div key={chat.sessionId} className="group/item relative">
                  {editingSessionId === chat.sessionId ? (
                    <input
                      autoFocus
                      className="w-full text-left px-3 py-2 text-sm rounded-lg bg-white border border-gray-300 outline-none"
                      value={editingTitle}
                      onChange={(e) => setEditingTitle(e.target.value)}
                      onBlur={() => handleInlineRename(chat.sessionId)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') handleInlineRename(chat.sessionId)
                        if (e.key === 'Escape') setEditingSessionId(null)
                      }}
                    />
                  ) : (
                    <>
                      <button
                        onClick={() => handleSwitchSession(chat.sessionId)}
                        className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors truncate pr-10 ${
                          currentSessionId === chat.sessionId ? 'bg-[#ececec] font-medium' : 'hover:bg-[#ececec] text-gray-600'
                        }`}
                      >
                        {chat.chatTitle}
                      </button>
                      
                      <div className="absolute right-1 top-1 opacity-0 group-hover/item:opacity-100 transition-opacity">
                        <button 
                          onClick={(e) => {
                            e.stopPropagation();
                            setActiveMenuSessionId(activeMenuSessionId === chat.sessionId ? null : chat.sessionId);
                          }}
                          className="p-1.5 hover:bg-gray-200 rounded-full text-gray-500 hover:text-black transition-colors"
                        >
                          <MoreHorizontal size={14} />
                        </button>
                        
                        {activeMenuSessionId === chat.sessionId && (
                          <>
                            <div 
                              className="fixed inset-0 z-40" 
                              onClick={() => setActiveMenuSessionId(null)} 
                            />
                            <div className="absolute right-0 top-full mt-1 w-32 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-50 animate-in fade-in zoom-in-95 duration-100">
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleRenameSession(chat.sessionId, chat.chatTitle);
                                }}
                                className="flex items-center gap-2 w-full px-3 py-1.5 text-xs hover:bg-gray-50 text-gray-700 transition-colors"
                              >
                                <Pencil size={12} />
                                <span>Rename</span>
                              </button>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleDeleteSession(chat.sessionId);
                                }}
                                className="flex items-center gap-2 w-full px-3 py-1.5 text-xs hover:bg-red-50 text-red-600 transition-colors"
                              >
                                <Trash2 size={12} />
                                <span>Delete</span>
                              </button>
                            </div>
                          </>
                        )}
                      </div>
                    </>
                  )}
                </div>
              ))
            )}
          </div>

          <div className="pt-4 mt-auto border-t border-[#e5e5e5] relative">
            {user ? (
              <div className="relative group/menu">
                <button
                  onClick={() => setShowUserMenu(!showUserMenu)}
                  className="flex items-center gap-3 w-full p-2 text-sm hover:bg-[#ececec] rounded-lg transition-colors"
                >
                  <div className="w-7 h-7 bg-black rounded-full flex items-center justify-center text-[10px] text-white font-bold uppercase">
                    {user.username?.substring(0, 2) || 'US'}
                  </div>
                  <span className="font-medium truncate flex-1 text-left">{user.username}</span>
                </button>

                {showUserMenu && (
                  <>
                    <div
                      className="fixed inset-0 z-20"
                      onClick={() => setShowUserMenu(false)}
                    />
                    <div className="absolute bottom-full left-0 w-full mb-2 bg-white border border-[#e5e5e5] rounded-xl shadow-lg py-1 z-30 animate-in fade-in slide-in-from-bottom-2 duration-200">
                      <div className="px-3 py-2 border-b border-[#f4f4f4]">
                        <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">Account</p>
                        <p className="text-sm font-medium truncate">{user.username}</p>
                      </div>
                      <button
                        onClick={() => { setShowAddModal(true); setShowUserMenu(false) }}
                        className="flex items-center gap-3 w-full p-3 text-sm hover:bg-gray-50 transition-colors"
                      >
                        <Settings size={16} />
                        <span>AI Settings</span>
                      </button>
                      <button
                        onClick={handleLogout}
                        className="flex items-center gap-3 w-full p-3 text-sm text-red-600 hover:bg-red-50 transition-colors"
                      >
                        <LogOut size={16} />
                        <span>Log out</span>
                      </button>
                    </div>
                  </>
                )}
              </div>
            ) : (
              <div className="space-y-1">
                <Link to="/login" className="flex items-center gap-3 w-full p-2 text-sm hover:bg-[#ececec] rounded-lg transition-colors">
                  <LogIn size={16} />
                  <span>Log in</span>
                </Link>
                <Link to="/register" className="flex items-center gap-3 w-full p-2 text-sm hover:bg-[#ececec] rounded-lg transition-colors">
                  <UserPlus size={16} />
                  <span>Sign up</span>
                </Link>
              </div>
            )}
          </div>
        </div>
      </aside>

      <main className="flex-1 flex flex-col relative h-full min-w-0">
        <header className="h-14 flex items-center justify-between px-4 sticky top-0 z-10 bg-white/80 backdrop-blur-sm">
          <div className="flex items-center gap-2">
            {!sidebarOpen && (
              <button
                onClick={() => setSidebarOpen(true)}
                className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-all"
              >
                <Menu size={20} />
              </button>
            )}
            {sidebarOpen && (
              <button
                onClick={() => setSidebarOpen(false)}
                className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-all hidden md:block"
              >
                <ChevronLeft size={20} />
              </button>
            )}

            <div className="ml-2 flex items-center gap-3">
              <div className="font-semibold text-lg tracking-tight">Agentic RAG</div>
              <div className="relative">
                <button
                  onClick={() => {
                    if (showModelMenu) setModelSearchQuery('')
                    setShowModelMenu(!showModelMenu)
                  }}
                  className="flex items-center gap-2 px-2.5 py-1.5 hover:bg-gray-50 rounded-lg transition-colors text-sm font-medium text-gray-400 border border-gray-200/60 shadow-sm"
                >
                  <Sparkles size={14} className="text-emerald-500/70" />
                  <span className="max-w-[150px] truncate">
                    {aiSettings ? `${aiSettings.provider}:${aiSettings.chatModel}` : 'Select Model'}
                  </span>
                  <ChevronDown size={12} className="text-gray-400" />
                </button>

                {showModelMenu && (
                  <>
                    <div
                      className="fixed inset-0 z-20"
                      onClick={() => {
                        setModelSearchQuery('')
                        setShowModelMenu(false)
                      }}
                    />
                    <div className="absolute top-full left-0 mt-1.5 w-72 bg-white border border-gray-200 rounded-xl shadow-xl py-1 z-30 animate-in fade-in slide-in-from-top-1 duration-200">
                      <div className="px-3 py-2 border-b border-gray-50">
                        <div className="flex items-center gap-2 px-2 py-1.5 bg-gray-50 rounded-lg">
                          <Search size={14} className="text-gray-400" />
                          <input
                            type="text"
                            placeholder="Search models..."
                            value={modelSearchQuery}
                            onChange={(e) => setModelSearchQuery(e.target.value)}
                            className="w-full bg-transparent text-sm focus:outline-none placeholder:text-gray-400"
                            autoFocus
                          />
                        </div>
                      </div>
                      <div className="px-3 py-1.5 text-[10px] font-bold text-gray-400 uppercase tracking-widest border-b border-gray-50 mb-1">
                        AI Models
                      </div>
                      <div className="max-h-72 overflow-y-auto scrollbar-hide">
                        {configuredModels.length === 0 ? (
                          <div className="px-4 py-3 text-sm text-gray-400">No configured models yet</div>
                        ) : (
                          configuredModels
                            .filter(m => 
                              m.chatModel.toLowerCase().includes(modelSearchQuery.toLowerCase()) || 
                              m.provider.toLowerCase().includes(modelSearchQuery.toLowerCase())
                            )
                            .map((model, idx) => (
                            <button
                              key={`${model.provider}-${model.chatModel}-${idx}`}
                              onClick={() => {
                                handleSwitchModel(model)
                                setModelSearchQuery('')
                                setShowModelMenu(false)
                              }}
                              className="flex items-center justify-between w-full px-3 py-2 text-sm hover:bg-gray-50 transition-colors"
                            >
                              <div className="flex flex-col items-start min-w-0">
                                <span className={`truncate w-full font-medium ${model.active ? 'text-black' : 'text-gray-600'}`}>
                                  {model.chatModel}
                                </span>
                                <span className="text-[10px] text-gray-400 uppercase leading-tight">{model.provider}</span>
                              </div>
                              {model.active && (
                                <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full flex-shrink-0 ml-2" />
                              )}
                            </button>
                          ))
                        )}
                        {configuredModels.length > 0 && 
                         configuredModels.filter(m => m.chatModel.toLowerCase().includes(modelSearchQuery.toLowerCase()) || m.provider.toLowerCase().includes(modelSearchQuery.toLowerCase())).length === 0 && (
                          <div className="px-4 py-3 text-sm text-gray-400 text-center">No models found</div>
                        )}
                      </div>
                      <div className="h-px bg-gray-100 my-1" />
                      <button
                        onClick={() => {
                          setModelSearchQuery('')
                          setShowModelMenu(false)
                          setShowAddModal(true)
                        }}
                        className="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-400 hover:text-gray-600 hover:bg-gray-50 transition-colors"
                      >
                        <Plus size={14} />
                        <span>Manage Providers</span>
                      </button>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto scrollbar-hide pt-4">
          <div className="max-w-3xl mx-auto px-4 w-full">
            {messages.length === 0 ? (
              <div className="h-[70vh] flex flex-col items-center justify-center space-y-6">
                <div className="w-16 h-16 bg-white border border-gray-200 rounded-2xl flex items-center justify-center shadow-sm text-black">
                  <Bot size={32} strokeWidth={1.5} />
                </div>
                <h2 className="text-2xl font-semibold tracking-tight">How can I help you today?</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-xl">
                  {[
                    { text: 'Explain quantum computing', icon: <Search size={14} /> },
                    { text: 'Write a professional email', icon: <Mail size={14} /> },
                    { text: 'Help me debug my code', icon: <Bug size={14} /> },
                    { text: 'Plan a weekend trip', icon: <Map size={14} /> }
                  ].map((suggestion) => (
                    <button
                      key={suggestion.text}
                      onClick={() => setInput(suggestion.text)}
                      className="p-4 text-left text-sm border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors text-gray-600 flex items-center justify-between group"
                    >
                      <span>{suggestion.text}</span>
                      <span className="opacity-0 group-hover:opacity-100 transition-opacity">{suggestion.icon}</span>
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="space-y-8 pb-32">
                {messages.map((message, index) => (
                  <div key={index} className={`flex gap-4 group ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {message.role === 'assistant' && (
                      <div className="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white flex-shrink-0 mt-0.5 shadow-sm">
                        <Bot size={18} />
                      </div>
                    )}
                    <div className={`group relative flex flex-col gap-1 max-w-[85%] ${message.role === 'user' ? 'items-end' : 'items-start'}`}>
                      <div className={`px-4 py-2.5 rounded-2xl text-[15px] leading-relaxed ${
                        message.role === 'user'
                          ? 'bg-[#f4f4f4] text-[#171717] rounded-tr-sm'
                          : 'bg-white text-[#171717]'
                      }`}>
                        <div className="whitespace-pre-wrap">{message.content}</div>
                      </div>
                      {message.role === 'assistant' && !isLoading && (
                        <div className="flex gap-2 ml-1 mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button className="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600">
                            <Copy size={14} />
                          </button>
                          <button className="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600">
                            <ThumbsUp size={14} />
                          </button>
                        </div>
                      )}
                    </div>
                    {message.role === 'user' && (
                      <div className="w-8 h-8 rounded-full bg-[#171717] flex items-center justify-center text-white flex-shrink-0 mt-0.5">
                        <User size={16} />
                      </div>
                    )}
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>
            )}
          </div>
        </div>

        <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-white via-white/95 to-transparent pt-10 pb-6 px-4">
          <div className="max-w-3xl mx-auto relative group">
            <form onSubmit={handleSubmit} className="relative bg-white border border-[#e5e5e5] rounded-2xl shadow-[0_0_20px_rgba(0,0,0,0.05)] focus-within:border-gray-300 transition-all overflow-hidden">
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    handleSubmit(e)
                  }
                }}
                placeholder="Message Agentic RAG..."
                rows={1}
                className="w-full p-4 pr-16 resize-none bg-transparent focus:outline-none text-[15px] max-h-40 scrollbar-hide"
                style={{ height: 'auto' }}
              />
              <div className="absolute right-3 bottom-3 flex items-center gap-2">
                <button
                  type="submit"
                  disabled={isLoading || !input.trim()}
                  className={`p-2 rounded-xl transition-all ${
                    isLoading || !input.trim()
                      ? 'bg-gray-100 text-gray-300'
                      : 'bg-black text-white hover:scale-105 active:scale-95'
                  }`}
                >
                  <Send size={18} strokeWidth={2.5} />
                </button>
              </div>
            </form>
            <p className="text-[11px] text-center text-gray-400 mt-3 font-medium">
              Agentic RAG can make mistakes. Check important info.
            </p>
          </div>
        </div>
      </main>
    </div>
  )
}

function App() {
  return (
    <Router>
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
    </Router>
  )
}

export default App
