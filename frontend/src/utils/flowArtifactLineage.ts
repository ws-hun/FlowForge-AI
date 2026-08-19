import type { FlowNodeArtifactLineage } from '@/types'

const terminationLabels: Record<string, string> = {
  'legacy-record': '旧记录，来源字段不可用',
  'missing-upstream-artifact': '上游产物缺失，路径在此停止',
  'cycle-detected': '检测到循环引用，路径在此停止',
  'unsupported-input-storage': '来源存储类型暂不支持'
}

export function flowArtifactLineageStatusLabel(lineage: FlowNodeArtifactLineage | undefined) {
  if (!lineage) {
    return ''
  }
  if (lineage.complete) {
    return '已追溯到 Flow 快照目标'
  }
  return terminationLabels[lineage.termination] || '来源链不完整'
}
