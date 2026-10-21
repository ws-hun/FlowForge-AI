import { MarkerType } from '@vue-flow/core'
import type { FlowCanvasState, FlowNode } from '@/types'
import { mergeFlowCanvasPositions, resolveFlowCanvasEdges, type StoredFlowCanvasState } from '@/utils/flowCanvasState'

export type FlowCanvasRestoreDecision = 'use-cloud' | 'save-local' | 'migrate-local' | 'empty' | 'conflict'
export type OfflineCanvasRestoreDecision = 'identical' | 'resume-local' | 'conflict'

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
    .map(({ id, source, target, sourceHandle, targetHandle }) => ({
      id,
      source,
      target,
      sourceHandle: sourceHandle || 'source',
      targetHandle: targetHandle || 'target'
    }))
    .sort((left, right) => left.id.localeCompare(right.id))

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
