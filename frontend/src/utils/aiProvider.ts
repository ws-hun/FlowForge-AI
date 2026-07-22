export function formatProviderName(provider?: string | null) {
  if (provider === 'deepseek') return 'DeepSeek'
  if (provider === 'openai') return 'OpenAI'
  return provider?.trim() || ''
}

export function formatTokenUsage(totalTokens?: number | null) {
  if (typeof totalTokens !== 'number' || totalTokens < 0) return ''
  return `${new Intl.NumberFormat('zh-CN').format(totalTokens)} tokens`
}

export function formatExecutionDuration(durationMs?: number | null) {
  if (typeof durationMs !== 'number' || durationMs < 0) return ''
  if (durationMs < 1000) return `${Math.round(durationMs)} ms`
  if (durationMs < 10000) return `${(durationMs / 1000).toFixed(1)} s`
  return `${Math.round(durationMs / 1000)} s`
}

export function formatExecutionSource(
  provider?: string | null,
  model?: string | null,
  totalTokens?: number | null,
  durationMs?: number | null
) {
  return [
    formatProviderName(provider),
    model?.trim(),
    formatTokenUsage(totalTokens),
    formatExecutionDuration(durationMs)
  ]
    .filter(Boolean)
    .join(' · ')
}
