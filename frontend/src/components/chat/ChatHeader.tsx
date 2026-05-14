import {
  ChevronDown,
  ChevronLeft,
  Database,
  Library,
  Menu,
  MessageSquare,
  Plus,
  Search,
  Sparkles,
} from 'lucide-react';
import type { KnowledgeBase } from '../../api/knowledge';
import type { AiSettings, ConfiguredModelOption } from './types';

interface ChatHeaderProps {
  sidebarOpen: boolean;
  showModelMenu: boolean;
  showSceneMenu: boolean;
  modelSearchQuery: string;
  aiSettings: AiSettings | null;
  configuredModels: ConfiguredModelOption[];
  selectedKbId: string | null;
  knowledgeBases: KnowledgeBase[];
  onToggleSidebar: (open: boolean) => void;
  onToggleModelMenu: () => void;
  onCloseModelMenu: () => void;
  onToggleSceneMenu: () => void;
  onCloseSceneMenu: () => void;
  onModelSearchChange: (value: string) => void;
  onSwitchModel: (option: ConfiguredModelOption) => void;
  onOpenSettings: () => void;
  onSelectSceneKb: (kbId: string | null) => void;
}

export default function ChatHeader({
  sidebarOpen,
  showModelMenu,
  showSceneMenu,
  modelSearchQuery,
  aiSettings,
  configuredModels,
  selectedKbId,
  knowledgeBases,
  onToggleSidebar,
  onToggleModelMenu,
  onCloseModelMenu,
  onToggleSceneMenu,
  onCloseSceneMenu,
  onModelSearchChange,
  onSwitchModel,
  onOpenSettings,
  onSelectSceneKb,
}: ChatHeaderProps) {
  const filteredModels = configuredModels.filter(
    (model) =>
      model.chatModel.toLowerCase().includes(modelSearchQuery.toLowerCase()) ||
      model.provider.toLowerCase().includes(modelSearchQuery.toLowerCase()),
  );

  const selectedKbName = selectedKbId
    ? knowledgeBases.find((kb) => kb.id === selectedKbId)?.name
    : null;

  return (
    <header className="h-14 flex items-center justify-between px-4 sticky top-0 z-10 bg-white/80 backdrop-blur-sm">
      <div className="flex items-center gap-2">
        {!sidebarOpen ? (
          <button
            onClick={() => onToggleSidebar(true)}
            className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-all"
          >
            <Menu size={20} />
          </button>
        ) : (
          <button
            onClick={() => onToggleSidebar(false)}
            className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-all hidden md:block"
          >
            <ChevronLeft size={20} />
          </button>
        )}

        <div className="ml-2 flex items-center gap-3">
          <div className="font-semibold text-lg tracking-tight">Agentic RAG</div>

          <div className="relative">
            <button
              onClick={onToggleModelMenu}
              className="flex items-center gap-2 px-2.5 py-1.5 hover:bg-gray-50 rounded-lg transition-colors text-sm font-medium text-gray-400 border border-gray-200/60 shadow-sm"
            >
              <Sparkles size={14} className="text-emerald-500/70" />
              <span className="max-w-[150px] truncate">
                {aiSettings ? `${aiSettings.provider}:${aiSettings.chatModel}` : 'Select Model'}
              </span>
              <ChevronDown size={12} className="text-gray-400" />
            </button>

            {showModelMenu ? (
              <>
                <div className="fixed inset-0 z-20" onClick={onCloseModelMenu} />
                <div className="absolute top-full left-0 mt-1.5 w-72 bg-white border border-gray-200 rounded-xl shadow-xl py-1 z-30">
                  <div className="px-3 py-2 border-b border-gray-50">
                    <div className="flex items-center gap-2 px-2 py-1.5 bg-gray-50 rounded-lg">
                      <Search size={14} className="text-gray-400" />
                      <input
                        type="text"
                        placeholder="Search models..."
                        value={modelSearchQuery}
                        onChange={(e) => onModelSearchChange(e.target.value)}
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
                      filteredModels.map((model, idx) => (
                        <button
                          key={`${model.provider}-${model.chatModel}-${idx}`}
                          onClick={() => onSwitchModel(model)}
                          className="flex items-center justify-between w-full px-3 py-2 text-sm hover:bg-gray-50 transition-colors"
                        >
                          <div className="flex flex-col items-start min-w-0">
                            <span className={`truncate w-full font-medium ${model.active ? 'text-black' : 'text-gray-600'}`}>
                              {model.chatModel}
                            </span>
                            <span className="text-[10px] text-gray-400 uppercase leading-tight">{model.provider}</span>
                          </div>
                          {model.active ? <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full flex-shrink-0 ml-2" /> : null}
                        </button>
                      ))
                    )}
                  </div>
                  <div className="h-px bg-gray-100 my-1" />
                  <button
                    onClick={onOpenSettings}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-400 hover:text-gray-600 hover:bg-gray-50 transition-colors"
                  >
                    <Plus size={14} />
                    <span>Manage Providers</span>
                  </button>
                </div>
              </>
            ) : null}
          </div>

          <div className="relative">
            <button
              onClick={onToggleSceneMenu}
              className="flex items-center gap-2 px-2.5 py-1.5 hover:bg-gray-50 rounded-lg transition-colors text-sm font-medium text-gray-400 border border-gray-200/60 shadow-sm"
            >
              <Database size={14} className="text-blue-500/70" />
              <span className="max-w-[150px] truncate">
                {selectedKbName ? `RAG: ${selectedKbName}` : 'Standard Chat'}
              </span>
              <ChevronDown size={12} className="text-gray-400" />
            </button>

            {showSceneMenu ? (
              <>
                <div className="fixed inset-0 z-20" onClick={onCloseSceneMenu} />
                <div className="absolute top-full left-0 mt-1.5 w-64 bg-white border border-gray-200 rounded-xl shadow-xl py-1 z-30">
                  <div className="px-3 py-1.5 text-[10px] font-bold text-gray-400 uppercase tracking-widest border-b border-gray-50 mb-1">
                    Chat Mode
                  </div>
                  <button
                    onClick={() => onSelectSceneKb(null)}
                    className="flex items-center justify-between w-full px-3 py-2.5 text-sm hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-2">
                      <MessageSquare size={14} className="text-gray-400" />
                      <span className={!selectedKbId ? 'font-medium text-black' : 'text-gray-600'}>Standard Chat</span>
                    </div>
                    {!selectedKbId ? <div className="w-1.5 h-1.5 bg-blue-500 rounded-full" /> : null}
                  </button>

                  <div className="px-3 py-1.5 text-[10px] font-bold text-gray-400 uppercase tracking-widest border-y border-gray-50 my-1">
                    Knowledge Bases (RAG)
                  </div>
                  <div className="max-h-64 overflow-y-auto scrollbar-hide">
                    {knowledgeBases.length === 0 ? (
                      <div className="px-4 py-3 text-xs text-gray-400 italic">No knowledge bases found</div>
                    ) : (
                      knowledgeBases.map((kb) => (
                        <button
                          key={kb.id}
                          onClick={() => onSelectSceneKb(kb.id)}
                          className="flex items-center justify-between w-full px-3 py-2.5 text-sm hover:bg-gray-50 transition-colors"
                        >
                          <div className="flex items-center gap-2 truncate">
                            <Library size={14} className="text-gray-400 flex-shrink-0" />
                            <span className={`truncate ${selectedKbId === kb.id ? 'font-medium text-black' : 'text-gray-600'}`}>
                              {kb.name}
                            </span>
                          </div>
                          {selectedKbId === kb.id ? (
                            <div className="w-1.5 h-1.5 bg-blue-500 rounded-full flex-shrink-0 ml-2" />
                          ) : null}
                        </button>
                      ))
                    )}
                  </div>
                </div>
              </>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}
