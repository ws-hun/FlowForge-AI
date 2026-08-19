import type { FlowProviderCall } from '@/types'
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
