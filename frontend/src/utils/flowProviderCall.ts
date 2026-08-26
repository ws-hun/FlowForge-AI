import type {
  FlowProviderAttempt,
  FlowProviderAttemptPolicy,
  FlowProviderCall
} from '@/types'
import {
  formatExecutionDuration,
  formatProviderName,
  formatTokenUsage
} from '@/utils/aiProvider'

export function flowProviderCallSource(call: FlowProviderCall | null | undefined) {
  if (!call) {
    return ''
  }
  return [formatProviderName(call.provider), call.model?.trim()].filter(Boolean).join(' · ')
}

export function flowProviderCallMetrics(call: FlowProviderCall | null | undefined) {
  if (!call) {
    return ''
  }
  return [formatTokenUsage(call.totalTokens), formatExecutionDuration(call.durationMs)]
    .filter(Boolean)
    .join(' · ')
}

export function flowProviderCallStatusLabel(call: FlowProviderCall | null | undefined) {
  if (!call) {
    return ''
  }
  return call.status === 'failed' ? '调用失败' : '调用完成'
}

export function flowProviderAttemptTriggerLabel(trigger: FlowProviderAttempt['triggerType']) {
  const labels: Record<FlowProviderAttempt['triggerType'], string> = {
    initial: '初始调用',
    'automatic-retry': '自动重试',
    'manual-recovery': '手动恢复'
  }
  return labels[trigger]
}

export function flowProviderAttemptPolicyTitle(
  policy: FlowProviderAttemptPolicy | null | undefined
) {
  if (!policy) return ''
  const labels: Record<FlowProviderAttemptPolicy['currentState'], string> = {
    'not-recorded': '调用链未记录',
    completed: '调用链已完成',
    failed: '本次调用已停止'
  }
  return labels[policy.currentState]
}

export function flowProviderAttemptPolicyDescription(
  policy: FlowProviderAttemptPolicy | null | undefined
) {
  if (!policy) return ''
  if (policy.currentState === 'not-recorded') {
    return '旧运行没有 Attempt 契约，FlowForge 不会补造重试状态。'
  }
  if (policy.currentState === 'failed'
    && policy.failedRunRecoveryAction === 'create-new-run') {
    return '当前不会在原产物上自动重试；恢复执行会创建一条新的可比较运行。'
  }
  return '当前不会在已完成产物上追加重试。'
}
