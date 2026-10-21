import type { Edge } from '@vue-flow/core'

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

export const FLOW_CANVAS_HISTORY_LIMIT = 50

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
  return {
    past: [...history.past, snapshot].slice(-FLOW_CANVAS_HISTORY_LIMIT),
    future: []
  }
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
