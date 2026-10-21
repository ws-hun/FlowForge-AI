import { describe, expect, it, vi } from 'vitest'
import {
  createFlowCanvasSyncEpochs,
  flowCanvasStatesDiffer,
  hasFlowCanvasRevisionConflict,
  isFlowCanvasViewportRestoreCurrent,
  pendingBackgroundCanvasSyncIds,
  preserveLatestCanvasSyncSnapshot,
  resolveMissingCloudCanvasDecision,
  resolveKnownCanvasRevision,
  resolveOfflineCanvasRestoreDecision,
  resolveFlowCanvasRestoreDecision
} from '@/utils/flowCanvasSync'
import { readFlowCanvasState, writeFlowCanvasServerRevision, writeFlowCanvasState } from '@/utils/flowCanvasState'
import type { FlowNode } from '@/types'

const nodes: FlowNode[] = [
  { id: 'input', type: 'input', title: 'Input', description: 'Goal' },
  { id: 'prompt', type: 'prompt', title: 'Prompt', description: 'Process' },
  { id: 'output', type: 'output', title: 'Output', description: 'Result' }
]

describe('flow canvas synchronization policy', () => {
  it('does not replace a newer queued snapshot when an older save fails', () => {
    const attempted = { positions: { input: { x: 10, y: 20 } } }
    const newerPending = { positions: { input: { x: 90, y: 120 } } }

    expect(preserveLatestCanvasSyncSnapshot(newerPending, attempted)).toBe(newerPending)
    expect(preserveLatestCanvasSyncSnapshot(undefined, attempted)).toBe(attempted)
  })

  it('prefers the persisted acknowledged revision after re-entering a conflicted flow', () => {
    expect(resolveKnownCanvasRevision(4, 5)).toBe(4)
    expect(resolveKnownCanvasRevision(null, 5)).toBe(5)
    expect(resolveKnownCanvasRevision(undefined, undefined)).toBeUndefined()
  })

  it('keeps a same-session remote revision change in conflict after re-entry', () => {
    const knownRevision = resolveKnownCanvasRevision(4, 5)
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: true,
      knownRevision,
      remoteRevision: 5
    })).toBe('conflict')
  })

  it('invalidates late sync completions independently for each flow', () => {
    const epochs = createFlowCanvasSyncEpochs()
    const { current, invalidate, matches } = epochs
    const requestEpoch = current('flow-a')

    invalidate('flow-a')

    expect(matches('flow-a', requestEpoch)).toBe(false)
    expect(matches('flow-b', 0)).toBe(true)
    expect(current('flow-a')).toBe(1)
  })

  it('ignores viewport restores for a different Flow or stale navigation generation', () => {
    const current = {
      flowId: 'flow-a',
      activeFlowId: 'flow-a',
      workspaceFlowId: 'flow-a',
      restoreGeneration: 3,
      currentGeneration: 3
    }

    expect(isFlowCanvasViewportRestoreCurrent(current)).toBe(true)
    expect(isFlowCanvasViewportRestoreCurrent({ ...current, activeFlowId: 'flow-b' })).toBe(false)
    expect(isFlowCanvasViewportRestoreCurrent({ ...current, workspaceFlowId: 'flow-b' })).toBe(false)
    expect(isFlowCanvasViewportRestoreCurrent({ ...current, currentGeneration: 4 })).toBe(false)
  })

  it('retries only inactive pending canvases without a running retry', () => {
    expect(pendingBackgroundCanvasSyncIds(
      ['active', 'background-a', 'background-b'],
      'active',
      new Set(['background-b'])
    )).toEqual(['background-a'])
  })

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

  it('migrates local canvas when the cloud is empty unless a prior revision disappeared', () => {
    expect(resolveMissingCloudCanvasDecision({
      localSaved: true,
      knownRevision: undefined,
      remoteRevision: 0
    })).toBe('migrate-local')
    expect(resolveMissingCloudCanvasDecision({
      localSaved: true,
      knownRevision: 0,
      remoteRevision: 0
    })).toBe('migrate-local')
    expect(resolveMissingCloudCanvasDecision({
      localSaved: true,
      knownRevision: 3,
      remoteRevision: 0
    })).toBe('conflict')
    expect(resolveMissingCloudCanvasDecision({
      localSaved: false,
      knownRevision: undefined,
      remoteRevision: 0
    })).toBe('empty')
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

  it('uses a persisted revision to resume local work after a browser reload', () => {
    const storage = new Map<string, string>()
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => storage.get(key) || null,
        setItem: (key: string, value: string) => storage.set(key, value)
      }
    })
    writeFlowCanvasState('flow', [{ id: 'prompt', position: { x: 540, y: 180 } }], [], null)
    writeFlowCanvasServerRevision('flow', 9)

    const restoredLocalState = readFlowCanvasState('flow')
    const remote = { positions: {}, edges: [], viewport: null, saved: true }
    const differs = flowCanvasStatesDiffer(restoredLocalState, remote, nodes)

    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: differs,
      knownRevision: restoredLocalState.serverRevision ?? undefined,
      remoteRevision: 9
    })).toBe('resume-local')
    expect(resolveOfflineCanvasRestoreDecision({
      localDiffersFromCloud: differs,
      knownRevision: restoredLocalState.serverRevision ?? undefined,
      remoteRevision: 10
    })).toBe('conflict')
  })

  it('ignores edge ordering and legacy default handles but detects topology changes', () => {
    const local = {
      positions: {},
      edges: [
        { id: 'first', source: 'input', target: 'prompt', sourceHandle: 'source', targetHandle: 'target' },
        { id: 'second', source: 'prompt', target: 'output', sourceHandle: 'source', targetHandle: 'target' }
      ],
      viewport: null,
      hasSavedEdges: true
    }
    const remote = {
      positions: {},
      edges: [
        { id: 'second', source: 'prompt', target: 'output' },
        { id: 'first', source: 'input', target: 'prompt' }
      ],
      viewport: null,
      saved: true
    }

    expect(flowCanvasStatesDiffer(local, remote, nodes)).toBe(false)
    expect(flowCanvasStatesDiffer({
      ...local,
      edges: local.edges.map((edge) => ({ ...edge, id: `local-${edge.id}` }))
    }, {
      ...remote,
      edges: remote.edges.map((edge) => ({ ...edge, id: `cloud-${edge.id}` }))
    }, nodes)).toBe(false)
    expect(flowCanvasStatesDiffer(local, {
      ...remote,
      edges: remote.edges.map((edge) => edge.id === 'second' ? { ...edge, target: 'input' } : edge)
    }, nodes)).toBe(true)
  })
})
