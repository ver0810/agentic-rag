import { useEffect, useState, type ReactNode } from 'react';
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  CheckCircle2,
  ChevronRight,
  Clock,
  Columns,
  Cpu,
  Database,
  MessageSquare,
  RefreshCw,
  Search,
  Send,
  Sparkles,
  TriangleAlert,
  XCircle,
  RotateCcw,
  Filter,
  ArrowDownToLine,
  Layers,
  User
} from 'lucide-react';

const nodeTypeStyles: Record<string, { icon: React.ReactNode; color: string; bg: string; border: string }> = {
  rewrite: { icon: <RotateCcw size={14} />, color: 'text-blue-600', bg: 'bg-blue-50', border: 'border-blue-100' },
  retrieve: { icon: <Search size={14} />, color: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-100' },
  rerank: { icon: <Filter size={14} />, color: 'text-amber-600', bg: 'bg-amber-50', border: 'border-amber-100' },
  generate: { icon: <Sparkles size={14} />, color: 'text-purple-600', bg: 'bg-purple-50', border: 'border-purple-100' },
  default: { icon: <Activity size={14} />, color: 'text-gray-600', bg: 'bg-gray-50', border: 'border-gray-100' }
};

import { ObservabilityAPI } from '../api/observability';
import type { RagAlertDispatchResult, RagObservabilitySummary } from '../api/observability';
import { TraceAPI } from '../api/trace';
import type { RagTraceRun } from '../api/trace';

interface ObservabilityViewProps {}

const formatCost = (value?: number) => `$${(value ?? 0).toFixed(4)}`;

const safeParse = (data?: string) => {
  if (!data) return {};
  try {
    return JSON.parse(data);
  } catch {
    return { raw: data };
  }
};

export default function ObservabilityView({}: ObservabilityViewProps) {
  const [metrics, setMetrics] = useState<RagObservabilitySummary | null>(null);
  const [activeAlerts, setActiveAlerts] = useState<RagAlertDispatchResult[]>([]);
  const [traces, setTraces] = useState<RagTraceRun[]>([]);
  const [selectedTrace, setSelectedTrace] = useState<RagTraceRun | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<'list' | 'compare'>('list');

  useEffect(() => {
    void fetchDashboard();
  }, []);

  const fetchDashboard = async () => {
    setIsLoading(true);
    try {
      const [metricsRes, alertsRes, tracesRes] = await Promise.all([
        ObservabilityAPI.getSummary(),
        ObservabilityAPI.listAlerts(),
        TraceAPI.list(50)
      ]);
      setMetrics(metricsRes.data);
      setActiveAlerts(alertsRes.data);
      setTraces(tracesRes.data);
    } catch (err) {
      console.error('Failed to fetch observability dashboard', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectTrace = async (traceId: string) => {
    try {
      const response = await TraceAPI.detail(traceId);
      setSelectedTrace(response.data);
    } catch (err) {
      console.error('Failed to fetch trace detail', err);
    }
  };

  const toggleCompare = (traceId: string) => {
    setSelectedForCompare(prev =>
      prev.includes(traceId)
        ? prev.filter(id => id !== traceId)
        : [...prev, traceId].slice(-2)
    );
  };

  if (viewMode === 'compare' && selectedForCompare.length >= 2) {
    return <CompareView ids={selectedForCompare} onBack={() => setViewMode('list')} />;
  }

  if (selectedTrace) {
    return <TraceDetailView trace={selectedTrace} onBack={() => setSelectedTrace(null)} />;
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8 space-y-10">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 tracking-tight">RAG Observability</h1>
          <p className="text-gray-500 mt-1">Real-time performance monitoring and trace analysis.</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => void fetchDashboard()}
            className="p-2.5 hover:bg-gray-100 rounded-xl text-gray-500 transition-all active:scale-95"
          >
            <RefreshCw size={20} className={isLoading ? 'animate-spin' : ''} />
          </button>
          {selectedForCompare.length >= 2 && (
            <button
              onClick={() => setViewMode('compare')}
              className="flex items-center gap-2 px-5 py-2.5 bg-black text-white rounded-xl text-sm font-semibold hover:bg-gray-800 transition-all shadow-lg shadow-black/10 active:scale-95"
            >
              <Columns size={18} />
              Compare ({selectedForCompare.length})
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <MetricCard icon={<Activity size={16} />} label="System Health" value={metrics?.healthStatus === 'HEALTHY' ? 'Healthy' : 'Warning'} sub="Based on recent query success rate" />
        <MetricCard icon={<Clock size={16} />} label="Avg Latency" value={formatDuration(metrics?.averageLatencyMs ?? 0)} sub="End-to-end RAG response time" />
        <MetricCard icon={<BarChart3 size={16} />} label="Estimated Cost" value={formatCost(metrics?.estimatedTotalCost)} sub={`chat ${formatCost(metrics?.estimatedChatCost)} · embed ${formatCost(metrics?.estimatedEmbeddingCost)}`} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-6">
            <AlertTriangle size={16} className="text-amber-500" />
            <h2 className="text-sm font-bold text-gray-900 uppercase tracking-widest">Active Alerts</h2>
          </div>
          <div className="space-y-4">
            {activeAlerts.length === 0 ? (
              <div className="py-12 text-center bg-gray-50 rounded-2xl border border-dashed border-gray-200">
                <CheckCircle2 size={24} className="mx-auto text-emerald-500 mb-3" />
                <p className="text-sm text-gray-500 font-medium">All systems operational. No active alerts.</p>
              </div>
            ) : (
              activeAlerts.map((alert, idx) => (
                <div key={idx} className="p-4 rounded-2xl border border-gray-100 bg-white shadow-sm flex flex-col gap-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-bold text-gray-900">{alert.metricName}</span>
                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                      alert.status === 'CRITICAL'
                        ? 'bg-red-100 text-red-600'
                        : 'bg-amber-100 text-amber-600'
                    }`}>
                      {alert.status}
                    </span>
                  </div>
                  <div className="text-xs text-gray-600 flex flex-wrap gap-4">
                    <span>Current: {formatAlertValue(alert.currentValue)}</span>
                    {typeof alert.baselineValue === 'number' && <span>Baseline: {formatAlertValue(alert.baselineValue)}</span>}
                    {typeof alert.thresholdValue === 'number' && <span>Threshold: {formatAlertValue(alert.thresholdValue)}</span>}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <Database size={16} className="text-gray-500" />
            <h2 className="text-sm font-bold text-gray-900 uppercase tracking-widest">Window Snapshot</h2>
          </div>
          <div className="space-y-4">
            <SnapshotRow label="Window Start" value={metrics ? new Date(metrics.windowStart).toLocaleString() : '-'} />
            <SnapshotRow label="Window End" value={metrics ? new Date(metrics.windowEnd).toLocaleString() : '-'} />
            <SnapshotRow label="Active Alerts" value={String(activeAlerts.length)} />
            <SnapshotRow label="Successful Queries" value={String(metrics?.successfulQueries ?? 0)} />
            <SnapshotRow label="Failed Queries" value={String(metrics?.failedQueries ?? 0)} />
            <SnapshotRow label="Avg Ingestion" value={`${(metrics?.averageIngestionDurationMs ?? 0).toFixed(0)}ms`} />
            <SnapshotRow label="Embedding Tokens" value={`${metrics?.estimatedQueryEmbeddingTokens ?? 0} + ${metrics?.estimatedIngestionEmbeddingTokens ?? 0}`} />
          </div>
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-bold text-gray-900 uppercase tracking-widest">Trace History</h2>
            <p className="text-xs text-gray-400 mt-1 font-medium">Inspect execution paths and compare retrieval/generation traces.</p>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Status</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Trace Name</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Entry</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Duration</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Started At</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {traces.map((trace) => {
                const isSelected = selectedForCompare.includes(trace.traceId);
                return (
                  <tr
                    key={trace.traceId}
                    className={`hover:bg-gray-50 transition-colors group ${isSelected ? 'bg-blue-50/40' : ''}`}
                  >
                    <td className="px-6 py-4">{renderStatus(trace.status)}</td>
                    <td className="px-6 py-4">
                      <div className="flex flex-col">
                        <span className="text-sm font-semibold text-gray-900">{trace.traceName || 'Unnamed Trace'}</span>
                        <span className="text-[10px] text-gray-400 font-mono mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity">{trace.traceId}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500 uppercase tracking-tight">
                        {trace.entryMethod}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-700 font-semibold">{formatDuration(trace.durationMs)}</td>
                    <td className="px-6 py-4 text-sm text-gray-500 font-medium">{new Date(trace.startTime).toLocaleString()}</td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => toggleCompare(trace.traceId)}
                          className={`p-2 rounded-xl transition-all ${
                            isSelected
                              ? 'bg-blue-100 text-blue-600 shadow-sm'
                              : 'hover:bg-gray-100 text-gray-400'
                          }`}
                        >
                          <Columns size={16} />
                        </button>
                        <button
                          onClick={() => void handleSelectTrace(trace.traceId)}
                          className="p-2 hover:bg-black hover:text-white rounded-xl text-gray-400 transition-all active:scale-90"
                        >
                          <ChevronRight size={18} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {traces.length === 0 && !isLoading && (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-400 italic">No traces found</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function MetricCard({ icon, label, value, sub }: { icon: ReactNode; label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm hover:shadow-md transition-shadow">
      <div className="flex items-center gap-2 text-gray-500 mb-3">
        <div className="w-6 h-6 rounded-lg bg-gray-50 flex items-center justify-center text-gray-400">
          {icon}
        </div>
        <span className="text-[11px] font-bold uppercase tracking-widest">{label}</span>
      </div>
      <div className="text-2xl font-bold text-gray-900">{value}</div>
      {sub && <div className="text-xs text-gray-400 mt-2 font-medium">{sub}</div>}
    </div>
  );
}

function SnapshotRow({ label, value, isMono }: { label: string; value: string; isMono?: boolean }) {
  return (
    <div className="flex flex-col gap-1 py-0.5">
      <span className="text-[11px] font-bold text-gray-400 uppercase tracking-widest leading-none">{label}</span>
      <span className={`text-sm font-semibold text-gray-900 break-all ${isMono ? 'font-mono text-xs opacity-70' : ''}`}>
        {value || '-'}
      </span>
    </div>
  );
}

function renderStatus(status: string) {
  switch (status) {
    case 'SUCCESS':
      return <CheckCircle2 size={16} className="text-emerald-500" />;
    case 'ERROR':
      return <XCircle size={16} className="text-red-500" />;
    default:
      return <Clock size={16} className="text-amber-500" />;
  }
}

function formatDuration(ms: number) {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function formatAlertValue(value?: number) {
  if (typeof value !== 'number') return '-';
  if (value >= 0 && value <= 1) return `${(value * 100).toFixed(1)}%`;
  return value.toFixed(3);
}

function TraceDetailView({ trace, onBack }: { trace: RagTraceRun; onBack: () => void }) {
  const extra = safeParse(trace.extraData);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8 animate-in fade-in slide-in-from-right-4 duration-300">
      <button onClick={onBack} className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group">
        <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
        Back to History
      </button>

      <div className="flex items-start justify-between mb-8">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <h1 className="text-3xl font-bold text-gray-900 tracking-tight">{trace.traceName || 'Unnamed Trace'}</h1>
            {trace.status === 'SUCCESS'
              ? <span className="px-2.5 py-1 bg-emerald-50 text-emerald-600 text-[11px] font-bold rounded-full border border-emerald-100">SUCCESS</span>
              : <span className="px-2.5 py-1 bg-red-50 text-red-600 text-[11px] font-bold rounded-full border border-red-100">{trace.status}</span>}
          </div>
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-500">
            <div className="flex items-center gap-1.5"><Clock size={14} /><span>{new Date(trace.startTime).toLocaleString()}</span></div>
            <div className="flex items-center gap-1.5"><BarChart3 size={14} /><span>{trace.durationMs}ms</span></div>
            <div className="flex items-center gap-1.5 text-gray-400">
              <Cpu size={14} />
              <span className="font-mono text-[11px] truncate max-w-[200px]" title={trace.traceId}>ID: {trace.traceId}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white border border-gray-200 rounded-3xl p-8 shadow-sm overflow-hidden relative">
            <div className="absolute top-0 right-0 p-4 opacity-[0.03] pointer-events-none">
              <MessageSquare size={120} />
            </div>

            <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest mb-8 flex items-center gap-2">
              <MessageSquare size={16} className="text-blue-500" />
              Conversation Context
            </h3>
            
            <div className="space-y-8">
              {extra.query && (
                <div className="flex flex-col gap-3">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-full bg-blue-50 flex items-center justify-center text-blue-500">
                      <User size={12} />
                    </div>
                    <span className="text-[11px] font-bold text-gray-400 uppercase tracking-wider">User Input</span>
                  </div>
                  <div className="bg-gray-50 rounded-2xl rounded-tl-none p-5 border border-gray-100 shadow-inner">
                    <p className="text-gray-800 text-base font-medium leading-relaxed italic">
                      "{extra.query}"
                    </p>
                  </div>
                </div>
              )}
              
              {extra.rewrittenQuery && (
                <div className="flex flex-col gap-3 ml-4 border-l-2 border-blue-50 pl-6 relative">
                  <div className="absolute -left-[2px] top-0 h-8 w-[2px] bg-blue-400"></div>
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-full bg-emerald-50 flex items-center justify-center text-emerald-500">
                      <Sparkles size={12} />
                    </div>
                    <span className="text-[11px] font-bold text-emerald-600/70 uppercase tracking-wider">Rewritten for Retrieval</span>
                  </div>
                  <div className="bg-blue-50/30 rounded-2xl p-5 border border-blue-100/50">
                    <p className="text-blue-700 text-sm font-semibold leading-relaxed">
                      {extra.rewrittenQuery}
                    </p>
                  </div>
                </div>
              )}

              {(!extra.query && !extra.rewrittenQuery) && (
                <div className="py-10 text-center">
                  <p className="text-sm text-gray-400 italic font-medium">No context data captured for this trace.</p>
                </div>
              )}
            </div>
          </div>

          <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
            <div className="flex items-center justify-between mb-8">
              <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest flex items-center gap-2">
                <Layers size={16} />
                Execution Timeline
              </h3>
              <span className="text-[10px] font-bold text-gray-400 bg-gray-50 px-2 py-1 rounded border border-gray-100">
                {trace.nodes.length} STEPS
              </span>
            </div>
            <div className="relative border-l-2 border-dashed border-gray-100 ml-4 pl-10 space-y-10">
              {trace.nodes.map((node, idx) => {
                const style = nodeTypeStyles[node.nodeType] || nodeTypeStyles.default;
                return (
                  <div key={node.nodeId} className="relative group">
                    <div className={`absolute -left-[53px] top-0 w-8 h-8 rounded-full border-4 border-white shadow-sm flex items-center justify-center transition-transform group-hover:scale-110 z-10 ${
                      node.status === 'SUCCESS' ? 'bg-black text-white' : 'bg-red-500 text-white'
                    }`}>
                      {node.status === 'SUCCESS' ? style.icon : <XCircle size={14} />}
                    </div>
                    
                    <div className="bg-white rounded-2xl p-5 border border-gray-100 shadow-sm hover:shadow-md hover:border-gray-200 transition-all">
                      <div className="flex items-start justify-between mb-4">
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-2">
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border uppercase tracking-wider ${style.bg} ${style.color} ${style.border}`}>
                              {node.nodeType}
                            </span>
                            <h4 className="text-sm font-bold text-gray-900 leading-none">{node.nodeName}</h4>
                          </div>
                          <span className="text-[10px] text-gray-400 font-mono">ID: {node.nodeId.substring(0, 8)}</span>
                        </div>
                        <div className="flex flex-col items-end gap-1">
                          <span className="text-xs font-bold text-gray-900">{node.durationMs}ms</span>
                          <div className="w-16 h-1 bg-gray-100 rounded-full overflow-hidden">
                            <div 
                              className={`h-full ${node.durationMs > 1000 ? 'bg-amber-400' : 'bg-emerald-400'}`} 
                              style={{ width: `${Math.min((node.durationMs / trace.durationMs) * 100, 100)}%` }}
                            />
                          </div>
                        </div>
                      </div>
                      
                      {node.errorMessage && (
                        <div className="mb-4 p-3 bg-red-50 border border-red-100 rounded-xl text-xs text-red-600 font-medium flex gap-2">
                          <TriangleAlert size={14} className="shrink-0" />
                          {node.errorMessage}
                        </div>
                      )}
                      
                      <NodeExtraData data={node.extraData} type={node.nodeType} />
                    </div>
                  </div>
                );
              })}
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
              <SnapshotRow label="Entry Method" value={trace.entryMethod} />
              <SnapshotRow label="Conversation ID" value={trace.conversationId} isMono />
              <SnapshotRow label="Answer State" value={extra.answerState || '-'} />
              <SnapshotRow label="Estimated Cost" value={formatCost(extra.estimatedTotalCost)} />
              <SnapshotRow label="Estimated Tokens" value={String(extra.estimatedTotalTokens || 0)} />
              {extra.kbId && <SnapshotRow label="Knowledge Base" value={extra.kbId} isMono />}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function NodeExtraData({ data, type }: { data?: string; type: string }) {
  if (!data) return null;
  const extra = safeParse(data);

  return (
    <div className="mt-2 space-y-3">
      {type === 'rewrite' && extra.rewrittenQuery && (
        <div className="bg-blue-50/50 border border-blue-100 p-3 rounded-xl">
          <div className="flex items-center gap-1.5 mb-1.5">
            <RotateCcw size={12} className="text-blue-500" />
            <span className="text-[10px] font-bold text-blue-400 uppercase tracking-widest">Optimized Query</span>
          </div>
          <p className="text-sm font-medium text-blue-700 leading-relaxed">{extra.rewrittenQuery}</p>
        </div>
      )}
      
      {type === 'retrieve' && (
        <div className="grid grid-cols-3 gap-3">
          <NodeMetric label="Vector" value={extra.vectorResultCount || 0} icon={<Database size={10} />} />
          <NodeMetric label="Keyword" value={extra.keywordResultCount || 0} icon={<Search size={10} />} />
          <NodeMetric label="Final" value={extra.filteredResultCount || 0} icon={<Filter size={10} />} color="emerald" />
        </div>
      )}
      
      {type === 'generate' && (
        <div className="grid grid-cols-2 gap-3">
          <NodeMetric label="Answer Length" value={`${extra.answerLength || 0} chars`} icon={<MessageSquare size={10} />} />
          <NodeMetric label="Output Tokens" value={extra.estimatedChatOutputTokens || 0} icon={<Sparkles size={10} />} color="purple" />
        </div>
      )}
      
      <details className="group/raw">
        <summary className="cursor-pointer text-[10px] font-bold text-gray-400 hover:text-gray-600 transition-colors uppercase tracking-widest list-none flex items-center gap-1">
          <ChevronRight size={10} className="transition-transform group-open/raw:rotate-90" />
          Raw Metadata
        </summary>
        <div className="mt-2 relative">
          <pre className="p-4 bg-gray-900 text-gray-300 rounded-xl overflow-x-auto text-[10px] font-mono leading-relaxed shadow-inner">
            {JSON.stringify(extra, null, 2)}
          </pre>
        </div>
      </details>
    </div>
  );
}

function NodeMetric({ label, value, icon, color = 'gray' }: { label: string; value: string | number; icon?: React.ReactNode; color?: 'emerald' | 'purple' | 'gray' }) {
  const colors = {
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    purple: 'bg-purple-50 text-purple-700 border-purple-100',
    gray: 'bg-gray-50 text-gray-700 border-gray-100'
  };

  return (
    <div className={`p-3 rounded-xl border flex flex-col gap-1 transition-colors hover:bg-white ${colors[color]}`}>
      <div className="flex items-center gap-1.5 opacity-60">
        {icon}
        <span className="text-[9px] font-bold uppercase tracking-widest leading-none">{label}</span>
      </div>
      <p className="text-base font-bold leading-none mt-1">{value}</p>
    </div>
  );
}

function CompareView({ ids, onBack }: { ids: string[]; onBack: () => void }) {
  const [traceData, setTraceData] = useState<RagTraceRun[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const results = await Promise.all(ids.map(id => TraceAPI.detail(id)));
        setTraceData(results.map(r => r.data));
      } finally {
        setIsLoading(false);
      }
    };
    void fetchData();
  }, [ids]);

  if (isLoading) {
    return <div className="h-[60vh] flex items-center justify-center"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-black" /></div>;
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <button onClick={onBack} className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors">
        <ArrowLeft size={16} />
        Back to History
      </button>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {traceData.map((trace, idx) => {
          const extra = safeParse(trace.extraData);
          return (
            <div key={trace.traceId} className="space-y-6">
              <div className={`p-4 rounded-2xl border-2 ${idx === 0 ? 'border-blue-100 bg-blue-50/10' : 'border-emerald-100 bg-emerald-50/10'}`}>
                <div className="flex items-center justify-between mb-4">
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${idx === 0 ? 'bg-blue-100 text-blue-600' : 'bg-emerald-100 text-emerald-600'}`}>Variant {idx + 1}</span>
                  <span className="text-xs text-gray-400 font-mono">{trace.traceId.substring(0, 8)}</span>
                </div>
                <h3 className="text-lg font-bold text-gray-900 mb-1">{trace.traceName}</h3>
                <div className="flex items-center gap-3 text-sm text-gray-500">
                  <span className="flex items-center gap-1"><Clock size={14} /> {trace.durationMs}ms</span>
                  <span className="flex items-center gap-1"><Activity size={14} /> {extra.answerState || trace.status}</span>
                </div>
              </div>
              <div className="bg-white border border-gray-200 rounded-2xl p-6 space-y-4">
                <SnapshotRow label="Query" value={extra.query || '-'} />
                <div className="grid grid-cols-2 gap-4">
                  <SnapshotRow label="Retrieved Chunks" value={String(extra.retrievedChunkCount || 0)} />
                  <SnapshotRow label="Estimated Tokens" value={String(extra.estimatedTotalTokens || 0)} />
                </div>
                <SnapshotRow label="Estimated Cost" value={formatCost(extra.estimatedTotalCost)} />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
