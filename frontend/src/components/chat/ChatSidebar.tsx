import {
  Activity,
  ChevronDown,
  Database,
  Library,
  LogIn,
  LogOut,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Plus,
  Settings,
  SquareTerminal,
  Target,
  Trash2,
  User,
  UserPlus,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { KnowledgeBase } from '../../api/knowledge';
import type {
  ChatSessionItem,
  ChatUser,
  ObservabilityTab,
  SidebarTab,
} from './types';

interface ChatSidebarProps {
  sidebarOpen: boolean;
  sidebarTab: SidebarTab;
  obsTab: ObservabilityTab;
  recentChats: ChatSessionItem[];
  currentSessionId: string | null;
  activeKb: KnowledgeBase | null;
  knowledgeBases: KnowledgeBase[];
  editingSessionId: string | null;
  editingTitle: string;
  activeMenuSessionId: string | null;
  showUserMenu: boolean;
  user: ChatUser | null;
  onToggleSidebarTab: (tab: SidebarTab) => void;
  onSelectObsTab: (tab: ObservabilityTab) => void;
  onCreateSession: () => void;
  onSwitchSession: (sessionId: string) => void;
  onStartRename: (sessionId: string, currentTitle: string) => void;
  onCommitRename: (sessionId: string) => void;
  onEditTitleChange: (title: string) => void;
  onDeleteSession: (sessionId: string) => void;
  onToggleSessionMenu: (sessionId: string | null) => void;
  onCreateKb: () => void;
  onSelectKb: (kb: KnowledgeBase) => void;
  onOpenSettings: () => void;
  onToggleUserMenu: () => void;
  onCloseUserMenu: () => void;
  onLogout: () => void;
}

export default function ChatSidebar({
  sidebarOpen,
  sidebarTab,
  obsTab,
  recentChats,
  currentSessionId,
  activeKb,
  knowledgeBases,
  editingSessionId,
  editingTitle,
  activeMenuSessionId,
  showUserMenu,
  user,
  onToggleSidebarTab,
  onSelectObsTab,
  onCreateSession,
  onSwitchSession,
  onStartRename,
  onCommitRename,
  onEditTitleChange,
  onDeleteSession,
  onToggleSessionMenu,
  onCreateKb,
  onSelectKb,
  onOpenSettings,
  onToggleUserMenu,
  onCloseUserMenu,
  onLogout,
}: ChatSidebarProps) {
  return (
    <aside
      className={`${
        sidebarOpen ? 'w-[260px]' : 'w-0'
      } transition-all duration-300 ease-in-out flex flex-col bg-[#f9f9f9] border-r border-[#e5e5e5] overflow-hidden`}
    >
      <div className="p-3 flex flex-col h-full w-[260px]">
        <div className="flex bg-[#ececec] p-1 rounded-xl mb-4">
          <SidebarTabButton
            active={sidebarTab === 'chats'}
            icon={<MessageSquare size={14} />}
            label="Chats"
            onClick={() => onToggleSidebarTab('chats')}
          />
          <SidebarTabButton
            active={sidebarTab === 'knowledge'}
            icon={<Library size={14} />}
            label="KB"
            onClick={() => onToggleSidebarTab('knowledge')}
          />
          <SidebarTabButton
            active={sidebarTab === 'observability'}
            icon={<Activity size={14} />}
            label="Trace"
            onClick={() => onToggleSidebarTab('observability')}
          />
        </div>

        {sidebarTab === 'chats' ? (
          <>
            <ActionButton
              icon={<Plus size={16} />}
              trailing={<SquareTerminal size={16} className="text-gray-400 group-hover:text-black" />}
              label="New Chat"
              onClick={onCreateSession}
            />

            <div className="flex-1 mt-4 overflow-y-auto space-y-1">
              <SectionLabel label="Recent" />
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
                        onChange={(e) => onEditTitleChange(e.target.value)}
                        onBlur={() => onCommitRename(chat.sessionId)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') onCommitRename(chat.sessionId);
                          if (e.key === 'Escape') onEditTitleChange(chat.chatTitle);
                        }}
                      />
                    ) : (
                      <>
                        <button
                          onClick={() => onSwitchSession(chat.sessionId)}
                          className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors truncate pr-10 ${
                            currentSessionId === chat.sessionId && !activeKb
                              ? 'bg-[#ececec] font-medium'
                              : 'hover:bg-[#ececec] text-gray-600'
                          }`}
                        >
                          {chat.chatTitle}
                        </button>

                        <div className="absolute right-1 top-1 opacity-0 group-hover/item:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              onToggleSessionMenu(activeMenuSessionId === chat.sessionId ? null : chat.sessionId);
                            }}
                            className="p-1.5 hover:bg-gray-200 rounded-full text-gray-500 hover:text-black transition-colors"
                          >
                            <MoreHorizontal size={14} />
                          </button>

                          {activeMenuSessionId === chat.sessionId ? (
                            <>
                              <div className="fixed inset-0 z-40" onClick={() => onToggleSessionMenu(null)} />
                              <div className="absolute right-0 top-full mt-1 w-32 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-50">
                                <MenuButton
                                  icon={<Pencil size={12} />}
                                  label="Rename"
                                  onClick={() => onStartRename(chat.sessionId, chat.chatTitle)}
                                />
                                <MenuButton
                                  destructive
                                  icon={<Trash2 size={12} />}
                                  label="Delete"
                                  onClick={() => onDeleteSession(chat.sessionId)}
                                />
                              </div>
                            </>
                          ) : null}
                        </div>
                      </>
                    )}
                  </div>
                ))
              )}
            </div>
          </>
        ) : sidebarTab === 'knowledge' ? (
          <>
            <ActionButton
              icon={<Plus size={16} />}
              trailing={<Database size={16} className="text-gray-400 group-hover:text-black" />}
              label="New Base"
              onClick={onCreateKb}
            />

            <div className="flex-1 mt-4 overflow-y-auto space-y-1">
              <SectionLabel label="Knowledge Bases" />
              {knowledgeBases.length === 0 ? (
                <div className="px-3 py-2 text-sm text-gray-400 italic">No knowledge bases</div>
              ) : (
                knowledgeBases.map((kb) => (
                  <button
                    key={kb.id}
                    onClick={() => onSelectKb(kb)}
                    className={`w-full text-left p-3 rounded-xl border transition-colors ${
                      activeKb?.id === kb.id
                        ? 'bg-white border-gray-300 shadow-sm'
                        : 'bg-transparent border-transparent hover:bg-[#ececec]'
                    }`}
                  >
                    <div className="flex items-center gap-2 text-sm font-medium">
                      <Library size={15} className="text-gray-500 flex-shrink-0" />
                      <span className="truncate">{kb.name}</span>
                    </div>
                    <p className="mt-1 text-xs text-gray-500 line-clamp-2">
                      {kb.description || 'No description provided.'}
                    </p>
                  </button>
                ))
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col">
            <div className="rounded-2xl border border-gray-200 bg-white p-3 space-y-2">
              <button
                onClick={() => onSelectObsTab('trace')}
                className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl text-sm transition-colors ${
                  obsTab === 'trace' ? 'bg-[#171717] text-white' : 'hover:bg-gray-50 text-gray-600'
                }`}
              >
                <Activity size={16} />
                <span>Trace Overview</span>
              </button>
              <button
                onClick={() => onSelectObsTab('eval')}
                className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl text-sm transition-colors ${
                  obsTab === 'eval' ? 'bg-[#171717] text-white' : 'hover:bg-gray-50 text-gray-600'
                }`}
              >
                <Target size={16} />
                <span>Eval Center</span>
              </button>
            </div>
            <div className="mt-4 px-2 text-xs text-gray-400 leading-5">
              Switch between trace monitoring and evaluation runs here.
            </div>
          </div>
        )}

        <div className="mt-auto pt-3 border-t border-[#e5e5e5]">
          {user ? (
            <div className="relative">
              <button
                onClick={onToggleUserMenu}
                className="flex items-center justify-between w-full p-2 hover:bg-[#ececec] rounded-lg transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-8 h-8 rounded-full bg-[#171717] text-white flex items-center justify-center flex-shrink-0">
                    <User size={16} />
                  </div>
                  <div className="min-w-0 text-left">
                    <div className="text-sm font-medium truncate">{user.username || 'User'}</div>
                    <div className="text-xs text-gray-500">Workspace</div>
                  </div>
                </div>
                <ChevronDown size={14} className="text-gray-400" />
              </button>

              {showUserMenu ? (
                <>
                  <div className="fixed inset-0 z-40" onClick={onCloseUserMenu} />
                  <div className="absolute bottom-full left-0 mb-2 w-full bg-white border border-gray-200 rounded-xl shadow-lg py-2 z-50">
                    <MenuButton
                      icon={<Settings size={16} />}
                      label="AI Settings"
                      onClick={onOpenSettings}
                    />
                    <MenuButton
                      destructive
                      icon={<LogOut size={16} />}
                      label="Log out"
                      onClick={onLogout}
                    />
                  </div>
                </>
              ) : null}
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
  );
}

function SidebarTabButton({
  active,
  icon,
  label,
  onClick,
}: {
  active: boolean;
  icon: ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex-1 flex items-center justify-center gap-2 py-1.5 text-xs font-medium rounded-lg transition-all ${
        active ? 'bg-white shadow-sm text-black' : 'text-gray-500 hover:text-gray-700'
      }`}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}

function ActionButton({
  icon,
  trailing,
  label,
  onClick,
}: {
  icon: ReactNode;
  trailing: ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className="flex items-center justify-between w-full p-2 text-sm font-medium hover:bg-[#ececec] rounded-lg transition-colors group"
      onClick={onClick}
    >
      <div className="flex items-center gap-2">
        <div className="w-7 h-7 bg-white border border-[#e5e5e5] rounded-full flex items-center justify-center shadow-sm">
          {icon}
        </div>
        <span>{label}</span>
      </div>
      {trailing}
    </button>
  );
}

function SectionLabel({ label }: { label: string }) {
  return (
    <div className="px-2 py-1 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
      <span>{label}</span>
    </div>
  );
}

function MenuButton({
  icon,
  label,
  destructive,
  onClick,
}: {
  icon: ReactNode;
  label: string;
  destructive?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-2 w-full px-3 py-2 text-sm transition-colors ${
        destructive ? 'text-red-600 hover:bg-red-50' : 'text-gray-700 hover:bg-gray-50'
      }`}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}
