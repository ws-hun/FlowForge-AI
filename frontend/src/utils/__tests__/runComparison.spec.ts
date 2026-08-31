import { describe, expect, it } from 'vitest'
import {
  compareRunExecutionInputs,
  compareRunProviderExecution,
  compareRunProviderInputDeclarations
} from '../runComparison'
import type { FlowArtifactContract, FlowRunTrace, TaskHistoryItem } from '@/types'

function run(
  input: string,
  fingerprint?: string,
  providerInputs?: FlowArtifactContract[],
  overrides: Partial<TaskHistoryItem> = {}
): TaskHistoryItem {
  const flowRunTrace: FlowRunTrace | null = fingerprint || providerInputs
    ? {
        runId: 'run-1',
        flowId: 'flow-1',
        status: 'completed',
        executionMode: 'single-pass',
        providerCallCount: 1,
        executionInputFingerprint: fingerprint,
        executionPlan: providerInputs
          ? {
              version: 'flow-plan-v5',
              scheduling: 'linear',
              steps: [{
                sequence: 1,
                nodeId: 'ai-task-1',
                nodeType: 'ai-task',
                title: '执行任务',
                operation: 'invoke-provider',
                dependsOnNodeIds: [],
                providerBoundary: true,
                providerInputArtifacts: providerInputs
              }]
            }
          : null,
        nodes: []
      }
    : null

  return {
    id: crypto.randomUUID(),
    input,
    summary: 'Result',
    result: 'Content',
    provider: 'deepseek',
    model: 'deepseek-chat',
    status: 'completed',
    flowRunTrace,
    createdAt: '2026-08-13T00:00:00.000Z',
    ...overrides
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

describe('run Provider input declaration comparison', () => {
  const objective: FlowArtifactContract = {
    key: 'flow:objective',
    type: 'flow-objective',
    storage: 'flow-snapshot'
  }
  const context: FlowArtifactContract = {
    key: 'node:input-1:context-contribution',
    type: 'context-contribution',
    storage: 'node-artifact'
  }

  it('compares ordered declarations from saved v5 execution plans', () => {
    expect(compareRunProviderInputDeclarations(
      run('source', 'source-sha', [objective, context]),
      run('target', 'target-sha', [objective, context])
    )).toEqual({
      relation: 'same',
      verification: 'saved-execution-plan',
      sourceInputCount: 2,
      targetInputCount: 2
    })
  })

  it('reports reordered or changed declarations', () => {
    expect(compareRunProviderInputDeclarations(
      run('source', 'same-sha', [objective, context]),
      run('target', 'same-sha', [context, objective])
    ).relation).toBe('different')
  })

  it('does not infer a declaration for legacy runs', () => {
    expect(compareRunProviderInputDeclarations(
      run('legacy', 'same-sha'),
      run('modern', 'same-sha', [objective, context])
    )).toEqual({
      relation: 'unavailable',
      verification: 'saved-execution-plan',
      sourceInputCount: null,
      targetInputCount: 2
    })
  })
})

describe('run Provider execution comparison', () => {
  const objective: FlowArtifactContract = {
    key: 'flow:objective',
    type: 'flow-objective',
    storage: 'flow-snapshot'
  }

  it('confirms the same Provider execution contract for modern Flow runs', () => {
    const source = run('source', 'same-sha', [objective])
    const target = run('target', 'different-sha', [objective])

    expect(compareRunProviderExecution(source, target)).toEqual({
      relation: 'same',
      verification: 'flow-runtime-contract',
      source: {
        provider: 'deepseek',
        model: 'deepseek-chat',
        status: 'completed',
        providerCallCount: 1,
        attemptCount: 1
      },
      target: {
        provider: 'deepseek',
        model: 'deepseek-chat',
        status: 'completed',
        providerCallCount: 1,
        attemptCount: 1
      },
      differences: [],
      unknown: []
    })
  })

  it('reports Provider, model, status, and call-count changes', () => {
    const source = run('source', 'same-sha', [objective])
    const target = run('target', 'same-sha', [objective], {
      provider: 'openai',
      model: 'gpt-4.1',
      status: 'failed',
      flowRunTrace: {
        ...source.flowRunTrace!,
        status: 'failed',
        providerCallCount: 2
      }
    })

    expect(compareRunProviderExecution(source, target)).toMatchObject({
      relation: 'different',
      verification: 'flow-runtime-contract',
      differences: ['provider', 'model', 'status', 'provider-call-count'],
      unknown: ['attempt-count']
    })
  })

  it('keeps legacy attempt count unavailable while comparing saved task metadata', () => {
    const source = run('source', undefined, undefined, { flowRunTrace: null })
    const target = run('target', undefined, undefined, {
      flowRunTrace: null,
      model: 'gpt-4.1'
    })

    expect(compareRunProviderExecution(source, target)).toEqual({
      relation: 'different',
      verification: 'task-metadata',
      source: {
        provider: 'deepseek',
        model: 'deepseek-chat',
        status: 'completed',
        providerCallCount: null,
        attemptCount: null
      },
      target: {
        provider: 'deepseek',
        model: 'gpt-4.1',
        status: 'completed',
        providerCallCount: null,
        attemptCount: null
      },
      differences: ['model'],
      unknown: ['provider-call-count', 'attempt-count']
    })
  })

  it('does not infer Provider evidence for records without execution metadata', () => {
    const source = run('source', undefined, undefined, {
      provider: null,
      model: null,
      status: null,
      flowRunTrace: null
    })
    const target = run('target', undefined, undefined, {
      provider: null,
      model: null,
      status: null,
      flowRunTrace: null
    })

    expect(compareRunProviderExecution(source, target)).toMatchObject({
      relation: 'unavailable',
      verification: 'unavailable',
      differences: [],
      unknown: ['provider-call-count', 'attempt-count']
    })
  })
})
