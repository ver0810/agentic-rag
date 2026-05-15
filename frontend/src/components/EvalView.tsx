import { useState } from 'react';
import {
  Play,
  ChevronRight,
  ArrowLeft,
  LoaderCircle,
  Plus,
  Trash2
} from 'lucide-react';
import { EvalAPI, type RagasSampleInput, type RagasReport, type RagasResult } from '../api/eval';

const formatScore = (value: number | null | undefined) => {
  if (value == null) return '-';
  return (value * 100).toFixed(1) + '%';
};

const scoreColor = (value: number | null | undefined) => {
  if (value == null) return 'text-gray-400';
  if (value >= 0.8) return 'text-emerald-500';
  if (value >= 0.5) return 'text-amber-500';
  return 'text-red-500';
};

export default function EvalView() {
  const [kbId, setKbId] = useState('');
  const [samples, setSamples] = useState<RagasSampleInput[]>([
    { id: 'sample_1', question: '', groundTruth: '' }
  ]);
  const [report, setReport] = useState<RagasReport | null>(null);
  const [singleResult, setSingleResult] = useState<RagasResult | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [viewMode, setViewMode] = useState<'form' | 'report' | 'detail'>('form');

  const addSample = () => {
    setSamples([...samples, { id: `sample_${samples.length + 1}`, question: '', groundTruth: '' }]);
  };

  const removeSample = (index: number) => {
    if (samples.length <= 1) return;
    setSamples(samples.filter((_, i) => i !== index));
  };

  const updateSample = (index: number, field: keyof RagasSampleInput, value: string) => {
    const updated = [...samples];
    updated[index] = { ...updated[index], [field]: value };
    setSamples(updated);
  };

  const handleRunBatch = async () => {
    if (!kbId.trim() || samples.some(s => !s.question.trim())) return;
    setIsRunning(true);
    try {
      const response = await EvalAPI.run(kbId, samples);
      setReport(response.data);
      setViewMode('report');
    } catch (err) {
      console.error('Failed to run evaluation', err);
    } finally {
      setIsRunning(false);
    }
  };

  const handleRunSingle = async (index: number) => {
    const sample = samples[index];
    if (!kbId.trim() || !sample.question.trim()) return;
    setIsRunning(true);
    try {
      const response = await EvalAPI.evaluateSample(kbId, sample.question, sample.groundTruth, sample.id);
      setSingleResult(response.data);
      setViewMode('detail');
    } catch (err) {
      console.error('Failed to evaluate sample', err);
    } finally {
      setIsRunning(false);
    }
  };

  if (viewMode === 'detail' && singleResult) {
    return <ResultDetailView result={singleResult} onBack={() => setViewMode('form')} />;
  }

  if (viewMode === 'report' && report) {
    return <ReportView report={report} onBack={() => setViewMode('form')} />;
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">RAGAS Evaluation</h1>
        <p className="text-sm text-gray-500 mt-1">Evaluate RAG quality using RAGAS metrics</p>
      </div>

      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <h2 className="text-sm font-bold text-gray-900 mb-4">Knowledge Base</h2>
        <input
          value={kbId}
          onChange={(e) => setKbId(e.target.value)}
          placeholder="Enter Knowledge Base ID"
          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
        />
      </div>

      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-bold text-gray-900">Test Samples</h2>
          <button
            onClick={addSample}
            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-600 hover:text-black hover:bg-gray-100 rounded-lg transition-colors"
          >
            <Plus size={14} />
            Add Sample
          </button>
        </div>

        <div className="space-y-4">
          {samples.map((sample, index) => (
            <div key={index} className="border border-gray-100 rounded-xl p-4">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-bold text-gray-400">Sample {index + 1}</span>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleRunSingle(index)}
                    disabled={isRunning || !kbId.trim() || !sample.question.trim()}
                    className="px-2 py-1 text-[10px] font-bold bg-black text-white rounded disabled:bg-gray-200 disabled:text-gray-400"
                  >
                    Run Single
                  </button>
                  {samples.length > 1 && (
                    <button
                      onClick={() => removeSample(index)}
                      className="p-1 text-gray-400 hover:text-red-500 transition-colors"
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <input
                  value={sample.id || ''}
                  onChange={(e) => updateSample(index, 'id', e.target.value)}
                  placeholder="ID (optional)"
                  className="px-3 py-2 border border-gray-200 rounded-lg text-xs"
                />
                <input
                  value={sample.question}
                  onChange={(e) => updateSample(index, 'question', e.target.value)}
                  placeholder="Question *"
                  className="px-3 py-2 border border-gray-200 rounded-lg text-xs"
                />
                <input
                  value={sample.groundTruth || ''}
                  onChange={(e) => updateSample(index, 'groundTruth', e.target.value)}
                  placeholder="Ground Truth (optional)"
                  className="px-3 py-2 border border-gray-200 rounded-lg text-xs"
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <button
        onClick={handleRunBatch}
        disabled={isRunning || !kbId.trim() || samples.some(s => !s.question.trim())}
        className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-black text-white rounded-xl text-sm font-medium hover:bg-gray-800 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
      >
        {isRunning ? <LoaderCircle size={16} className="animate-spin" /> : <Play size={16} />}
        Run All Samples
      </button>
    </div>
  );
}

function ReportView({ report, onBack }: { report: RagasReport; onBack: () => void }) {
  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <button
        onClick={onBack}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group"
      >
        <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
        Back
      </button>

      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Evaluation Report</h1>
        <p className="text-sm text-gray-500 mt-1">
          Run ID: <span className="font-mono">{report.evalRunId}</span> | KB: {report.kbId}
        </p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-6 gap-4 mb-8">
        <MetricCard label="Overall" value={report.overallScore} />
        <MetricCard label="Faithfulness" value={report.avgFaithfulness} />
        <MetricCard label="Relevancy" value={report.avgAnswerRelevancy} />
        <MetricCard label="Precision" value={report.avgContextPrecision} />
        <MetricCard label="Recall" value={report.avgContextRecall} />
        <MetricCard label="Correctness" value={report.avgAnswerCorrectness} />
      </div>

      <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">Sample Results</h3>
      <div className="space-y-3">
        {report.results.map((result) => (
          <ResultCard key={result.sampleId} result={result} />
        ))}
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="bg-white border border-gray-200 p-4 rounded-xl shadow-sm">
      <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{label}</p>
      <p className={`text-xl font-bold mt-1 ${scoreColor(value)}`}>{formatScore(value)}</p>
    </div>
  );
}

function ResultCard({ result }: { result: RagasResult }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div
        className="p-4 flex items-center justify-between cursor-pointer hover:bg-gray-50 transition-colors"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="min-w-0">
          <p className="text-sm font-semibold text-gray-900 truncate">{result.question}</p>
          <div className="flex items-center gap-3 mt-2">
            <ScoreBadge label="F" value={result.faithfulness} />
            <ScoreBadge label="AR" value={result.answerRelevancy} />
            <ScoreBadge label="CP" value={result.contextPrecision} />
            <ScoreBadge label="CR" value={result.contextRecall} />
            <ScoreBadge label="AC" value={result.answerCorrectness} />
          </div>
        </div>
        <ChevronRight size={16} className={`text-gray-400 transition-transform shrink-0 ${expanded ? 'rotate-90' : ''}`} />
      </div>

      {expanded && (
        <div className="px-4 pb-4 border-t border-gray-100">
          <div className="mt-4 space-y-4">
            <div>
              <label className="text-[10px] font-bold text-gray-400 uppercase">Answer</label>
              <p className="mt-1 p-3 bg-gray-50 rounded-lg text-sm text-gray-700">{result.answer || '-'}</p>
            </div>
            {result.groundTruth && (
              <div>
                <label className="text-[10px] font-bold text-gray-400 uppercase">Ground Truth</label>
                <p className="mt-1 p-3 bg-blue-50 rounded-lg text-sm text-gray-700">{result.groundTruth}</p>
              </div>
            )}
            <div>
              <label className="text-[10px] font-bold text-gray-400 uppercase">Contexts ({result.contexts.length})</label>
              <div className="mt-2 space-y-2">
                {result.contexts.map((ctx, idx) => (
                  <p key={idx} className="p-2 bg-gray-50 rounded text-xs text-gray-600 border border-gray-100">
                    {ctx}
                  </p>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ScoreBadge({ label, value }: { label: string; value: number | null }) {
  return (
    <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${value != null ? (value >= 0.8 ? 'bg-emerald-50 text-emerald-600' : value >= 0.5 ? 'bg-amber-50 text-amber-600' : 'bg-red-50 text-red-600') : 'bg-gray-50 text-gray-400'}`}>
      {label}: {formatScore(value)}
    </span>
  );
}

function ResultDetailView({ result, onBack }: { result: RagasResult; onBack: () => void }) {
  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <button
        onClick={onBack}
        className="flex items-center gap-2 text-sm text-gray-500 hover:text-black mb-6 transition-colors group"
      >
        <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
        Back
      </button>

      <h1 className="text-xl font-bold text-gray-900 mb-2">Sample Evaluation Result</h1>
      <p className="text-sm text-gray-500 mb-8">ID: {result.sampleId}</p>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-8">
        <MetricCard label="Faithfulness" value={result.faithfulness} />
        <MetricCard label="Relevancy" value={result.answerRelevancy} />
        <MetricCard label="Precision" value={result.contextPrecision} />
        <MetricCard label="Recall" value={result.contextRecall} />
        <MetricCard label="Correctness" value={result.answerCorrectness} />
      </div>

      <div className="space-y-6">
        <div>
          <h3 className="text-xs font-bold text-gray-400 uppercase mb-2">Question</h3>
          <p className="p-4 bg-white border border-gray-200 rounded-xl text-sm">{result.question}</p>
        </div>
        <div>
          <h3 className="text-xs font-bold text-gray-400 uppercase mb-2">Answer</h3>
          <p className="p-4 bg-white border border-gray-200 rounded-xl text-sm">{result.answer || '-'}</p>
        </div>
        {result.groundTruth && (
          <div>
            <h3 className="text-xs font-bold text-gray-400 uppercase mb-2">Ground Truth</h3>
            <p className="p-4 bg-blue-50 border border-blue-100 rounded-xl text-sm">{result.groundTruth}</p>
          </div>
        )}
        <div>
          <h3 className="text-xs font-bold text-gray-400 uppercase mb-2">Retrieved Contexts</h3>
          <div className="space-y-2">
            {result.contexts.map((ctx, idx) => (
              <p key={idx} className="p-3 bg-white border border-gray-200 rounded-lg text-xs text-gray-700">
                {ctx}
              </p>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
