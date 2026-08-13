import { describe, expect, it } from 'vitest'
import { compareRunExecutionInputs } from '../runComparison'
import type { FlowRunTrace, TaskHistoryItem } from '@/types'

function run(input: string, fingerprint?: string): TaskHistoryItem {
  const flowRunTrace: FlowRunTrace | null = fingerprint
    ? {
        runId: 'run-1',
        flowId: 'flow-1',
        status: 'completed',
        executionMode: 'single-pass',
        providerCallCount: 1,
        executionInputFingerprint: fingerprint,
        nodes: []
      }
    : null

  return {
    id: crypto.randomUUID(),
    input,
    summary: 'Result',
    result: 'Content',
    flowRunTrace,
    createdAt: '2026-08-13T00:00:00.000Z'
  }
}

describe('run execution input comparison', () => {
  it('verifies equal Provider input by fingerprint when both runs expose one', () => {
    expect(compareRunExecutionInputs(run('source input', 'abc123'), run('different stored text', 'abc123'))).toEqual({
      relation: 'same',
      verification: 'fingerprint'
    })
  })

  it('reports changed Provider input when fingerprints differ', () => {
    expect(compareRunExecutionInputs(run('same text', 'abc123'), run('same text', 'def456'))).toEqual({
      relation: 'different',
      verification: 'fingerprint'
    })
  })

  it('falls back to exact stored input text for legacy runs', () => {
    expect(compareRunExecutionInputs(run('Exact input'), run('Exact input'))).toEqual({
      relation: 'same',
      verification: 'stored-text'
    })
    expect(compareRunExecutionInputs(run('Exact input'), run('Exact input\n'))).toEqual({
      relation: 'different',
      verification: 'stored-text'
    })
  })
})
