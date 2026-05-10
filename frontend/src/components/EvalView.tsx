import { useEffect, useState } from 'react';
import {
  Play,
  History,
  CheckCircle2,
  XCircle,
  ChevronRight,
  ArrowLeft,
  RefreshCw,
  Scale,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  LoaderCircle
} from 'lucide-react';
import { EvalAPI } from '../api/eval';

type RagEvalSummary = {
  total: number;
  passed: number;
  failed: number;
  passRate: number;
  answerAccuracy: number;
  citationHitRate: number;
  refusalAccuracy: number;
};

type RagEvalDataset = {
  name: string;
  description?: string;
  caseCount: number;
};

type RagEvalRunSummary = {
  runId: string;
  dataset: string;
  kbId?: string;
  topK?: number;
  executedAt: string;
  summary: RagEvalSummary;
};

type RagEvalCaseResult = {
  caseId: string;
  kbId: string;
  query: string;
  traceId: string;
  rewrittenQuery?: string;
  passed: boolean;
  answerPassed: boolean;
  citationPassed: boolean;
  refusalPassed: boolean;
  expectedAnswerTermCount: number;
  matchedAnswerTermCount: number;
  expectedDocNames: string[];
  matchedDocNames: string[];
  answer: string;
  failureReason?: string;
};

type RagEvalReport = {
  runId: string;
  dataset: string;
  kbIdOverride?: string;
  executedAt: string;
  summary: RagEvalSummary;
  cases: RagEvalCaseResult[];
};

type RagEvalCompare = {
  baseRun: RagEvalRunSummary;
  targetRun: RagEvalRunSummary;
  delta: {
    passRateDelta: number;
    answerAccuracyDelta: number;
    citationHitRateDelta: number;
    refusalAccuracyDelta: number;
    passedDelta: number;
    failedDelta: number;
  };
  caseDiffs: Array<{
    caseId: string;
    basePassed: boolean | null;
    targetPassed: boolean | null;
    change: 'improved' | 'regressed' | 'unchanged' | 'added' | 'removed';
    baseFailureReason?: string;
    targetFailureReason?: string;
    targetTraceId?: string;
  }>;
};

const DEFAULT_TOP_K = 5;

const formatPercent = (value: number | undefined) => `${(value ?? 0).toFixed(1)}%`;

const metricColor = (value: number | undefined) => {
  const safeValue = value ?? 0;
  if (safeValue >= 80) return 'text-emerald-500';
  if (safeValue >= 50) return 'text-amber-500';
  return 'text-red-500';
};

export default function EvalView() {
  const [runs, setRuns] = useState<RagEvalRunSummary[]>([]);
  const [datasets, setDatasets] = useState<RagEvalDataset[]>([]);
  const [selectedRun, setSelectedRun] = useState<RagEvalReport | null>(null);
  const [compareData, setCompareData] = useState<RagEvalCompare | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isRunning, setIsRunning] = useState(false);
  const [selectedForCompare, setSelectedForCompare] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<'list' | 'detail' | 'compare'>('list');
  const [selectedDataset, setSelectedDataset] = useState('');
  const [kbIdOverride, setKbIdOverride] = useState('');
  const [topKOverride, setTopKOverride] = useState(String(DEFAULT_TOP_K));

  useEffect(() => {
    void Promise.all([fetchRuns(), fetchDatasets()]);
  }, []);

  const fetchRuns = async () => {
    setIsLoading(true);
    try {
      const response = await EvalAPI.listRuns();
      setRuns(response.data);
    } catch (err) {
      console.error('Failed to fetch eval runs', err);
    } finally {
      setIsLoading(false);
    }
  };

  const fetchDatasets = async () => {
    try {
      const response = await EvalAPI.listDatasets();
      const items = Array.isArray(response.data) ? response.data : [];
      setDatasets(items);
      if (!selectedDataset && items.length > 0) {
        setSelectedDataset(items[0].name);
      }
    } catch (err) {
      console.error('Failed to fetch eval datasets', err);
    }
  };

  const handleRun = async () => {
    if (!selectedDataset) return;
    setIsRunning(true);
    try {
      const topK = Number.parseInt(topKOverride, 10);
      const response = await EvalAPI.run(
        selectedDataset,
        kbIdOverride.trim() || undefined,
        Number.isFinite(topK) && topK > 0 ? topK : DEFAULT_TOP_K
      );
      setSelectedRun(response.data);
      setViewMode('detail');
      await fetchRuns();
    } catch (err) {
      console.error('Failed to run evaluation', err);
    } finally {
      setIsRunning(false);
    }
  };

  const handleSelectRun = async (runId: string) => {
    setIsLoading(true);
    try {
      const response = await EvalAPI.getRun(runId);
      setSelectedRun(response.data);
      setViewMode('detail');
    } catch (err) {
      console.error('Failed to fetch run report', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCompare = async () => {
    if (selectedForCompare.length < 2) return;
    setIsLoading(true);
    try {
      const response = await EvalAPI.compare(selectedForCompare[0], selectedForCompare[1]);
      setCompareData(response.data);
      setViewMode('compare');
    } catch (err) {
      console.error('Failed to compare runs', err);
    } finally {
      setIsLoading(false);
    }
  };

  const toggleCompareSelect = (runId: string) => {
    setSelectedForCompare((prev) =>
      prev.includes(runId) ? prev.filter((id) => id !== runId) : [...prev, runId].slice(-2)
    );
  };

  if (viewMode === 'compare' && compareData) {
    return <EvalCompareView data={compareData} onBack={() => setViewMode('list')} />;
  }

  if (viewMode === 'detail' && selectedRun) {
    return <EvalReportView report={selectedRun} onBack={() => setViewMode('list')} />;
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">RAG Evaluation</h1>
          <p className="text-sm text-gray-500 mt-1">Benchmarking RAG quality across datasets</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={fetchRuns}
            className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-colors"
            title="Refresh runs"
          >
            <RefreshCw size={20} className={isLoading ? 'animate-spin' : ''} />
          </button>
          {selectedForCompare.length >= 2 && (
            <button
              onClick={handleCompare}
              className="flex items-center gap-2 px-4 py-2 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-all shadow-sm active:scale-95"
            >
              <Scale size={16} />
              Compare ({selectedForCompare.length})
            </button>
          )}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <div className="flex items-center gap-2 mb-4">
          <Play size={16} className="text-gray-500" />
          <h2 className="text-sm font-bold text-gray-900">Run Evaluation</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <label className="flex flex-col gap-2">
            <span className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Dataset</span>
            <select
              value={selectedDataset}
              onChange={(e) => setSelectedDataset(e.target.value)}
              className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-white"
            >
              <option value="">Select dataset</option>
              {datasets.map((dataset) => (
                <option key={dataset.name} value={dataset.name}>
                  {dataset.name} ({dataset.caseCount})
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">KB Override</span>
            <input
              value={kbIdOverride}
              onChange={(e) => setKbIdOverride(e.target.value)}
              placeholder="Optional kbId"
              className="px-3 py-2 border border-gray-200 rounded-lg text-sm"
            />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Top K</span>
            <input
              value={topKOverride}
              onChange={(e) => setTopKOverride(e.target.value)}
              inputMode="numeric"
              className="px-3 py-2 border border-gray-200 rounded-lg text-sm"
            />
          </label>
          <div className="flex flex-col gap-2">
            <span className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Action</span>
            <button
              onClick={handleRun}
              disabled={isRunning || !selectedDataset}
              className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
            >
              {isRunning ? <LoaderCircle size={16} className="animate-spin" /> : <Play size={16} />}
              Run Eval
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Dataset</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Pass Rate</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">KB</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest">Executed At</th>
                <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {runs.map((run) => (
                <tr
                  key={run.runId}
                  className={`hover:bg-gray-50 transition-colors group ${
                    selectedForCompare.includes(run.runId) ? 'bg-blue-50/30' : ''
                  }`}
                >
                  <td className="px-6 py-4">
                    <div className="flex flex-col">
                      <span className="text-sm font-semibold text-gray-900">{run.dataset}</span>
                      <span className="text-xs text-gray-400 font-mono mt-0.5">{run.runId.substring(0, 8)}...</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm font-medium">
                    <span className={`font-bold ${metricColor(run.summary?.passRate)}`}>
                      {formatPercent(run.summary?.passRate)}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    {run.kbId ? (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500 uppercase">
                        KB: {run.kbId.substring(0, 6)}
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">Default</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">{new Date(run.executedAt).toLocaleString()}</td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => toggleCompareSelect(run.runId)}
                        className={`p-1.5 rounded-md transition-colors ${
                          selectedForCompare.includes(run.runId)
                            ? 'bg-blue-100 text-blue-600'
                            : 'hover:bg-gray-100 text-gray-400'
                        }`}
                        title="Add to compare"
                      >
                        <Scale size={16} />
                      </button>
                      <button
                        onClick={() => handleSelectRun(run.runId)}
                        className="p-1.5 hover:bg-black hover:text-white rounded-md text-gray-400 transition-all"
                      >
                        <ChevronRight size={18} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {runs.length === 0 && !isLoading && (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-gray-400 italic">
                    No evaluation runs found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function EvalReportView({ report, onBack }: { report: RagEvalReport; onBack: () => void }) {
  return (
    <div className="max-w-6xl mx-auto px-4 py-8 animate-in fade-in slide-in-from-right-4 duration-300">
      <button
        onClick={onBack}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group"
      >
        <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
        Back to Runs
      </button>

      <div className="flex items-end justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{report.dataset}</h1>
          <p className="text-sm text-gray-500 mt-2 flex items-center gap-2">
            <History size={14} /> Executed at {new Date(report.executedAt).toLocaleString()}
          </p>
        </div>
        <div className="flex items-center gap-6 bg-gray-50 px-6 py-4 rounded-2xl border border-gray-100">
          <StatItem
            label="Pass Rate"
            value={formatPercent(report.summary.passRate)}
            sub={`${report.summary.passed}/${report.summary.total}`}
            color="text-emerald-500"
          />
          <StatItem label="Answer Acc" value={formatPercent(report.summary.answerAccuracy)} color="text-blue-500" />
          <StatItem label="Citation Hit" value={formatPercent(report.summary.citationHitRate)} color="text-purple-500" />
          <StatItem label="Refusal Acc" value={formatPercent(report.summary.refusalAccuracy)} color="text-amber-500" />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4">
        <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-2 px-2">Test Cases</h3>
        {report.cases.map((caseResult) => (
          <CaseResultCard key={caseResult.caseId} result={caseResult} />
        ))}
      </div>
    </div>
  );
}

function StatItem({ label, value, sub, color }: { label: string; value: string; sub?: string; color: string }) {
  return (
    <div className="flex flex-col items-center">
      <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{label}</span>
      <span className={`text-xl font-bold mt-1 ${color}`}>{value}</span>
      {sub && <span className="text-[10px] text-gray-400 font-medium">{sub}</span>}
    </div>
  );
}

function CaseResultCard({ result }: { result: RagEvalCaseResult }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={`bg-white border rounded-2xl transition-all overflow-hidden ${result.passed ? 'border-gray-200' : 'border-red-100'}`}>
      <div
        className="p-4 flex items-center justify-between cursor-pointer hover:bg-gray-50 transition-colors"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-4 min-w-0">
          {result.passed ? <CheckCircle2 size={18} className="text-emerald-500 shrink-0" /> : <XCircle size={18} className="text-red-500 shrink-0" />}
          <div className="truncate">
            <p className="text-sm font-semibold text-gray-900 truncate">{result.query}</p>
            <div className="flex items-center gap-2 mt-1">
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${result.answerPassed ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>ANS</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${result.citationPassed ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>CIT</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${result.refusalPassed ? 'bg-emerald-50 text-emerald-600' : 'bg-gray-50 text-gray-500'}`}>REF</span>
            </div>
          </div>
        </div>
        <ChevronRight size={16} className={`text-gray-400 transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </div>

      {expanded && (
        <div className="px-4 pb-4 pt-2 border-t border-gray-50 animate-in slide-in-from-top-2 duration-200">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="space-y-4">
              <div>
                <label className="text-[10px] font-bold text-gray-400 uppercase">Answer</label>
                <div className="mt-1 p-3 bg-gray-50 rounded-xl text-sm text-gray-700 leading-relaxed border border-gray-100">
                  {result.answer}
                </div>
              </div>
              {result.rewrittenQuery && (
                <div>
                  <label className="text-[10px] font-bold text-gray-400 uppercase">Rewritten Query</label>
                  <p className="mt-1 text-xs text-blue-600 font-medium">{result.rewrittenQuery}</p>
                </div>
              )}
              {result.failureReason && (
                <div>
                  <label className="text-[10px] font-bold text-red-400 uppercase">Failure Reason</label>
                  <p className="mt-1 text-xs text-red-600 font-medium">{result.failureReason}</p>
                </div>
              )}
            </div>
            <div className="space-y-4">
              <div>
                <label className="text-[10px] font-bold text-gray-400 uppercase">Expected Docs</label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {result.expectedDocNames.map((doc, idx) => (
                    <span key={`${doc}-${idx}`} className="px-2 py-1 bg-gray-50 text-gray-600 text-[10px] font-bold rounded-lg border border-gray-100">
                      {doc}
                    </span>
                  ))}
                  {result.expectedDocNames.length === 0 && <span className="text-xs text-gray-400 italic">None</span>}
                </div>
              </div>
              <div>
                <label className="text-[10px] font-bold text-gray-400 uppercase">Matched Docs</label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {result.matchedDocNames.map((doc, idx) => (
                    <span key={`${doc}-${idx}`} className="px-2 py-1 bg-blue-50 text-blue-600 text-[10px] font-bold rounded-lg border border-blue-100">
                      {doc}
                    </span>
                  ))}
                  {result.matchedDocNames.length === 0 && <span className="text-xs text-gray-400 italic">No matches</span>}
                </div>
              </div>
              <div className="pt-4 border-t border-gray-50 flex items-center justify-between">
                <div className="text-xs text-gray-400">
                  Trace ID: <span className="font-mono">{result.traceId || '-'}</span>
                </div>
                <div className="text-xs text-gray-400">
                  Terms: {result.matchedAnswerTermCount}/{result.expectedAnswerTermCount}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function EvalCompareView({ data, onBack }: { data: RagEvalCompare; onBack: () => void }) {
  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <button
        onClick={onBack}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group"
      >
        <ArrowLeft size={16} />
        Back to History
      </button>

      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-3">
          <Scale className="text-blue-500" /> Metrics Comparison
        </h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-12">
        <MetricCard label="Pass Rate" base={data.baseRun.summary.passRate} target={data.targetRun.summary.passRate} delta={data.delta.passRateDelta} />
        <MetricCard label="Answer Accuracy" base={data.baseRun.summary.answerAccuracy} target={data.targetRun.summary.answerAccuracy} delta={data.delta.answerAccuracyDelta} />
        <MetricCard label="Citation Hit" base={data.baseRun.summary.citationHitRate} target={data.targetRun.summary.citationHitRate} delta={data.delta.citationHitRateDelta} />
        <MetricCard label="Refusal Accuracy" base={data.baseRun.summary.refusalAccuracy} target={data.targetRun.summary.refusalAccuracy} delta={data.delta.refusalAccuracyDelta} />
      </div>

      <div className="space-y-4">
        <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-2">Case Diff Analysis</h3>
        <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Case ID</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest text-center">Base</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest text-center">Target</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Change</th>
                <th className="px-6 py-4 text-[10px] font-bold text-gray-400 uppercase tracking-widest">Target Issues</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.caseDiffs.map((diff) => (
                <tr key={diff.caseId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 text-xs font-medium text-gray-900">{diff.caseId}</td>
                  <td className="px-6 py-4 text-center">
                    <PassIcon passed={diff.basePassed} />
                  </td>
                  <td className="px-6 py-4 text-center">
                    <PassIcon passed={diff.targetPassed} />
                  </td>
                  <td className="px-6 py-4">
                    <ChangeBadge change={diff.change} />
                  </td>
                  <td className="px-6 py-4 text-xs text-red-500 font-medium">{diff.targetFailureReason || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, base, target, delta }: { label: string; base: number; target: number; delta: number }) {
  const isPositive = delta > 0;
  const isZero = delta === 0;

  return (
    <div className="bg-white border border-gray-200 p-6 rounded-2xl shadow-sm">
      <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-2">{label}</p>
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-[11px] text-gray-400">Base</p>
          <p className="text-lg font-bold text-gray-900">{formatPercent(base)}</p>
        </div>
        <div>
          <p className="text-[11px] text-gray-400">Target</p>
          <p className="text-lg font-bold text-gray-900">{formatPercent(target)}</p>
        </div>
      </div>
      <div className="mt-3 flex items-center gap-2">
        {isZero ? (
          <>
            <Minus size={14} className="text-gray-400" />
            <span className="text-xs font-bold text-gray-500">No change</span>
          </>
        ) : (
          <>
            <span className={`flex items-center text-xs font-bold ${isPositive ? 'text-emerald-500' : 'text-red-500'}`}>
              {isPositive ? <ArrowUpRight size={14} /> : <ArrowDownRight size={14} />}
              {formatPercent(Math.abs(delta))}
            </span>
          </>
        )}
      </div>
    </div>
  );
}

function PassIcon({ passed }: { passed: boolean | null }) {
  if (passed === null) {
    return <Minus size={16} className="text-gray-300 mx-auto" />;
  }
  return passed ? <CheckCircle2 size={16} className="text-emerald-500 mx-auto" /> : <XCircle size={16} className="text-red-500 mx-auto" />;
}

function ChangeBadge({ change }: { change: RagEvalCompare['caseDiffs'][number]['change'] }) {
  switch (change) {
    case 'improved':
      return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-600 uppercase border border-emerald-100">Improved</span>;
    case 'regressed':
      return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-red-50 text-red-600 uppercase border border-red-100">Regressed</span>;
    case 'added':
      return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-600 uppercase border border-blue-100">Added</span>;
    case 'removed':
      return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-gray-50 text-gray-500 uppercase border border-gray-200">Removed</span>;
    case 'unchanged':
      return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-gray-50 text-gray-500 uppercase border border-gray-200">Unchanged</span>;
    default:
      return null;
  }
}
