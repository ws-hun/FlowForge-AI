import { describe, expect, it } from 'vitest'
import {
  flowProviderAttemptPolicyDescription,
  flowProviderAttemptPolicyTitle,
  flowProviderAttemptTriggerLabel,
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

  it('distinguishes initial, retry, and recovery attempt triggers', () => {
    expect(flowProviderAttemptTriggerLabel('initial')).toBe('初始调用')
    expect(flowProviderAttemptTriggerLabel('automatic-retry')).toBe('自动重试')
    expect(flowProviderAttemptTriggerLabel('manual-recovery')).toBe('手动恢复')
  })

  it('explains the current no-retry recovery boundary', () => {
    const failedPolicy = {
      version: 'flow-provider-attempt-policy-v1',
      currentState: 'failed' as const,
      recordedAttempts: 1,
      automaticRetryEnabled: false,
      sameArtifactRecoveryEnabled: false,
      failedRunRecoveryAction: 'create-new-run' as const
    }

    expect(flowProviderAttemptPolicyTitle(failedPolicy)).toBe('本次调用已停止')
    expect(flowProviderAttemptPolicyDescription(failedPolicy))
      .toBe('当前不会在原产物上自动重试；恢复执行会创建一条新的可比较运行。')
  })

  it('keeps legacy attempt state explicit', () => {
    const legacyPolicy = {
      version: 'flow-provider-attempt-policy-v1',
      currentState: 'not-recorded' as const,
      recordedAttempts: 0,
      automaticRetryEnabled: false,
      sameArtifactRecoveryEnabled: false,
      failedRunRecoveryAction: 'none' as const
    }

    expect(flowProviderAttemptPolicyTitle(legacyPolicy)).toBe('调用链未记录')
    expect(flowProviderAttemptPolicyDescription(legacyPolicy))
      .toBe('旧运行没有 Attempt 契约，FlowForge 不会补造重试状态。')
  })
})
