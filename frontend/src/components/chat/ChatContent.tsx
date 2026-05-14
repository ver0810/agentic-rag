import { Bot, Bug, Copy, Mail, Map, Search, ThumbsDown, ThumbsUp, User } from 'lucide-react';
import type { RefObject } from 'react';
import MessageContent from '../MessageContent';
import type { Message } from '../../api/chat';
import type { KnowledgeBase } from '../../api/knowledge';

interface ChatContentProps {
  isLoadingMessages: boolean;
  messages: Message[];
  isLoading: boolean;
  messagesEndRef: RefObject<HTMLDivElement | null>;
  knowledgeBases: KnowledgeBase[];
  highlightedTraceId?: string | null;
  feedbackRatings?: Record<string, number>;
  isSubmittingFeedback?: boolean;
  onTraceClick?: (traceId: string) => void;
  onSubmitFeedback?: (payload: {
    traceId: string;
    kbId?: string;
    query: string;
    answer: string;
    rating: number;
  }) => void;
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
  knowledgeBases,
  highlightedTraceId,
  feedbackRatings,
  isSubmittingFeedback,
  onTraceClick,
  onSubmitFeedback,
  onSuggestionClick,
}: ChatContentProps) {
  const resolveKbName = (kbId?: string) =>
    kbId ? knowledgeBases.find((kb) => kb.id === kbId)?.name ?? kbId : null;

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
            (() => {
              const previousUserMessage = [...messages.slice(0, index)].reverse().find((item) => item.role === 'user');
              const feedbackRating = message.traceId ? feedbackRatings?.[message.traceId] : undefined;
              return (
            <div
              key={`${message.role}-${index}`}
              className={`flex gap-4 group ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              {message.role === 'assistant' ? (
                <div className="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white flex-shrink-0 mt-0.5 shadow-sm">
                  <Bot size={18} />
                </div>
              ) : null}

              <div
                className={`group relative flex flex-col gap-1 max-w-[85%] ${message.role === 'user' ? 'items-end' : 'items-start'}`}
                data-trace-id={message.traceId}
              >
                <div
                  className={`px-4 py-2.5 rounded-2xl text-[15px] leading-relaxed ${
                    message.role === 'user'
                      ? 'bg-[#f4f4f4] text-[#171717] rounded-tr-sm'
                      : message.traceId && highlightedTraceId === message.traceId
                        ? 'bg-emerald-50 text-[#171717] ring-1 ring-emerald-200 shadow-sm'
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

                {(message.sourceType === 'rag' || message.traceId || message.kbId) ? (
                  <div className="flex flex-wrap gap-2 mt-1 px-1">
                    {message.sourceType === 'rag' ? (
                      <span className="rounded-full border border-blue-200 bg-blue-50 px-2 py-0.5 text-[10px] font-medium text-blue-700">
                        RAG
                      </span>
                    ) : null}
                    {message.kbId ? (
                      <span className="rounded-full border border-gray-200 bg-gray-50 px-2 py-0.5 text-[10px] text-gray-600">
                        KB: {resolveKbName(message.kbId)}
                      </span>
                    ) : null}
                    {message.traceId ? (
                      <button
                        type="button"
                        onClick={() => onTraceClick?.(message.traceId!)}
                        className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700 transition-colors hover:bg-emerald-100"
                      >
                        Trace: {message.traceId.slice(0, 8)}
                      </button>
                    ) : null}
                  </div>
                ) : null}

                {message.role === 'assistant' && message.rewrittenQuery ? (
                  <div className="w-full mt-1 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2">
                    <div className="text-[11px] font-medium text-amber-700 uppercase tracking-wide mb-1">
                      Rewritten Query
                    </div>
                    <div className="text-xs text-amber-900">{message.rewrittenQuery}</div>
                  </div>
                ) : null}

                {message.role === 'assistant' && !isLoading && ((message.citations?.length ?? 0) > 0 || (message.retrievedChunks?.length ?? 0) > 0) ? (
                  <div className="w-full mt-2 space-y-2">
                    {(message.citations?.length ?? 0) > 0 ? (
                      <div className="rounded-xl border border-gray-200 bg-[#fafafa] px-3 py-2">
                        <div className="text-[11px] font-medium text-gray-500 mb-2 uppercase tracking-wide">Citations</div>
                        <div className="space-y-2">
                          {message.citations?.slice(0, 4).map((citation) => (
                            <div key={citation.chunkId} className="text-xs text-gray-600">
                              <div className="flex flex-wrap gap-2 items-center mb-1">
                                <span className="font-medium text-gray-800">{citation.docName || 'Unknown Document'}</span>
                                {citation.segmentType ? (
                                  <span className="rounded-full bg-white border border-gray-200 px-2 py-0.5 text-[10px] text-gray-500">
                                    {citation.segmentType}
                                  </span>
                                ) : null}
                                {typeof citation.chunkIndex === 'number' ? (
                                  <span className="text-[10px] text-gray-400">Chunk {citation.chunkIndex + 1}</span>
                                ) : null}
                              </div>
                              {citation.headingPath ? (
                                <div className="text-[11px] text-gray-500 mb-1">{citation.headingPath}</div>
                              ) : null}
                              <div className="line-clamp-2 text-gray-600">{citation.snippet}</div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}

                    {(message.retrievedChunks?.length ?? 0) > 0 ? (
                      <details className="rounded-xl border border-gray-200 bg-[#fafafa] px-3 py-2">
                        <summary className="cursor-pointer text-[11px] font-medium text-gray-500 uppercase tracking-wide">
                          Retrieved Chunks ({message.retrievedChunks?.length ?? 0})
                        </summary>
                        <div className="mt-2 space-y-3">
                          {message.retrievedChunks?.slice(0, 3).map((chunk) => (
                            <div key={chunk.chunkId} className="text-xs text-gray-600">
                              <div className="flex flex-wrap gap-2 items-center mb-1">
                                <span className="font-medium text-gray-800">{chunk.docName || 'Unknown Document'}</span>
                                {chunk.segmentType ? (
                                  <span className="rounded-full bg-white border border-gray-200 px-2 py-0.5 text-[10px] text-gray-500">
                                    {chunk.segmentType}
                                  </span>
                                ) : null}
                                {typeof chunk.chunkIndex === 'number' ? (
                                  <span className="text-[10px] text-gray-400">Chunk {chunk.chunkIndex + 1}</span>
                                ) : null}
                              </div>
                              {chunk.headingPath ? (
                                <div className="text-[11px] text-gray-500 mb-1">{chunk.headingPath}</div>
                              ) : null}
                              <div className="line-clamp-4 whitespace-pre-wrap">{chunk.content}</div>
                            </div>
                          ))}
                        </div>
                      </details>
                    ) : null}
                  </div>
                ) : null}

                {message.role === 'assistant' && !isLoading ? (
                  <div className="flex gap-2 ml-1 mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => navigator.clipboard.writeText(message.content)}
                      className="p-1.5 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600"
                      title="Copy to clipboard"
                    >
                      <Copy size={14} />
                    </button>
                    <button
                      type="button"
                      disabled={!message.traceId || !previousUserMessage?.content || isSubmittingFeedback}
                      onClick={() => {
                        if (!message.traceId || !previousUserMessage?.content) {
                          return;
                        }
                        onSubmitFeedback?.({
                          traceId: message.traceId,
                          kbId: message.kbId,
                          query: previousUserMessage.content,
                          answer: message.content,
                          rating: 5,
                        });
                      }}
                      className={`p-1.5 rounded transition-colors ${
                        feedbackRating === 5
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'hover:bg-gray-100 text-gray-400 hover:text-gray-600'
                      }`}
                      title="Helpful"
                    >
                      <ThumbsUp size={14} />
                    </button>
                    <button
                      type="button"
                      disabled={!message.traceId || !previousUserMessage?.content || isSubmittingFeedback}
                      onClick={() => {
                        if (!message.traceId || !previousUserMessage?.content) {
                          return;
                        }
                        onSubmitFeedback?.({
                          traceId: message.traceId,
                          kbId: message.kbId,
                          query: previousUserMessage.content,
                          answer: message.content,
                          rating: 1,
                        });
                      }}
                      className={`p-1.5 rounded transition-colors ${
                        feedbackRating === 1
                          ? 'bg-red-100 text-red-700'
                          : 'hover:bg-gray-100 text-gray-400 hover:text-gray-600'
                      }`}
                      title="Not helpful"
                    >
                      <ThumbsDown size={14} />
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
              );
            })()
          ))}
          <div ref={messagesEndRef} />
        </div>
      )}
    </div>
  );
}
