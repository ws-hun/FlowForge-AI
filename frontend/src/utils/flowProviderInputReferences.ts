import type { FlowProviderInputReference } from '@/types'

export type FlowProviderInputReferenceStatus = 'snapshot' | 'navigable' | 'failed' | 'skipped' | 'declared'

export function flowProviderInputReferenceStatus(
  reference: FlowProviderInputReference
): FlowProviderInputReferenceStatus {
  if (reference.artifactStorage === 'flow-snapshot') return 'snapshot'
  if (reference.artifactState === 'failed') return 'failed'
  if (reference.artifactState === 'skipped') return 'skipped'
  return reference.sourceArtifactId ? 'navigable' : 'declared'
}

export function flowProviderInputReferenceStatusLabel(reference: FlowProviderInputReference) {
  const labels: Record<FlowProviderInputReferenceStatus, string> = {
    snapshot: 'Flow 快照',
    navigable: '可定位',
    failed: '来源失败',
    skipped: '已跳过',
    declared: '已声明'
  }
  return labels[flowProviderInputReferenceStatus(reference)]
}
