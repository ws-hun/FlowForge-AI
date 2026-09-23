import { describe, expect, it } from 'vitest'
import {
  compareRunExecutionInputs,
  compareRunFlowOrigins,
  compareRunFlowSnapshots,
  compareRunProviderExecution,
  compareRunProviderInputDeclarations
} from '../runComparison'
import type { FlowArtifactContract, FlowRunSnapshot, FlowRunTrace, TaskHistoryItem } from '@/types'

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

function snapshot(overrides: Partial<FlowRunSnapshot> = {}): FlowRunSnapshot {
  return {
    flowId: 'flow-1',
    title: 'Launch Flow',
    description: 'Prepare the launch',
    nodes: [],
    flowUpdatedAt: '2026-09-21T00:00:00.000Z',
    runtimeContext: '',
    variableValues: {},
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

describe('run Flow origin comparison', () => {
  it('confirms the same executed Flow and immutable Result origin', () => {
    const sourceSnapshot = snapshot({
      sourceTaskId: 'task-origin',
      sourceTaskSummary: 'Launch recommendation',
      sourcePromptId: 'prompt-origin',
      sourcePromptTitle: 'Launch pattern'
    })

    expect(compareRunFlowOrigins(
      run('source', undefined, undefined, { flowRunSnapshot: sourceSnapshot }),
      run('target', undefined, undefined, { flowRunSnapshot: { ...sourceSnapshot } })
    )).toEqual({
      relation: 'same',
      flowRelation: 'same',
      originRelation: 'same',
      verification: 'saved-flow-snapshot',
      source: {
        flowId: 'flow-1',
        flowTitle: 'Launch Flow',
        originKind: 'result',
        originId: 'task-origin',
        originTitle: 'Launch recommendation',
        intermediatePromptId: 'prompt-origin',
        sourceFlowVersionId: null,
        sourceFlowVersionNumber: null
      },
      target: {
        flowId: 'flow-1',
        flowTitle: 'Launch Flow',
        originKind: 'result',
        originId: 'task-origin',
        originTitle: 'Launch recommendation',
        intermediatePromptId: 'prompt-origin',
        sourceFlowVersionId: null,
        sourceFlowVersionNumber: null
      },
      differences: []
    })
  })

  it('distinguishes Flow identity while recognizing a shared Prompt origin', () => {
    const source = run('source', undefined, undefined, {
      flowRunSnapshot: snapshot({
        flowId: 'flow-a',
        sourcePromptId: 'prompt-origin',
        sourcePromptTitle: 'Launch pattern'
      })
    })
    const target = run('target', undefined, undefined, {
      flowRunSnapshot: snapshot({
        flowId: 'flow-b',
        title: 'Launch Flow variant',
        sourcePromptId: 'prompt-origin',
        sourcePromptTitle: 'Launch pattern'
      })
    })

    expect(compareRunFlowOrigins(source, target)).toMatchObject({
      relation: 'different',
      flowRelation: 'different',
      originRelation: 'same',
      differences: ['flow-asset']
    })
  })

  it('reports different saved origins without comparing mutable titles', () => {
    const source = run('source', undefined, undefined, {
      flowRunSnapshot: snapshot({
        sourceFlowId: 'parent-flow',
        sourceFlowTitle: 'Original title',
        sourceFlowVersionId: 'version-1',
        sourceFlowVersionNumber: 1
      })
    })
    const target = run('target', undefined, undefined, {
      flowRunSnapshot: snapshot({
        sourceFlowId: 'parent-flow',
        sourceFlowTitle: 'Renamed title',
        sourceFlowVersionId: 'version-2',
        sourceFlowVersionNumber: 2
      })
    })

    expect(compareRunFlowOrigins(source, target)).toMatchObject({
      relation: 'different',
      flowRelation: 'same',
      originRelation: 'different',
      differences: ['origin-version']
    })
  })

  it('keeps missing snapshots and unrecorded legacy origins unavailable', () => {
    expect(compareRunFlowOrigins(
      run('source', undefined, undefined, { flowRunSnapshot: null }),
      run('target', undefined, undefined, { flowRunSnapshot: snapshot() })
    )).toMatchObject({
      relation: 'unavailable',
      flowRelation: 'unavailable',
      originRelation: 'unavailable'
    })

    expect(compareRunFlowOrigins(
      run('source', undefined, undefined, { flowRunSnapshot: snapshot() }),
      run('target', undefined, undefined, { flowRunSnapshot: snapshot() })
    )).toMatchObject({
      relation: 'unavailable',
      flowRelation: 'same',
      originRelation: 'unavailable',
      differences: []
    })

    expect(compareRunFlowOrigins(
      run('source', undefined, undefined, {
        flowRunSnapshot: snapshot({ sourceTaskId: 'task-origin' })
      }),
      run('target', undefined, undefined, {
        flowRunSnapshot: snapshot({ sourceTaskId: 'task-origin' })
      })
    )).toMatchObject({
      relation: 'unavailable',
      flowRelation: 'same',
      originRelation: 'unavailable'
    })
  })
})

describe('run Flow snapshot comparison', () => {
  const node = (id: string, title: string, content = title): FlowRunSnapshot['nodes'][number] => ({
    id,
    type: 'prompt',
    title,
    description: `${title} description`,
    content
  })

  it('confirms equal immutable Flow execution context', () => {
    const saved = snapshot({
      nodes: [node('a', 'Intent'), node('b', 'Draft')],
      runtimeContext: 'Prepare a launch memo',
      variableValues: { audience: 'product teams' }
    })

    expect(compareRunFlowSnapshots(
      run('source', undefined, undefined, { flowRunSnapshot: saved }),
      run('target', undefined, undefined, { flowRunSnapshot: structuredClone(saved) })
    )).toEqual({
      relation: 'same',
      verification: 'saved-flow-snapshot',
      sourceNodeCount: 2,
      targetNodeCount: 2,
      changes: []
    })
  })

  it('describes metadata, node, context, and variable changes from source to target', () => {
    const source = snapshot({
      nodes: [node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')],
      runtimeContext: 'First launch',
      variableValues: { audience: 'product teams', tone: 'calm' }
    })
    const target = snapshot({
      title: 'Launch Flow v2',
      description: 'Prepare and review the launch',
      nodes: [node('c', 'Deliver'), node('a', 'Intent', 'Updated intent'), node('d', 'Review')],
      runtimeContext: 'Second launch',
      variableValues: { audience: 'enterprise teams', format: 'memo' }
    })

    expect(compareRunFlowSnapshots(
      run('source', undefined, undefined, { flowRunSnapshot: source }),
      run('target', undefined, undefined, { flowRunSnapshot: target })
    )).toEqual({
      relation: 'different',
      verification: 'saved-flow-snapshot',
      sourceNodeCount: 3,
      targetNodeCount: 3,
      changes: [
        { key: 'title', kind: 'title', title: 'Flow 名称' },
        { key: 'description', kind: 'description', title: 'Flow 目标' },
        { key: 'runtime-context', kind: 'runtime-context', title: '运行说明' },
        { key: 'node-updated:a', kind: 'node-updated', title: 'Intent' },
        { key: 'node-removed:b', kind: 'node-removed', title: 'Draft' },
        { key: 'node-reordered:c', kind: 'node-reordered', title: 'Deliver' },
        { key: 'node-added:d', kind: 'node-added', title: 'Review' },
        { key: 'variable-updated:audience', kind: 'variable-updated', title: '{audience}' },
        { key: 'variable-removed:tone', kind: 'variable-removed', title: '{tone}' },
        { key: 'variable-added:format', kind: 'variable-added', title: '{format}' }
      ]
    })
  })

  it('does not report reorder when shared node order is unchanged', () => {
    const source = snapshot({ nodes: [node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')] })
    const target = snapshot({ nodes: [node('a', 'Intent'), node('c', 'Deliver'), node('d', 'Review')] })

    expect(compareRunFlowSnapshots(
      run('source', undefined, undefined, { flowRunSnapshot: source }),
      run('target', undefined, undefined, { flowRunSnapshot: target })
    ).changes).toEqual([
      { key: 'node-removed:b', kind: 'node-removed', title: 'Draft' },
      { key: 'node-added:d', kind: 'node-added', title: 'Review' }
    ])
  })

  it('keeps missing legacy snapshots unavailable', () => {
    expect(compareRunFlowSnapshots(
      run('legacy', undefined, undefined, { flowRunSnapshot: null }),
      run('modern', undefined, undefined, { flowRunSnapshot: snapshot({ nodes: [node('a', 'Intent')] }) })
    )).toEqual({
      relation: 'unavailable',
      verification: 'saved-flow-snapshot',
      sourceNodeCount: null,
      targetNodeCount: 1,
      changes: []
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
