import { describe, expect, it } from 'vitest'
import type { FlowNodeArtifact, FlowRunTrace } from '@/types'
import { resolveFlowArtifactFocus } from '../flowArtifactFocus'

const artifact = (overrides: Partial<FlowNodeArtifact> = {}): FlowNodeArtifact => ({
  key: 'node:prompt-1:instruction',
  type: 'instruction-contribution',
  storage: 'node-artifact',
  state: 'materialized',
  ...overrides
})

const trace = (overrides: Partial<FlowRunTrace> = {}): FlowRunTrace => ({
  runId: 'run-1',
  flowId: 'flow-1',
  status: 'completed',
  nodes: [
    {
      nodeId: 'prompt-1',
      nodeType: 'prompt',
      title: '写作指令',
      status: 'completed',
      compiledContent: 'content',
      outputArtifact: artifact()
    }
  ],
  ...overrides
})

describe('resolveFlowArtifactFocus', () => {
  it('accepts an Artifact owned by the requested run and node', () => {
    expect(resolveFlowArtifactFocus(trace(), 'run-1', 'prompt-1', 'node:prompt-1:instruction')).toMatchObject({
      valid: true,
      artifactKey: 'node:prompt-1:instruction'
    })
  })

  it('rejects an Artifact from another run before any detail request can happen', () => {
    expect(resolveFlowArtifactFocus(trace(), 'other-run', 'prompt-1', 'node:prompt-1:instruction')).toEqual({
      valid: false,
      reason: 'trace-run-mismatch'
    })
  })

  it('rejects an Artifact owned by another node', () => {
    const result = resolveFlowArtifactFocus(
      trace({
        nodes: [
          trace().nodes[0],
          {
            nodeId: 'prompt-2',
            nodeType: 'prompt',
            title: '交付规范',
            status: 'completed',
            compiledContent: 'content',
            outputArtifact: artifact({ key: 'node:prompt-2:instruction' })
          }
        ]
      }),
      'run-1',
      'prompt-1',
      'node:prompt-2:instruction'
    )

    expect(result).toEqual({ valid: false, reason: 'artifact-node-mismatch' })
  })

  it('rejects skipped or non-node Artifacts as non-inspectable', () => {
    expect(resolveFlowArtifactFocus(
      trace({ nodes: [{ ...trace().nodes[0], outputArtifact: artifact({ state: 'skipped' }) }] }),
      'run-1',
      'prompt-1',
      'node:prompt-1:instruction'
    )).toEqual({ valid: false, reason: 'artifact-not-inspectable' })

    expect(resolveFlowArtifactFocus(
      trace({ nodes: [{ ...trace().nodes[0], outputArtifact: artifact({ storage: 'trace-content' }) }] }),
      'run-1',
      'prompt-1',
      'node:prompt-1:instruction'
    )).toEqual({ valid: false, reason: 'artifact-not-inspectable' })
  })

  it('allows a failed Provider result because its call detail is inspectable', () => {
    expect(resolveFlowArtifactFocus(
      trace({ nodes: [{ ...trace().nodes[0], outputArtifact: artifact({ type: 'provider-result', state: 'failed' }) }] }),
      'run-1',
      'prompt-1',
      'node:prompt-1:instruction'
    )).toMatchObject({ valid: true })
  })
})
