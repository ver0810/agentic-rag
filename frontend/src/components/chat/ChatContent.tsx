import { AlertCircle, Bot, Bug, CheckCircle2, Copy, FileText, Mail, Map, Search, ThumbsDown, ThumbsUp, User } from 'lucide-react';
import type { RefObject } from 'react';
import MessageContent from '../MessageContent';
import type { Message } from '../../api/chat';
import type { RagCitation } from '../../api/knowledge';

interface ChatContentProps {
  isLoadingMessages: boolean;
  messages: Message[];
  isLoading: boolean;
  messagesEndRef: RefObject<HTMLDivElement | null>;
  highlightedTraceId?: string | null;
  feedbackRatings?: Record<string, number>;
  isSubmittingFeedback?: boolean;
  onTraceClick?: (traceId: string) => void;
  onCitationClick?: (citations: RagCitation[]) => void;
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
  highlightedTraceId,
  feedbackRatings,
  isSubmittingFeedback,
  onTraceClick,
  onCitationClick,
  onSubmitFeedback,
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
          {(() => {
            let lastUserMessage: Message | undefined = undefined;
            return messages.map((message, index) => {
              const previousUserMessage = lastUserMessage;
              if (message.role === 'user') {
                lastUserMessage = message;
              }
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

                  {message.traceId ? (
                    <div className="flex flex-wrap gap-2 mt-1 px-1">
                      <button
                        type="button"
                        onClick={() => onTraceClick?.(message.traceId!)}
                        className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700 transition-colors hover:bg-emerald-100"
                      >
                        Trace: {message.traceId.slice(0, 8)}
                      </button>

                      {message.verification ? (
                        <div className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium transition-all animate-in fade-in duration-500 ${
                          message.verification.faithful 
                            ? 'bg-blue-50 text-blue-700 border border-blue-100' 
                            : 'bg-red-50 text-red-700 border border-red-100'
                        }`} title={message.verification.reason}>
                          {message.verification.faithful ? (
                            <CheckCircle2 size={10} className="text-blue-500" />
                          ) : (
                            <AlertCircle size={10} className="text-red-500" />
                          )}
                          <span>{message.verification.faithful ? 'Faithful' : 'Unfaithful'}</span>
                        </div>
                      ) : null}
                    </div>
                  ) : null}

                  {message.role === 'assistant' && ((message.citations?.length ?? 0) > 0 || (message.retrievedChunks?.length ?? 0) > 0) ? (
                    <div className="flex flex-wrap gap-2 mt-2">
                      {(message.citations?.length ?? 0) > 0 ? (
                        <button
                          onClick={() => onCitationClick?.(message.citations!)}
                          className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-gray-200 text-[11px] font-medium text-gray-600 hover:bg-emerald-50 hover:border-emerald-200 hover:text-emerald-700 transition-all shadow-sm group/cite"
                        >
                          <FileText size={12} className="text-gray-400 group-hover/cite:text-emerald-500" />
                          <span>{message.citations?.length} References</span>
                        </button>
                      ) : null}

                      {(message.retrievedChunks?.length ?? 0) > 0 ? (
                        <button
                          onClick={() => onCitationClick?.(message.retrievedChunks!.map(c => ({
                            chunkId: c.chunkId,
                            docId: c.docId,
                            docName: c.docName,
                            chunkIndex: c.chunkIndex,
                            headingPath: c.headingPath,
                            segmentType: c.segmentType,
                            score: c.score,
                            snippet: c.content
                          })))}
                          className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-gray-200 text-[11px] font-medium text-gray-600 hover:bg-blue-50 hover:border-blue-200 hover:text-blue-700 transition-all shadow-sm group/chunk"
                        >
                          <Search size={12} className="text-gray-400 group-hover/chunk:text-blue-500" />
                          <span>{message.retrievedChunks?.length} Chunks</span>
                        </button>
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
            });
          })()}
          <div ref={messagesEndRef} />
        </div>
      )}
    </div>
  );
}
