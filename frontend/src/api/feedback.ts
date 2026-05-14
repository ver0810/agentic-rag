import axios from 'axios';

export interface FeedbackRequest {
  traceId: string;
  kbId?: string;
  query: string;
  answer: string;
  rating: number;
  comment?: string;
}

export const FeedbackAPI = {
  submit: (payload: FeedbackRequest) => axios.post('/api/rag/feedback', payload),
};
