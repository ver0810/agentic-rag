import axios from 'axios';

export interface RagasSampleInput {
  id?: string;
  question: string;
  groundTruth?: string;
}

export interface RagasResult {
  sampleId: string;
  question: string;
  groundTruth: string | null;
  answer: string;
  contexts: string[];
  faithfulness: number | null;
  answerRelevancy: number | null;
  contextPrecision: number | null;
  contextRecall: number | null;
  answerCorrectness: number | null;
}

export interface RagasReport {
  evalRunId: string;
  kbId: string;
  totalSamples: number;
  avgFaithfulness: number | null;
  avgAnswerRelevancy: number | null;
  avgContextPrecision: number | null;
  avgContextRecall: number | null;
  avgAnswerCorrectness: number | null;
  overallScore: number | null;
  results: RagasResult[];
}

export const EvalAPI = {
  run: (kbId: string, samples: RagasSampleInput[]) =>
    axios.post<RagasReport>('/api/eval/ragas/run', { kbId, samples }),
  evaluateSample: (kbId: string, question: string, groundTruth?: string, id?: string) =>
    axios.post<RagasResult>('/api/eval/ragas/sample', { kbId, question, groundTruth, id }),
};
