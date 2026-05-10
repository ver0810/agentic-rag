import React, { useState, useEffect } from 'react';
import { 
  FileText, 
  Upload, 
  Play, 
  Trash2, 
  File, 
  Loader2, 
  AlertCircle,
  CheckCircle2,
  Clock
} from 'lucide-react';
import { KnowledgeAPI } from '../api/knowledge';
import type { KnowledgeBase, KnowledgeDocument } from '../api/knowledge';

interface KnowledgeBaseViewProps {
  kb: KnowledgeBase;
  onDelete: () => void;
}

const KnowledgeBaseView: React.FC<KnowledgeBaseViewProps> = ({ kb, onDelete }) => {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDocuments();
  }, [kb.id]);

  useEffect(() => {
    const hasActiveProcessing = documents.some((doc) => {
      const status = doc.status?.toLowerCase();
      return status === 'queued' || status === 'running';
    });
    if (!hasActiveProcessing) {
      return;
    }
    const timer = window.setInterval(() => {
      fetchDocuments();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [documents, kb.id]);

  const fetchDocuments = async () => {
    setIsLoading(true);
    try {
      const response = await KnowledgeAPI.listDocuments(kb.id);
      setDocuments(response.data);
    } catch (err) {
      console.error('Failed to fetch documents', err);
      setError('Failed to load documents');
    } finally {
      setIsLoading(false);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploading(true);
    setError(null);
    try {
      await KnowledgeAPI.uploadDocument(kb.id, file);
      await fetchDocuments();
    } catch (err) {
      console.error('Upload failed', err);
      setError('Upload failed. Please try again.');
    } finally {
      setIsUploading(false);
      if (e.target) e.target.value = '';
    }
  };

  const handleProcess = async (docId: string) => {
    try {
      await KnowledgeAPI.processDocument(docId);
      // Refresh documents to show processing state
      await fetchDocuments();
    } catch (err) {
      console.error('Failed to start processing', err);
      setError('Failed to start processing');
    }
  };

  const handleDeleteDoc = async (docId: string) => {
    if (!confirm('Are you sure you want to delete this document?')) return;
    try {
      await KnowledgeAPI.deleteDocument(docId);
      setDocuments(docs => docs.filter(d => d.id !== docId));
    } catch (err) {
      console.error('Delete failed', err);
      setError('Failed to delete document');
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status?.toLowerCase()) {
      case 'success': return <CheckCircle2 size={16} className="text-emerald-500" />;
      case 'running': return <Loader2 size={16} className="text-blue-500 animate-spin" />;
      case 'queued': return <Clock size={16} className="text-amber-500" />;
      case 'failed': return <AlertCircle size={16} className="text-red-500" />;
      default: return <Clock size={16} className="text-gray-400" />;
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const displayName = (doc: KnowledgeDocument) => doc.docName || doc.fileName || 'Untitled document';

  return (
    <div className="flex flex-col h-full bg-white">
      <header className="px-8 py-6 border-b border-gray-100">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-2xl font-bold text-gray-900">{kb.name}</h2>
          <button 
            onClick={onDelete}
            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            title="Delete Knowledge Base"
          >
            <Trash2 size={20} />
          </button>
        </div>
        <p className="text-gray-500 text-sm max-w-2xl">{kb.description || 'No description provided.'}</p>
      </header>

      <div className="flex-1 overflow-y-auto p-8">
        <div className="max-w-4xl mx-auto">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-semibold text-gray-800">Documents</h3>
            <div className="relative">
              <input
                type="file"
                id="file-upload"
                className="hidden"
                onChange={handleFileUpload}
                disabled={isUploading}
              />
              <label
                htmlFor="file-upload"
                className={`flex items-center gap-2 px-4 py-2 bg-black text-white rounded-xl text-sm font-medium cursor-pointer transition-all hover:scale-[1.02] active:scale-[0.98] ${isUploading ? 'opacity-50 pointer-events-none' : ''}`}
              >
                {isUploading ? <Loader2 size={18} className="animate-spin" /> : <Upload size={18} />}
                <span>{isUploading ? 'Uploading...' : 'Upload Document'}</span>
              </label>
            </div>
          </div>

          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-100 text-red-700 rounded-xl flex items-center gap-3 text-sm">
              <AlertCircle size={18} />
              {error}
            </div>
          )}

          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-20 text-gray-400">
              <Loader2 size={32} className="animate-spin mb-4" />
              <p>Loading documents...</p>
            </div>
          ) : documents.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 border-2 border-dashed border-gray-100 rounded-2xl bg-gray-50/50">
              <FileText size={48} className="text-gray-200 mb-4" />
              <p className="text-gray-500 font-medium">No documents yet</p>
              <p className="text-gray-400 text-sm mt-1">Upload files to start building your knowledge base.</p>
            </div>
          ) : (
            <div className="grid gap-3">
              {documents.map((doc) => (
                <div 
                  key={doc.id}
                  className="flex items-center gap-4 p-4 border border-gray-100 rounded-2xl hover:border-gray-200 hover:shadow-sm transition-all group bg-white"
                >
                  <div className="w-10 h-10 rounded-xl bg-gray-50 flex items-center justify-center text-gray-400">
                    <File size={20} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-gray-900 truncate">{displayName(doc)}</span>
                      {getStatusIcon(doc.status)}
                    </div>
                    <div className="flex items-center gap-3 text-xs text-gray-400 mt-1">
                      <span>{formatFileSize(doc.fileSize)}</span>
                      <span>•</span>
                      <span>{doc.fileType.toUpperCase()}</span>
                      <span>•</span>
                      <span>{new Date(doc.createTime).toLocaleDateString()}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    {!['queued', 'running', 'success'].includes(doc.status?.toLowerCase()) && (
                      <button 
                        onClick={() => handleProcess(doc.id)}
                        className="p-2 text-gray-400 hover:text-black hover:bg-gray-100 rounded-lg transition-colors"
                        title="Start Processing"
                      >
                        <Play size={18} />
                      </button>
                    )}
                    <button 
                      onClick={() => handleDeleteDoc(doc.id)}
                      className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      title="Delete Document"
                    >
                      <Trash2 size={18} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default KnowledgeBaseView;
