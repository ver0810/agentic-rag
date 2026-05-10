import { useState, useEffect } from 'react';
import { 
  Activity, 
  Clock, 
  CheckCircle2, 
  XCircle, 
  ChevronRight, 
  ArrowLeft,
  Layers,
  BarChart3,
  Cpu,
  MessageSquare,
  Database,
  RefreshCw,
  Columns
} from 'lucide-react';
import { TraceAPI } from '../api/trace';
import type { RagTraceRun } from '../api/trace';

interface ObservabilityViewProps {
  // Add props if needed
}

const safeParse = (data?: string) => {
  if (!data) return {};
  try {
    return JSON.parse(data);
  } catch {
    return { raw: data };
  }
};

export default function ObservabilityView({}: ObservabilityViewProps) {
  const [traces, setTraces] = useState<RagTraceRun[]>([]);
  const [selectedTrace, setSelectedTrace] = useState<RagTraceRun | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<'list' | 'compare'>('list');

  useEffect(() => {
    fetchTraces();
  }, []);

  const fetchTraces = async () => {
    setIsLoading(true);
    try {
      const response = await TraceAPI.list(50);
      setTraces(response.data);
    } catch (err) {
      console.error('Failed to fetch traces', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectTrace = async (traceId: string) => {
    try {
      const response = await TraceAPI.detail(traceId);
      setSelectedTrace(response.data);
      setViewMode('list');
    } catch (err) {
      console.error('Failed to fetch trace detail', err);
    }
  };

  const toggleCompare = (traceId: string) => {
    setSelectedForCompare(prev => 
      prev.includes(traceId) 
        ? prev.filter(id => id !== traceId) 
        : [...prev, traceId].slice(-2) // Limit to 2 for side-by-side
    );
  };

  const renderStatus = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return <CheckCircle2 size={16} className="text-emerald-500" />;
      case 'ERROR':
        return <XCircle size={16} className="text-red-500" />;
      default:
        return <Clock size={16} className="text-amber-500" />;
    }
  };

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  if (viewMode === 'compare' && selectedForCompare.length >= 2) {
    return (
      <CompareView 
        ids={selectedForCompare} 
        onBack={() => setViewMode('list')} 
      />
    );
  }

  if (selectedTrace) {
    return (
      <TraceDetailView 
        trace={selectedTrace} 
        onBack={() => setSelectedTrace(null)} 
      />
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Observability</h1>
          <p className="text-sm text-gray-500 mt-1">Trace and evaluate RAG pipeline performance</p>
        </div>
        <div className="flex items-center gap-3">
          <button 
            onClick={fetchTraces}
            className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-colors"
          >
            <RefreshCw size={20} className={isLoading ? 'animate-spin' : ''} />
          </button>
          {selectedForCompare.length >= 2 && (
            <button
              onClick={() => setViewMode('compare')}
              className="flex items-center gap-2 px-4 py-2 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-colors shadow-sm active:scale-95 transition-all"
            >
              <Columns size={16} />
              Compare ({selectedForCompare.length})
            </button>
          )}
        </div>
      </div>

      {isLoading && traces.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 bg-gray-50/50 rounded-2xl border border-dashed border-gray-200">
          <div className="w-8 h-8 border-2 border-gray-200 border-t-black rounded-full animate-spin mb-4" />
          <p className="text-sm text-gray-400">Fetching latest traces...</p>
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Trace Name</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Entry</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Duration</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Started At</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {traces.map((trace) => (
                <tr 
                  key={trace.traceId}
                  className={`hover:bg-gray-50 transition-colors group ${selectedForCompare.includes(trace.traceId) ? 'bg-blue-50/30' : ''}`}
                >
                  <td className="px-6 py-4">{renderStatus(trace.status)}</td>
                  <td className="px-6 py-4">
                    <div className="flex flex-col">
                      <span className="text-sm font-semibold text-gray-900">{trace.traceName || 'Unnamed Trace'}</span>
                      <span className="text-xs text-gray-400 font-mono mt-0.5">{trace.traceId.substring(0, 8)}...</span>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500 uppercase">
                      {trace.entryMethod}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600 font-medium">
                    {formatDuration(trace.durationMs)}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {new Date(trace.startTime).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => toggleCompare(trace.traceId)}
                        className={`p-1.5 rounded-md transition-colors ${
                          selectedForCompare.includes(trace.traceId)
                            ? 'bg-blue-100 text-blue-600'
                            : 'hover:bg-gray-100 text-gray-400'
                        }`}
                        title="Add to compare"
                      >
                        <Columns size={16} />
                      </button>
                      <button 
                        onClick={() => handleSelectTrace(trace.traceId)}
                        className="p-1.5 hover:bg-black hover:text-white rounded-md text-gray-400 transition-all"
                      >
                        <ChevronRight size={18} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {traces.length === 0 && !isLoading && (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-400 italic">
                    No traces found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
      )}
    </div>
  );
}

function TraceDetailView({ trace, onBack }: { trace: RagTraceRun, onBack: () => void }) {
  const extra = safeParse(trace.extraData);
  
  return (
    <div className="max-w-5xl mx-auto px-4 py-8 animate-in fade-in slide-in-from-right-4 duration-300">
      <button 
        onClick={onBack}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group"
      >
        <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
        Back to History
      </button>

      <div className="flex items-start justify-between mb-8">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <h1 className="text-3xl font-bold text-gray-900">{trace.traceName || 'Unnamed Trace'}</h1>
            {trace.status === 'SUCCESS' ? (
              <span className="px-2.5 py-1 bg-emerald-50 text-emerald-600 text-[11px] font-bold rounded-full border border-emerald-100">SUCCESS</span>
            ) : (
              <span className="px-2.5 py-1 bg-red-50 text-red-600 text-[11px] font-bold rounded-full border border-red-100">{trace.status}</span>
            )}
          </div>
          <div className="flex items-center gap-4 text-sm text-gray-500">
            <div className="flex items-center gap-1.5">
              <Clock size={14} />
              <span>{new Date(trace.startTime).toLocaleString()}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <BarChart3 size={14} />
              <span>Duration: {trace.durationMs}ms</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Cpu size={14} />
              <span>ID: {trace.traceId}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center gap-2">
              <MessageSquare size={16} />
              Conversation Context
            </h3>
            <div className="space-y-4">
              {extra.query && (
                <div>
                  <label className="text-[11px] font-bold text-gray-400 uppercase">Input Query</label>
                  <p className="text-gray-900 font-medium mt-1">{extra.query}</p>
                </div>
              )}
              {extra.rewrittenQuery && (
                <div>
                  <label className="text-[11px] font-bold text-gray-400 uppercase">Rewritten Query</label>
                  <p className="text-blue-600 font-medium mt-1">{extra.rewrittenQuery}</p>
                </div>
              )}
            </div>
          </div>

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest mb-6 flex items-center gap-2">
              <Layers size={16} />
              Execution Timeline
            </h3>
            <div className="relative border-l-2 border-gray-100 ml-3 pl-8 space-y-8">
              {trace.nodes.map((node, idx) => (
                <div key={node.nodeId} className="relative">
                  <div className={`absolute -left-[41px] top-0 w-6 h-6 rounded-full border-4 border-white flex items-center justify-center ${
                    node.status === 'SUCCESS' ? 'bg-emerald-500' : 'bg-red-500'
                  }`}>
                    {idx + 1}
                  </div>
                  <div className="bg-gray-50 rounded-xl p-4 border border-gray-100 hover:border-gray-200 transition-colors">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] font-bold bg-white px-2 py-0.5 rounded border border-gray-200 text-gray-500 uppercase">
                          {node.nodeType}
                        </span>
                        <h4 className="text-sm font-bold text-gray-900">{node.nodeName}</h4>
                      </div>
                      <span className="text-xs text-gray-500 font-medium">{node.durationMs}ms</span>
                    </div>
                    {node.errorMessage && (
                      <div className="mt-2 p-2 bg-red-50 border border-red-100 rounded text-xs text-red-600 font-mono">
                        {node.errorMessage}
                      </div>
                    )}
                    <NodeExtraData data={node.extraData} type={node.nodeType} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center gap-2">
              <Database size={16} />
              Metadata
            </h3>
            <div className="space-y-4">
              <div>
                <label className="text-[11px] font-bold text-gray-400 uppercase">Entry Method</label>
                <p className="text-sm font-medium mt-1">{trace.entryMethod}</p>
              </div>
              <div>
                <label className="text-[11px] font-bold text-gray-400 uppercase">Conversation ID</label>
                <p className="text-sm font-mono mt-1 text-gray-600">{trace.conversationId}</p>
              </div>
              {extra.kbId && (
                <div>
                  <label className="text-[11px] font-bold text-gray-400 uppercase">Knowledge Base</label>
                  <p className="text-sm font-medium mt-1">{extra.kbId}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function NodeExtraData({ data, type }: { data?: string, type: string }) {
  if (!data) return null;
  const extra = safeParse(data);

  return (
    <div className="mt-3 text-xs space-y-2 overflow-hidden">
      {type === 'rewrite' && extra.rewrittenQuery && (
        <div className="bg-blue-50/50 p-2 rounded">
          <span className="text-[9px] font-bold text-blue-400 uppercase">Result</span>
          <p className="text-blue-700 mt-1">{extra.rewrittenQuery}</p>
        </div>
      )}
      {type === 'retrieve' && (
        <div className="grid grid-cols-3 gap-2">
          <div className="bg-white p-2 rounded border border-gray-100">
            <span className="text-[9px] font-bold text-gray-400 uppercase">Vector</span>
            <p className="text-gray-900 font-bold">{extra.vectorResultCount || 0}</p>
          </div>
          <div className="bg-white p-2 rounded border border-gray-100">
            <span className="text-[9px] font-bold text-gray-400 uppercase">Keyword</span>
            <p className="text-gray-900 font-bold">{extra.keywordResultCount || 0}</p>
          </div>
          <div className="bg-white p-2 rounded border border-gray-100">
            <span className="text-[9px] font-bold text-gray-400 uppercase">Final</span>
            <p className="text-gray-900 font-bold">{extra.filteredResultCount || 0}</p>
          </div>
        </div>
      )}
      {type === 'generate' && extra.answerLength && (
        <div className="flex items-center gap-2">
          <span className="text-[9px] font-bold text-gray-400 uppercase">Answer Length:</span>
          <span className="font-bold text-gray-900">{extra.answerLength} chars</span>
        </div>
      )}
      <details className="mt-2">
        <summary className="cursor-pointer text-gray-400 hover:text-gray-600 transition-colors">Raw Data</summary>
        <pre className="mt-1 p-2 bg-gray-900 text-gray-300 rounded overflow-x-auto text-[10px]">
          {JSON.stringify(extra, null, 2)}
        </pre>
      </details>
    </div>
  );
}

function CompareView({ ids, onBack }: { ids: string[], onBack: () => void }) {
  const [traceData, setTraceData] = useState<RagTraceRun[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const results = await Promise.all(ids.map(id => TraceAPI.detail(id)));
        setTraceData(results.map(r => r.data));
      } catch (err) {
        console.error('Failed to fetch traces for comparison', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, [ids]);

  if (isLoading) {
    return (
      <div className="h-[60vh] flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-black"></div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <button 
            onClick={onBack}
            className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-2 transition-colors"
          >
            <ArrowLeft size={16} />
            Back to History
          </button>
          <h1 className="text-2xl font-bold text-gray-900">Trace Comparison</h1>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {traceData.map((trace, idx) => (
          <div key={trace.traceId} className="space-y-6">
            <div className={`p-4 rounded-2xl border-2 ${idx === 0 ? 'border-blue-100 bg-blue-50/10' : 'border-emerald-100 bg-emerald-50/10'}`}>
              <div className="flex items-center justify-between mb-4">
                <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                  idx === 0 ? 'bg-blue-100 text-blue-600' : 'bg-emerald-100 text-emerald-600'
                }`}>
                  Variant {idx + 1}
                </span>
                <span className="text-xs text-gray-400 font-mono">{trace.traceId.substring(0, 8)}</span>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-1">{trace.traceName}</h3>
              <div className="flex items-center gap-3 text-sm text-gray-500">
                <span className="flex items-center gap-1"><Clock size={14} /> {trace.durationMs}ms</span>
                <span className="flex items-center gap-1"><Activity size={14} /> {trace.status}</span>
              </div>
            </div>

            <div className="bg-white border border-gray-200 rounded-2xl p-6 space-y-6">
              <div>
                <label className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Query</label>
                <p className="text-sm font-medium text-gray-900 mt-1">{safeParse(trace.extraData).query}</p>
              </div>

              <div className="space-y-4">
                <label className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Timeline</label>
                {trace.nodes.map(node => (
                  <div key={node.nodeId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg border border-gray-100">
                    <div className="flex items-center gap-3">
                      <div className={`w-2 h-2 rounded-full ${node.status === 'SUCCESS' ? 'bg-emerald-500' : 'bg-red-500'}`} />
                      <span className="text-xs font-bold text-gray-900">{node.nodeName}</span>
                    </div>
                    <span className="text-[10px] font-bold text-gray-400">{node.durationMs}ms</span>
                  </div>
                ))}
              </div>

              <div>
                <label className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Extra Stats</label>
                <div className="mt-2 grid grid-cols-2 gap-4">
                  {trace.nodes.find(n => n.nodeType === 'retrieve') && (
                    <div className="p-3 bg-gray-50 rounded-lg">
                      <p className="text-[10px] text-gray-400 uppercase font-bold">Chunks</p>
                      <p className="text-xl font-bold text-gray-900">
                        {safeParse(trace.nodes.find(n => n.nodeType === 'retrieve')?.extraData).filteredResultCount || 0}
                      </p>
                    </div>
                  )}
                  {trace.nodes.find(n => n.nodeType === 'generate') && (
                    <div className="p-3 bg-gray-50 rounded-lg">
                      <p className="text-[10px] text-gray-400 uppercase font-bold">Ans Length</p>
                      <p className="text-xl font-bold text-gray-900">
                        {safeParse(trace.nodes.find(n => n.nodeType === 'generate')?.extraData).answerLength || 0}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
