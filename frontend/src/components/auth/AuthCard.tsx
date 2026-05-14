import type { FormEventHandler, ReactNode } from 'react';
import { Bot } from 'lucide-react';
import { Link } from 'react-router-dom';

interface AuthCardProps {
  title: string;
  error?: string;
  isLoading: boolean;
  submitLabel: string;
  loadingLabel: string;
  footerText: string;
  footerLinkLabel: string;
  footerLinkTo: string;
  onSubmit: FormEventHandler<HTMLFormElement>;
  children: ReactNode;
}

export default function AuthCard({
  title,
  error,
  isLoading,
  submitLabel,
  loadingLabel,
  footerText,
  footerLinkLabel,
  footerLinkTo,
  onSubmit,
  children,
}: AuthCardProps) {
  return (
    <div className="min-h-screen bg-white flex flex-col items-center justify-center px-4">
      <div className="max-w-[400px] w-full space-y-8">
        <div className="flex flex-col items-center">
          <div className="w-12 h-12 bg-white border border-gray-200 rounded-xl flex items-center justify-center shadow-sm text-black mb-6">
            <Bot size={24} strokeWidth={1.5} />
          </div>
          <h1 className="text-[32px] font-bold tracking-tight text-[#171717] mb-2">{title}</h1>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          {error ? (
            <div className="p-3 text-sm text-red-500 bg-red-50 border border-red-100 rounded-lg">
              {error}
            </div>
          ) : null}

          {children}

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-3 px-4 bg-black text-white rounded-xl font-semibold hover:bg-[#2f2f2f] transition-colors disabled:bg-gray-400 mt-2"
          >
            {isLoading ? loadingLabel : submitLabel}
          </button>
        </form>

        <p className="text-center text-[14px] text-[#171717]">
          {footerText}{' '}
          <Link to={footerLinkTo} className="text-emerald-600 hover:underline font-medium">
            {footerLinkLabel}
          </Link>
        </p>
      </div>
    </div>
  );
}
