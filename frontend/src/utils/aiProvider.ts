export function formatProviderName(provider?: string | null) {
  if (provider === 'deepseek') return 'DeepSeek'
  if (provider === 'openai') return 'OpenAI'
  return provider?.trim() || ''
}
