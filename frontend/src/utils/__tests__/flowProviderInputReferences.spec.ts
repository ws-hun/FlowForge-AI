import { describe, expect, it } from 'vitest'
import { flowProviderInputReferenceStatus, flowProviderInputReferenceStatusLabel } from '../flowProviderInputReferences'
import type { FlowProviderInputReference } from '@/types'

function reference(
  overrides: Partial<FlowProviderInputReference> = {}
): FlowProviderInputReference {
  return {
    inputOrder: 1,
    artifactKey: 'flow:objective',
    artifactType: 'flow-objective',
    artifactStorage: 'flow-snapshot',
    artifactState: 'materialized',
    inputResolution: 'compiled-reference',
    contentFingerprint: 'a'.repeat(64),
    sourceArtifactId: null,
    sourceNodeId: null,
    ...overrides
  }
}

describe('Provider input reference status', () => {
  it('identifies the immutable Flow objective snapshot', () => {
    expect(flowProviderInputReferenceStatusLabel(reference())).toBe('Flow 快照')
  })

  it('marks a materialized node source as navigable', () => {
    const value = reference({
      artifactKey: 'node:input-1:context-contribution',
      artifactType: 'context-contribution',
      artifactStorage: 'node-artifact',
      sourceArtifactId: 'artifact-1'
    })
    expect(flowProviderInputReferenceStatus(value)).toBe('navigable')
  })

  it('preserves failed and skipped source states without making them navigable', () => {
    expect(flowProviderInputReferenceStatusLabel(reference({
      artifactStorage: 'node-artifact',
      artifactState: 'failed',
      sourceArtifactId: 'artifact-1'
    }))).toBe('来源失败')
    expect(flowProviderInputReferenceStatusLabel(reference({
      artifactStorage: 'node-artifact',
      artifactState: 'skipped',
      sourceArtifactId: 'artifact-1'
    }))).toBe('已跳过')
  })

  it('keeps a source-less non-snapshot declaration explicit', () => {
    expect(flowProviderInputReferenceStatusLabel(reference({
      artifactStorage: 'node-artifact',
      sourceArtifactId: null
    }))).toBe('已声明')
  })
})
