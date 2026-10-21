import type { Edge, EdgeChange } from '@vue-flow/core'

export type FlowCanvasEditSnapshot = {
  positions: Record<string, { x: number; y: number }>
  edges: Array<{
    id: string
    source: string
    target: string
    sourceHandle?: string | null
    targetHandle?: string | null
  }>
}

export type FlowCanvasHistory = {
  past: FlowCanvasEditSnapshot[]
  future: FlowCanvasEditSnapshot[]
}

export type FlowCanvasHistoryAction = 'undo' | 'redo' | null

export const FLOW_CANVAS_HISTORY_LIMIT = 50

export function restoreRemovedFlowCanvasEdges(
  currentEdges: ReadonlyArray<Pick<Edge, 'id' | 'source' | 'target' | 'sourceHandle' | 'targetHandle'>>,
  changes: ReadonlyArray<EdgeChange>
): FlowCanvasEditSnapshot['edges'] {
  const previousEdges = currentEdges.map((edge) => ({ ...edge }))
  for (const edge of changes) {
    if (edge.type !== 'remove' || previousEdges.some((existing) => existing.id === edge.id)) continue
    previousEdges.push({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle
    })
  }
  return previousEdges
}

export function resolveFlowCanvasHistoryShortcut(input: {
  key: string
  metaKey: boolean
  ctrlKey: boolean
  shiftKey: boolean
  altKey: boolean
  composing: boolean
  insideCanvas: boolean
  editableTarget: boolean
}): FlowCanvasHistoryAction {
  if ((!input.metaKey && !input.ctrlKey) || input.altKey || input.composing || !input.insideCanvas || input.editableTarget) return null
  const key = input.key.toLowerCase()
  if (key === 'y' || (key === 'z' && input.shiftKey)) return 'redo'
  if (key === 'z') return 'undo'
  return null
}

export function createFlowCanvasHistory(): FlowCanvasHistory {
  return { past: [], future: [] }
}

export function captureFlowCanvasEditSnapshot(
  nodes: ReadonlyArray<{ id: string; position: { x: number; y: number } }>,
  edges: ReadonlyArray<Pick<Edge, 'id' | 'source' | 'target' | 'sourceHandle' | 'targetHandle'>>
): FlowCanvasEditSnapshot {
  return {
    positions: Object.fromEntries(nodes.map(({ id, position }) => [id, { ...position }])),
    edges: edges.map(({ id, source, target, sourceHandle, targetHandle }) => ({
      id,
      source,
      target,
      sourceHandle,
      targetHandle
    }))
  }
}

export function recordFlowCanvasEdit(
  history: FlowCanvasHistory,
  snapshot: FlowCanvasEditSnapshot
): FlowCanvasHistory {
  const previous = history.past[history.past.length - 1]
  if (previous && sameFlowCanvasEditSnapshot(previous, snapshot)) {
    if (!history.future.length) return history
    return { ...history, future: [] }
  }
  return {
    past: [...history.past, cloneFlowCanvasEditSnapshot(snapshot)].slice(-FLOW_CANVAS_HISTORY_LIMIT),
    future: []
  }
}

function sameFlowCanvasEditSnapshot(left: FlowCanvasEditSnapshot, right: FlowCanvasEditSnapshot): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

export function undoFlowCanvasEdit(
  history: FlowCanvasHistory,
  current: FlowCanvasEditSnapshot
): { history: FlowCanvasHistory; snapshot: FlowCanvasEditSnapshot | null } {
  const snapshot = history.past[history.past.length - 1] || null
  if (!snapshot) return { history, snapshot: null }
  return {
    history: {
      past: history.past.slice(0, -1),
      future: [current, ...history.future].slice(0, FLOW_CANVAS_HISTORY_LIMIT)
    },
    snapshot
  }
}

export function redoFlowCanvasEdit(
  history: FlowCanvasHistory,
  current: FlowCanvasEditSnapshot
): { history: FlowCanvasHistory; snapshot: FlowCanvasEditSnapshot | null } {
  const snapshot = history.future[0] || null
  if (!snapshot) return { history, snapshot: null }
  return {
    history: {
      past: [...history.past, current].slice(-FLOW_CANVAS_HISTORY_LIMIT),
      future: history.future.slice(1)
    },
    snapshot
  }
}

function cloneFlowCanvasEditSnapshot(snapshot: FlowCanvasEditSnapshot): FlowCanvasEditSnapshot {
  return {
    positions: Object.fromEntries(Object.entries(snapshot.positions).map(([id, position]) => [id, { ...position }])),
    edges: snapshot.edges.map((edge) => ({ ...edge }))
  }
}
