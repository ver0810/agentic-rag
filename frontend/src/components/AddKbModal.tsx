import React, { useState } from 'react';
import { X, Loader2, Database, AlertCircle } from 'lucide-react';
import { KnowledgeAPI } from '../api/knowledge';

interface AddKbModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const AddKbModal: React.FC<AddKbModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setIsLoading(true);
    setError(null);
    try {
      await KnowledgeAPI.create(name.trim(), description.trim());
      setName('');
      setDescription('');
      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Failed to create Knowledge Base', err);
      setError('Failed to create Knowledge Base. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black/40 backdrop-blur-sm animate-in fade-in duration-300"
        onClick={onClose}
      />
      
      {/* Modal Card */}
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 fade-in duration-300">
        <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-black rounded-xl flex items-center justify-center text-white shadow-lg">
              <Database size={20} />
            </div>
            <div>
              <h3 className="text-lg font-bold text-gray-900">Create Base</h3>
              <p className="text-xs text-gray-500">Add a new knowledge source</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-black hover:bg-gray-100 rounded-full transition-colors"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {error && (
            <div className="p-3 bg-red-50 border border-red-100 rounded-xl flex items-center gap-2 text-sm text-red-600 animate-in slide-in-from-top-2">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-2">
            <label htmlFor="kb-name" className="text-sm font-semibold text-gray-700 ml-1">
              Base Name
            </label>
            <input
              id="kb-name"
              type="text"
              required
              autoFocus
              placeholder="e.g., Company Handbook, Project Docs"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-black/5 focus:border-black transition-all text-sm placeholder:text-gray-400"
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="kb-desc" className="text-sm font-semibold text-gray-700 ml-1">
              Description <span className="text-gray-400 font-normal">(Optional)</span>
            </label>
            <textarea
              id="kb-desc"
              rows={3}
              placeholder="What kind of information is in this base?"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-black/5 focus:border-black transition-all text-sm placeholder:text-gray-400 resize-none"
            />
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-3 text-sm font-medium text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 text-sm font-bold text-white bg-black rounded-xl transition-all shadow-lg shadow-black/10 hover:shadow-black/20 hover:scale-[1.02] active:scale-[0.98] ${
                isLoading || !name.trim() ? 'opacity-50 pointer-events-none' : ''
              }`}
            >
              {isLoading ? (
                <>
                  <Loader2 size={18} className="animate-spin" />
                  <span>Creating...</span>
                </>
              ) : (
                <span>Create Base</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AddKbModal;
