import { describe, expect, it } from 'vitest'
import { flowArtifactLineageStatusLabel } from '@/utils/flowArtifactLineage'
import type { FlowNodeArtifactLineage } from '@/types'

function lineage(overrides: Partial<FlowNodeArtifactLineage> = {}): FlowNodeArtifactLineage {
  return {
    taskId: 'task-1',
    requestedArtifactKey: 'node:output-1:result-document',
    complete: false,
    termination: 'legacy-record',
    path: [],
    ...overrides
  }
}

describe('flow artifact lineage labels', () => {
  it('describes a complete path without implying node-level execution', () => {
    expect(flowArtifactLineageStatusLabel(lineage({
      complete: true,
      termination: 'flow-snapshot'
    }))).toBe('已追溯到 Flow 快照目标')
  })

  it('keeps incomplete and historical paths explicit', () => {
    expect(flowArtifactLineageStatusLabel(lineage())).toBe('旧记录，来源字段不可用')
    expect(flowArtifactLineageStatusLabel(lineage({ termination: 'missing-upstream-artifact' })))
      .toBe('上游产物缺失，路径在此停止')
    expect(flowArtifactLineageStatusLabel(lineage({ termination: 'cycle-detected' })))
      .toBe('检测到循环引用，路径在此停止')
    expect(flowArtifactLineageStatusLabel(lineage({ termination: 'unsupported-input-storage' })))
      .toBe('来源存储类型暂不支持')
    expect(flowArtifactLineageStatusLabel(lineage({ termination: 'future-state' })))
      .toBe('来源链不完整')
  })

  it('returns an empty label before a lineage response exists', () => {
    expect(flowArtifactLineageStatusLabel(undefined)).toBe('')
  })
})
