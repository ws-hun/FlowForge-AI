import { MarkerType } from '@vue-flow/core'
import type { FlowCanvasState, FlowNode } from '@/types'
import { mergeFlowCanvasPositions, resolveFlowCanvasEdges, type StoredFlowCanvasState } from '@/utils/flowCanvasState'

export type FlowCanvasRestoreDecision = 'use-cloud' | 'save-local' | 'migrate-local' | 'empty' | 'conflict'
export type OfflineCanvasRestoreDecision = 'identical' | 'resume-local' | 'conflict'
export type MissingCloudCanvasDecision = 'empty' | 'migrate-local' | 'conflict'

export function resolveKnownCanvasRevision(
  persistedRevision: number | null | undefined,
  inMemoryRevision: number | undefined
): number | undefined {
  return persistedRevision ?? inMemoryRevision
}

export function isFlowCanvasViewportRestoreCurrent(input: {
  flowId: string
  activeFlowId: string
  workspaceFlowId: string
  restoreGeneration: number
  currentGeneration: number
}): boolean {
  return Boolean(input.flowId) &&
    input.flowId === input.activeFlowId &&
    input.flowId === input.workspaceFlowId &&
    input.restoreGeneration === input.currentGeneration
}

export function preserveLatestCanvasSyncSnapshot<T>(pending: T | undefined, failed: T): T {
  return pending ?? failed
}

export function createFlowCanvasSyncEpochs() {
  const epochs = new Map<string, number>()
  const current = (flowId: string) => epochs.get(flowId) || 0
  const invalidate = (flowId: string) => {
    const next = current(flowId) + 1
    epochs.set(flowId, next)
    return next
  }
  const matches = (flowId: string, epoch: number) => current(flowId) === epoch

  return {
    current,
    invalidate,
    matches
  }
}

export function resolveMissingCloudCanvasDecision(input: {
  localSaved: boolean
  knownRevision: number | undefined
  remoteRevision: number
}): MissingCloudCanvasDecision {
  if (!input.localSaved) return 'empty'
  if (input.knownRevision !== undefined && input.knownRevision !== input.remoteRevision) return 'conflict'
  return 'migrate-local'
}

export function resolveOfflineCanvasRestoreDecision(input: {
  localDiffersFromCloud: boolean
  knownRevision: number | undefined
  remoteRevision: number
}): OfflineCanvasRestoreDecision {
  if (!input.localDiffersFromCloud) return 'identical'
  if (input.knownRevision === undefined || input.knownRevision !== input.remoteRevision) return 'conflict'
  return 'resume-local'
}

export function resolveFlowCanvasRestoreDecision(input: {
  remoteSaved: boolean
  localSaved: boolean
  editedWhileLoading: boolean
}): FlowCanvasRestoreDecision {
  if (input.editedWhileLoading) return input.remoteSaved ? 'conflict' : 'save-local'
  if (input.remoteSaved) return 'use-cloud'
  return input.localSaved ? 'migrate-local' : 'empty'
}

export function hasFlowCanvasRevisionConflict(input: {
  remoteSaved: boolean
  hasPendingLocalState: boolean
  knownRevision: number | undefined
  remoteRevision: number
}): boolean {
  return input.remoteSaved && input.hasPendingLocalState &&
    (input.knownRevision === undefined || input.knownRevision !== input.remoteRevision)
}

export function flowCanvasStatesDiffer(
  local: Pick<StoredFlowCanvasState, 'positions' | 'edges' | 'viewport' | 'hasSavedEdges'>,
  remote: Pick<FlowCanvasState, 'positions' | 'edges' | 'viewport' | 'saved'>,
  nodes: FlowNode[]
): boolean {
  const localEdges = resolveFlowCanvasEdges(local.edges, nodes, MarkerType.ArrowClosed, local.hasSavedEdges)
  const remoteEdges = resolveFlowCanvasEdges(remote.edges, nodes, MarkerType.ArrowClosed, remote.saved)
  const normalizeEdges = (edges: typeof localEdges) => edges
    .map(({ source, target, sourceHandle, targetHandle }) => ({
      source,
      target,
      sourceHandle: sourceHandle || 'source',
      targetHandle: targetHandle || 'target'
    }))
    .sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))

  const localSnapshot = {
    positions: mergeFlowCanvasPositions(nodes, local.positions),
    edges: normalizeEdges(localEdges),
    viewport: local.viewport
  }
  const remoteSnapshot = {
    positions: mergeFlowCanvasPositions(nodes, remote.positions),
    edges: normalizeEdges(remoteEdges),
    viewport: remote.viewport
  }
  return JSON.stringify(localSnapshot) !== JSON.stringify(remoteSnapshot)
}
