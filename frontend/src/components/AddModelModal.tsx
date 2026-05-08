import React, { useState, useEffect } from 'react'
import axios from 'axios'
import { X, CheckCircle, AlertCircle, ChevronRight, Loader2, Sparkles } from 'lucide-react'

interface AiProviderOption {
  provider: string
  displayName: string
  configured: boolean
  verified: boolean
  enabled: boolean
}

interface AiProviderModel {
  modelCode: string
  displayName: string
  modelType: string
  recommended: boolean
}

interface AiSettingsResponse {
  provider: string
  providerName: string
  hasApiKey: boolean
  apiKeyMasked: string
  verified: boolean
  chatModel: string
  embeddingModel: string
}

interface AiSettingsVerifyResponse {
  success: boolean
  message: string
  provider: string
  verifiedAt: string | null
  chatModels: AiProviderModel[]
  embeddingModels: AiProviderModel[]
}

interface AddModelModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: () => void
}

type Step = 'config' | 'verify' | 'select'

const AddModelModal: React.FC<AddModelModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [step, setStep] = useState<Step>('config')
  const [providers, setProviders] = useState<AiProviderOption[]>([])
  const [selectedProvider, setSelectedProvider] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [isVerifying, setIsVerifying] = useState(false)
  const [verifyResult, setVerifyResult] = useState<{ success: boolean; message: string } | null>(null)
  const [availableModels, setAvailableModels] = useState<AiSettingsVerifyResponse | null>(null)
  const [selectedChatModel, setSelectedChatModel] = useState('')
  const [selectedEmbeddingModel, setSelectedEmbeddingModel] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null

  useEffect(() => {
    if (isOpen) {
      void initialize()
    }
  }, [isOpen])

  const initialize = async () => {
    resetForm()
    await fetchProviders()
  }

  const resetForm = () => {
    setStep('config')
    setApiKey('')
    setVerifyResult(null)
    setAvailableModels(null)
    setSelectedChatModel('')
    setSelectedEmbeddingModel('')
    setError('')
  }

  const fetchProviders = async () => {
    try {
      const [providersResponse, settingsResponse] = await Promise.all([
        axios.get('/ai/providers', {
          headers: { 'X-User-Id': user?.id }
        }),
        axios.get('/user/ai-settings', {
          headers: { 'X-User-Id': user?.id }
        })
      ])
      const providerList: AiProviderOption[] = providersResponse.data
      const settings: AiSettingsResponse = settingsResponse.data
      setProviders(providerList)
      if (settings?.provider) {
        setSelectedProvider(settings.provider)
        setSelectedChatModel(settings.chatModel || '')
        setSelectedEmbeddingModel(settings.embeddingModel || '')
      } else if (providerList.length > 0) {
        setSelectedProvider(providerList[0].provider)
      }
    } catch (err) {
      console.error('Failed to fetch AI settings bootstrap data', err)
    }
  }

  const handleVerify = async () => {
    setIsLoading(true)
    setIsVerifying(true)
    setError('')
    setStep('verify')
    try {
      const response = await axios.post('/user/ai-settings/verify', {
        provider: selectedProvider,
        apiKey
      }, {
        headers: { 'X-User-Id': user?.id }
      })
      setVerifyResult({ success: response.data.success, message: response.data.message })
      if (response.data.success) {
        setAvailableModels(response.data)
        setSelectedChatModel(response.data.chatModels[0]?.modelCode || '')
        setSelectedEmbeddingModel(response.data.embeddingModels[0]?.modelCode || '')
        setStep('select')
      }
    } catch (err: any) {
      setVerifyResult({
        success: false,
        message: err.response?.data?.message || 'Verification service unavailable'
      })
    } finally {
      setIsVerifying(false)
      setIsLoading(false)
    }
  }

  const handleFinalSubmit = async () => {
    setIsLoading(true)
    try {
      const payload: Record<string, string> = {
        provider: selectedProvider,
        chatModel: selectedChatModel,
        embeddingModel: selectedEmbeddingModel
      }
      if (apiKey.trim()) {
        payload.apiKey = apiKey.trim()
      }
      await axios.post('/user/ai-settings/save', payload, {
        headers: { 'X-User-Id': user?.id }
      })
      onSuccess()
      onClose()
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save AI settings')
    } finally {
      setIsLoading(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-semibold">AI Settings</h3>
            <div className="flex gap-1 ml-2">
              <div className={`w-1.5 h-1.5 rounded-full ${step === 'config' ? 'bg-black' : 'bg-gray-200'}`} />
              <div className={`w-1.5 h-1.5 rounded-full ${step === 'verify' ? 'bg-black' : 'bg-gray-200'}`} />
              <div className={`w-1.5 h-1.5 rounded-full ${step === 'select' ? 'bg-black' : 'bg-gray-200'}`} />
            </div>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-100 rounded-lg transition-colors">
            <X size={20} className="text-gray-500" />
          </button>
        </div>

        <div className="p-6">
          {error && (
            <div className="mb-4 p-3 text-sm text-red-500 bg-red-50 border border-red-100 rounded-lg flex items-center gap-2">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          {step === 'config' && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-gray-500 uppercase">Provider</label>
                <select
                  value={selectedProvider}
                  onChange={(e) => setSelectedProvider(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:border-black transition-colors bg-white text-sm"
                >
                  {providers.map((provider) => (
                    <option key={provider.provider} value={provider.provider}>{provider.displayName}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-gray-500 uppercase">API Key</label>
                <input
                  type="password"
                  placeholder="Paste your API key here"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:border-black transition-colors text-sm font-mono"
                />
                <p className="text-[11px] text-gray-400">The provider is preset. You only need to verify your key and choose models.</p>
              </div>

              <button
                onClick={handleVerify}
                disabled={isLoading || !apiKey}
                className="w-full py-3 bg-black text-white rounded-xl font-semibold hover:bg-gray-800 transition-colors disabled:bg-gray-400 flex items-center justify-center gap-2 group"
              >
                {isLoading ? <Loader2 className="animate-spin" size={18} /> : <>Verify <ChevronRight size={18} className="group-hover:translate-x-0.5 transition-transform" /></>}
              </button>
            </div>
          )}

          {step === 'verify' && (
            <div className="py-8 flex flex-col items-center justify-center space-y-4">
              {isVerifying ? (
                <>
                  <div className="w-12 h-12 border-4 border-gray-100 border-t-black rounded-full animate-spin" />
                  <p className="text-sm font-medium text-gray-600">Verifying connectivity...</p>
                </>
              ) : verifyResult ? (
                <>
                  {verifyResult.success ? (
                    <CheckCircle size={48} className="text-emerald-500 animate-in zoom-in duration-300" />
                  ) : (
                    <AlertCircle size={48} className="text-red-500" />
                  )}
                  <div className="text-center">
                    <p className="font-semibold">{verifyResult.success ? 'Success!' : 'Verification Failed'}</p>
                    <p className="text-sm text-gray-500 max-w-[250px] mx-auto mt-1">{verifyResult.message}</p>
                  </div>
                  {!verifyResult.success && (
                    <button
                      onClick={() => setStep('config')}
                      className="text-sm font-medium text-black hover:underline pt-2"
                    >
                      Try again with a different key
                    </button>
                  )}
                </>
              ) : null}
            </div>
          )}

          {step === 'select' && availableModels && (
            <div className="space-y-5 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div className="space-y-2">
                <label className="text-xs font-semibold text-gray-500 uppercase">Choose Chat Model</label>
                <div className="grid grid-cols-1 gap-2 max-h-48 overflow-y-auto pr-1">
                  {availableModels.chatModels.map((model) => (
                    <button
                      key={model.modelCode}
                      onClick={() => setSelectedChatModel(model.modelCode)}
                      className={`flex items-center justify-between p-3 rounded-xl border text-left transition-all ${
                        selectedChatModel === model.modelCode
                          ? 'border-black bg-black/5 ring-1 ring-black'
                          : 'border-gray-100 hover:border-gray-300'
                      }`}
                    >
                      <div className="flex flex-col">
                        <span className="text-sm font-medium">{model.displayName}</span>
                        <span className="text-[10px] text-gray-400 font-mono">{model.modelCode}</span>
                      </div>
                      {model.recommended && <Sparkles size={14} className="text-emerald-500" />}
                    </button>
                  ))}
                </div>
              </div>

              {availableModels.embeddingModels.length > 0 && (
                <div className="space-y-2">
                  <label className="text-xs font-semibold text-gray-500 uppercase">Embedding Model</label>
                  <select
                    value={selectedEmbeddingModel}
                    onChange={(e) => setSelectedEmbeddingModel(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:border-black transition-colors bg-white text-sm"
                  >
                    {availableModels.embeddingModels.map((model) => (
                      <option key={model.modelCode} value={model.modelCode}>{model.displayName}</option>
                    ))}
                  </select>
                </div>
              )}

              <button
                onClick={handleFinalSubmit}
                disabled={isLoading}
                className="w-full py-3 bg-black text-white rounded-xl font-semibold hover:bg-gray-800 transition-colors disabled:bg-gray-400 flex items-center justify-center gap-2"
              >
                {isLoading ? <Loader2 className="animate-spin" size={18} /> : 'Save Settings'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default AddModelModal
