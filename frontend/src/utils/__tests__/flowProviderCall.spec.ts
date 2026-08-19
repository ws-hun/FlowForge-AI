import { describe, expect, it } from 'vitest'
import {
  flowProviderCallMetrics,
  flowProviderCallSource,
  flowProviderCallStatusLabel
} from '@/utils/flowProviderCall'

describe('flow provider call presentation', () => {
  it('formats the real provider source and metrics', () => {
    const call = {
      status: 'completed' as const,
      provider: 'deepseek',
      model: 'deepseek-chat',
      inputTokens: 120,
      outputTokens: 80,
      totalTokens: 200,
      durationMs: 1840,
      errorMessage: null
    }

    expect(flowProviderCallSource(call)).toBe('DeepSeek · deepseek-chat')
    expect(flowProviderCallMetrics(call)).toBe('200 tokens · 1.8 s')
    expect(flowProviderCallStatusLabel(call)).toBe('调用完成')
  })

  it('keeps failed and partially reported calls honest', () => {
    const call = {
      status: 'failed' as const,
      provider: null,
      model: null,
      totalTokens: null,
      durationMs: 420,
      errorMessage: 'AI API error: provider unavailable'
    }

    expect(flowProviderCallSource(call)).toBe('')
    expect(flowProviderCallMetrics(call)).toBe('420 ms')
    expect(flowProviderCallStatusLabel(call)).toBe('调用失败')
  })

  it('does not invent labels when no provider call was persisted', () => {
    expect(flowProviderCallSource(null)).toBe('')
    expect(flowProviderCallMetrics(undefined)).toBe('')
    expect(flowProviderCallStatusLabel(null)).toBe('')
  })
})
