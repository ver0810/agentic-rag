import { X, FileText, ChevronRight } from 'lucide-react';
import type { RagCitation } from '../../api/knowledge';

interface CitationPanelProps {
  isOpen: boolean;
  onClose: () => void;
  citations: RagCitation[];
}

export default function CitationPanel({ isOpen, onClose, citations }: CitationPanelProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-[400px] bg-white border-l border-gray-200 shadow-2xl z-50 flex flex-col animate-in slide-in-from-right duration-300">
      {/* Header */}
      <div className="h-14 border-b border-gray-100 flex items-center justify-between px-4 bg-gray-50/50">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-emerald-100 flex items-center justify-center text-emerald-700">
            <FileText size={18} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-gray-900">Sources & Citations</h3>
            <p className="text-[10px] text-gray-500 uppercase tracking-wider font-medium">
              {citations.length} References Found
            </p>
          </div>
        </div>
        <button
          onClick={onClose}
          className="p-2 hover:bg-gray-200 rounded-full text-gray-400 hover:text-gray-600 transition-colors"
        >
          <X size={20} />
        </button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {citations.map((citation, idx) => (
          <div key={citation.chunkId} className="group/item">
            <div className="flex gap-3 mb-3">
              <div className="flex-shrink-0 w-6 h-6 rounded bg-gray-100 flex items-center justify-center text-xs font-bold text-gray-500 group-hover/item:bg-emerald-600 group-hover/item:text-white transition-all">
                {idx + 1}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <h4 className="text-sm font-medium text-gray-900 truncate pr-4" title={citation.docName}>
                    {citation.docName || 'Unknown Document'}
                  </h4>
                </div>
                {citation.segmentType ? (
                  <span className="inline-block mt-1 rounded px-1.5 py-0.5 text-[10px] font-medium bg-blue-50 text-blue-600 border border-blue-100 uppercase">
                    {citation.segmentType}
                  </span>
                ) : null}
              </div>
            </div>

            <div className="ml-9 space-y-3">
              {citation.headingPath ? (
                <div className="flex items-center gap-1.5 text-xs text-gray-400 bg-gray-50 rounded-lg px-2 py-1.5 border border-gray-100">
                  <ChevronRight size={12} className="text-gray-300" />
                  <span className="truncate">{citation.headingPath.replace(/ > /g, ' / ')}</span>
                </div>
              ) : null}

              <div className="relative">
                <div className="absolute -left-3 top-0 bottom-0 w-0.5 bg-emerald-100 rounded-full" />
                <div className="text-[13px] text-gray-600 leading-relaxed pl-3 italic">
                  "{citation.snippet}"
                </div>
              </div>

              {citation.score ? (
                <div className="flex items-center gap-2">
                  <div className="h-1 flex-1 bg-gray-100 rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-emerald-500 transition-all duration-500" 
                      style={{ width: `${Math.min(100, citation.score * 100)}%` }}
                    />
                  </div>
                  <span className="text-[10px] font-medium text-gray-400">
                    {Math.round(citation.score * 100)}% Match
                  </span>
                </div>
              ) : null}
            </div>
          </div>
        ))}
      </div>

      {/* Footer */}
      <div className="p-4 border-t border-gray-100 bg-gray-50/30">
        <p className="text-[10px] text-gray-400 text-center leading-tight">
          Citations are automatically extracted from the knowledge base to support the generated answer.
        </p>
      </div>
    </div>
  );
}
