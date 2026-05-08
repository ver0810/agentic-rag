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
  Settings
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

function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null

  useEffect(() => {
    if (user) {
      fetchAiSettings()
    }
  }, [])

  const fetchAiSettings = async () => {
    try {
      const response = await axios.get('/user/ai-settings', {
        headers: { 'X-User-Id': user.id }
      })
      setAiSettings(response.data)
    } catch (err) {
      console.error('Failed to fetch AI settings', err)
    }
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const handleLogout = () => {
    localStorage.removeItem('user')
    localStorage.removeItem('userId')
    navigate('/login')
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
      const response = await fetch('/chat/stream?message=' + encodeURIComponent(input), {
        method: 'POST',
        headers: {
          'X-User-Id': user?.id || ''
        }
      })

      if (!response.ok) throw new Error('Failed to fetch')

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
    } catch (error) {
      console.error('Error:', error)
      setMessages(prev => {
        const newMessages = [...prev]
        newMessages[newMessages.length - 1] = {
          ...newMessages[newMessages.length - 1],
          content: 'Error: Failed to get response.'
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
        onSuccess={fetchAiSettings}
      />

      <aside
        className={`${
          sidebarOpen ? 'w-[260px]' : 'w-0'
        } transition-all duration-300 ease-in-out flex flex-col bg-[#f9f9f9] border-r border-[#e5e5e5] overflow-hidden`}
      >
        <div className="p-3 flex flex-col h-full w-[260px]">
          <button className="flex items-center justify-between w-full p-2 text-sm font-medium hover:bg-[#ececec] rounded-lg transition-colors group">
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
            <div className="px-3 py-2 text-sm text-gray-400 italic">No recent chats</div>
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

            <div className="ml-2 flex flex-col">
              <div className="font-semibold text-lg tracking-tight">Agentic RAG</div>
              {aiSettings?.providerName && (
                <div className="text-xs text-gray-400">
                  {aiSettings.providerName}
                  {aiSettings.verified ? ' connected' : ' not verified'}
                </div>
              )}
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
