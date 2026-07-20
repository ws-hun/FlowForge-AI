export function formatProviderName(provider?: string | null) {
  if (provider === 'deepseek') return 'DeepSeek'
  if (provider === 'openai') return 'OpenAI'
  return provider?.trim() || ''
}

export function formatTokenUsage(totalTokens?: number | null) {
  if (typeof totalTokens !== 'number' || totalTokens < 0) return ''
  return `${new Intl.NumberFormat('zh-CN').format(totalTokens)} tokens`
}

export function formatExecutionSource(provider?: string | null, model?: string | null, totalTokens?: number | null) {
  return [formatProviderName(provider), model?.trim(), formatTokenUsage(totalTokens)].filter(Boolean).join(' · ')
}
