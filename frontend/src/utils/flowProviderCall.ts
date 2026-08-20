import type { FlowProviderAttempt, FlowProviderCall } from '@/types'
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
