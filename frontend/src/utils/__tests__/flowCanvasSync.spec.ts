import { describe, expect, it } from 'vitest'
import {
  flowCanvasStatesDiffer,
  hasFlowCanvasRevisionConflict,
  resolveOfflineCanvasRestoreDecision,
  resolveFlowCanvasRestoreDecision
} from '@/utils/flowCanvasSync'
import type { FlowNode } from '@/types'

const nodes: FlowNode[] = [
  { id: 'input', type: 'input', title: 'Input', description: 'Goal' },
  { id: 'transform', type: 'transform', title: 'Transform', description: 'Process' },
  { id: 'output', type: 'output', title: 'Output', description: 'Result' }
]

describe('flow canvas synchronization policy', () => {
  it('prefers saved cloud state when the user did not edit during loading', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: true,
      localSaved: true,
      editedWhileLoading: false
    })).toBe('use-cloud')
  })

  it('migrates a local canvas when cloud state does not exist', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: false,
      localSaved: true,
      editedWhileLoading: false
    })).toBe('migrate-local')
  })

  it('keeps edits made while loading and saves them to the server', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: false,
      localSaved: false,
      editedWhileLoading: true
    })).toBe('save-local')
  })

  it('requires a decision when local edits race with a saved cloud canvas', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: true,
      localSaved: true,
      editedWhileLoading: true
    })).toBe('conflict')
  })

  it('recognizes a server change when pending local edits exist', () => {
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: true,
      hasPendingLocalState: true,
      knownRevision: 2,
      remoteRevision: 3
    })).toBe(true)
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: true,
      hasPendingLocalState: true,
      knownRevision: 3,
      remoteRevision: 3
    })).toBe(false)
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: false,
      hasPendingLocalState: true,
      knownRevision: undefined,
      remoteRevision: 0
    })).toBe(false)
  })

  it('resumes offline edits only when the cloud revision is unchanged', () => {
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: true,
      knownRevision: 4,
      remoteRevision: 4
    })).toBe('resume-local')
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: true,
      knownRevision: undefined,
      remoteRevision: 4
    })).toBe('conflict')
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: true,
      knownRevision: 3,
      remoteRevision: 4
    })).toBe('conflict')
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: false,
      knownRevision: 3,
      remoteRevision: 4
    })).toBe('identical')
  })

  it('compares normalized local and cloud state while ignoring stale node IDs', () => {
    const local = {
      positions: { input: { x: 80, y: 120 }, stale: { x: 20, y: 20 } },
      edges: [{ id: 'edge', source: 'input', target: 'output', sourceHandle: 'source', targetHandle: 'target' }],
      viewport: null,
      hasSavedEdges: true
    }
    const remote = {
      positions: {},
      edges: [{ id: 'edge', source: 'input', target: 'output' }],
      viewport: null,
      saved: true
    }
    expect(flowCanvasStatesDiffer(local, remote, nodes)).toBe(false)
    expect(flowCanvasStatesDiffer({ ...local, positions: { input: { x: 480, y: 120 } } }, remote, nodes)).toBe(true)
    expect(flowCanvasStatesDiffer({ ...local, viewport: { x: 12, y: 0, zoom: 1 } }, remote, nodes)).toBe(true)
  })

  it('ignores edge ordering and legacy default handles but detects topology changes', () => {
    const local = {
      positions: {},
      edges: [
        { id: 'first', source: 'input', target: 'transform', sourceHandle: 'source', targetHandle: 'target' },
        { id: 'second', source: 'transform', target: 'output', sourceHandle: 'source', targetHandle: 'target' }
      ],
      viewport: null,
      hasSavedEdges: true
    }
    const remote = {
      positions: {},
      edges: [
        { id: 'second', source: 'transform', target: 'output' },
        { id: 'first', source: 'input', target: 'transform' }
      ],
      viewport: null,
      saved: true
    }

    expect(flowCanvasStatesDiffer(local, remote, nodes)).toBe(false)
    expect(flowCanvasStatesDiffer(local, {
      ...remote,
      edges: remote.edges.map((edge) => edge.id === 'second' ? { ...edge, target: 'input' } : edge)
    }, nodes)).toBe(true)
  })
})
