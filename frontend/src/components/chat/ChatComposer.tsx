import type { FormEvent } from 'react';
import { Send } from 'lucide-react';

interface ChatComposerProps {
  input: string;
  isLoading: boolean;
  onInputChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
}

export default function ChatComposer({
  input,
  isLoading,
  onInputChange,
  onSubmit,
}: ChatComposerProps) {
  return (
    <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-white via-white/95 to-transparent pt-10 pb-6 px-4">
      <div className="max-w-3xl mx-auto relative group">
        <form
          onSubmit={onSubmit}
          className="relative bg-white border border-[#e5e5e5] rounded-2xl shadow-[0_0_20px_rgba(0,0,0,0.05)] focus-within:border-gray-300 transition-all overflow-hidden"
        >
          <textarea
            value={input}
            onChange={(e) => onInputChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                onSubmit(e);
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
  );
}
