import { Bot, Bug, Copy, Mail, Map, Search, ThumbsUp, User } from 'lucide-react';
import type { RefObject } from 'react';
import MessageContent from '../MessageContent';
import type { Message } from '../../api/chat';

interface ChatContentProps {
  isLoadingMessages: boolean;
  messages: Message[];
  isLoading: boolean;
  messagesEndRef: RefObject<HTMLDivElement | null>;
  onSuggestionClick: (value: string) => void;
}

const suggestions = [
  { text: 'Explain quantum computing', icon: <Search size={14} /> },
  { text: 'Write a professional email', icon: <Mail size={14} /> },
  { text: 'Help me debug my code', icon: <Bug size={14} /> },
  { text: 'Plan a weekend trip', icon: <Map size={14} /> },
];

export default function ChatContent({
  isLoadingMessages,
  messages,
  isLoading,
  messagesEndRef,
  onSuggestionClick,
}: ChatContentProps) {
  return (
    <div className="max-w-3xl mx-auto px-4 w-full">
      {isLoadingMessages ? (
        <div className="h-[70vh] flex flex-col items-center justify-center space-y-4">
          <div className="w-8 h-8 border-2 border-gray-200 border-t-black rounded-full animate-spin" />
          <p className="text-sm text-gray-400">Loading messages...</p>
        </div>
      ) : messages.length === 0 ? (
        <div className="h-[70vh] flex flex-col items-center justify-center space-y-6">
          <div className="w-16 h-16 bg-white border border-gray-200 rounded-2xl flex items-center justify-center shadow-sm text-black">
            <Bot size={32} strokeWidth={1.5} />
          </div>
          <h2 className="text-2xl font-semibold tracking-tight">How can I help you today?</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-xl">
            {suggestions.map((suggestion) => (
              <button
                key={suggestion.text}
                onClick={() => onSuggestionClick(suggestion.text)}
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
            <div key={`${message.role}-${index}`} className={`flex gap-4 group ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              {message.role === 'assistant' ? (
                <div className="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white flex-shrink-0 mt-0.5 shadow-sm">
                  <Bot size={18} />
                </div>
              ) : null}

              <div className={`group relative flex flex-col gap-1 max-w-[85%] ${message.role === 'user' ? 'items-end' : 'items-start'}`}>
                <div
                  className={`px-4 py-2.5 rounded-2xl text-[15px] leading-relaxed ${
                    message.role === 'user'
                      ? 'bg-[#f4f4f4] text-[#171717] rounded-tr-sm'
                      : 'bg-white text-[#171717]'
                  }`}
                >
                  {message.role === 'assistant' && isLoading && index === messages.length - 1 && !message.content ? (
                    <div className="flex gap-1.5 items-center h-6 px-1">
                      <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:-0.3s]" />
                      <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:-0.15s]" />
                      <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" />
                    </div>
                  ) : (
                    <MessageContent content={message.content} />
                  )}
                </div>

                {message.role === 'assistant' && !isLoading ? (
                  <div className="flex gap-2 ml-1 mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => navigator.clipboard.writeText(message.content)}
                      className="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600"
                      title="Copy to clipboard"
                    >
                      <Copy size={14} />
                    </button>
                    <button className="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600">
                      <ThumbsUp size={14} />
                    </button>
                  </div>
                ) : null}
              </div>

              {message.role === 'user' ? (
                <div className="w-8 h-8 rounded-full bg-[#171717] flex items-center justify-center text-white flex-shrink-0 mt-0.5">
                  <User size={16} />
                </div>
              ) : null}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
      )}
    </div>
  );
}
