import { Position } from '@vue-flow/core'
import type { Connection, Edge, MarkerType, Node, ViewportTransform } from '@vue-flow/core'
import type { FlowDraft, FlowNode } from '@/types'

export type FlowCanvasNodeData = {
  flowNode: FlowNode
  kindLabel: string
  stateLabel: string
  operationLabel: string
  incomplete: boolean
}

export type FlowCanvasGraphNode = Node<FlowCanvasNodeData>
export type StoredFlowCanvasState = {
  positions: Record<string, { x: number; y: number }>
  edges: Array<Pick<Edge, 'id' | 'source' | 'target' | 'sourceHandle' | 'targetHandle'>>
  viewport: ViewportTransform | null
  hasSavedEdges: boolean
}

const STORAGE_PREFIX = 'flowforge.canvas.v1:'

export function buildFlowCanvasNodes(
  flow: FlowDraft,
  positions: StoredFlowCanvasState['positions'],
  getData: (node: FlowNode) => Omit<FlowCanvasNodeData, 'flowNode'>
): FlowCanvasGraphNode[] {
  return flow.nodes.map((node, index) => {
    const data = getData(node)
    return {
      id: node.id,
      type: 'flowCanvas',
      class: 'flow-node-wrapper',
      position: positions[node.id] || { x: 80 + index * 300, y: 120 + (index % 2) * 24 },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: true,
      connectable: true,
      selectable: true,
      deletable: false,
      width: 248,
      ariaLabel: `${node.title}，${data.kindLabel}，${data.stateLabel}，${data.operationLabel}`,
      data: { flowNode: node, ...data }
    }
  })
}

export function captureFlowCanvasPositions(
  nodes: ReadonlyArray<Pick<FlowCanvasGraphNode, 'id' | 'position'>>
): StoredFlowCanvasState['positions'] {
  return Object.fromEntries(nodes.map(({ id, position }) => [id, { x: position.x, y: position.y }]))
}

export function buildSequentialCanvasEdges(
  nodes: FlowNode[],
  arrowMarker: MarkerType
): Edge[] {
  return nodes.slice(0, -1).map((node, index) => ({
    id: `canvas-${node.id}-${nodes[index + 1].id}`,
    source: node.id,
    target: nodes[index + 1].id,
    sourceHandle: 'source',
    targetHandle: 'target',
    type: 'smoothstep',
    markerEnd: arrowMarker,
    deletable: true,
    selectable: true
  }))
}

export function readFlowCanvasState(flowId: string): StoredFlowCanvasState {
  if (typeof window === 'undefined') return { positions: {}, edges: [], viewport: null, hasSavedEdges: false }
  try {
    const value = JSON.parse(window.localStorage.getItem(`${STORAGE_PREFIX}${flowId}`) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { positions: {}, edges: [], viewport: null, hasSavedEdges: false }
    }

    const candidate = value as Record<string, unknown>
    const positions: StoredFlowCanvasState['positions'] = {}
    if (candidate.positions && typeof candidate.positions === 'object' && !Array.isArray(candidate.positions)) {
      for (const [id, value] of Object.entries(candidate.positions)) {
        if (
          value && typeof value === 'object' &&
          Number.isFinite((value as { x?: unknown }).x) &&
          Number.isFinite((value as { y?: unknown }).y)
        ) {
          positions[id] = { x: (value as { x: number }).x, y: (value as { y: number }).y }
        }
      }
    }

    return {
      positions,
      edges: Array.isArray(candidate.edges) ? candidate.edges.filter(isStoredEdge) : [],
      viewport: isViewport(candidate.viewport) ? candidate.viewport : null,
      hasSavedEdges: Array.isArray(candidate.edges)
    }
  } catch {
    return { positions: {}, edges: [], viewport: null, hasSavedEdges: false }
  }
}

export function writeFlowCanvasState(
  flowId: string,
  nodes: ReadonlyArray<{ id: string; position: { x: number; y: number } }>,
  edges: ReadonlyArray<Pick<Edge, 'id' | 'source' | 'target' | 'sourceHandle' | 'targetHandle'>>,
  viewport: ViewportTransform | null = null
) {
  if (typeof window === 'undefined') return
  const state: StoredFlowCanvasState = {
    positions: Object.fromEntries(nodes.map(({ id, position }) => [id, position])),
    edges: edges.map(({ id, source, target, sourceHandle, targetHandle }) => ({
      id, source, target, sourceHandle, targetHandle
    })),
    viewport,
    hasSavedEdges: true
  }
  try {
    window.localStorage.setItem(`${STORAGE_PREFIX}${flowId}`, JSON.stringify(state))
  } catch {
    // Keep the canvas usable in memory when browser storage is unavailable.
  }
}

export function resolveFlowCanvasEdges(
  savedEdges: StoredFlowCanvasState['edges'],
  nodes: FlowNode[],
  arrowMarker: MarkerType,
  hasSavedEdges = savedEdges.length > 0
): Edge[] {
  const nodeIds = new Set(nodes.map((node) => node.id))
  const validEdges: StoredFlowCanvasState['edges'] = []
  const seenConnections = new Set<string>()
  const seenIds = new Set<string>()
  for (const edge of savedEdges) {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target) || edge.source === edge.target) continue
    if (seenIds.has(edge.id)) continue
    const key = JSON.stringify([edge.source, edge.sourceHandle || null, edge.target, edge.targetHandle || null])
    if (seenConnections.has(key)) continue
    seenIds.add(edge.id)
    seenConnections.add(key)
    validEdges.push(edge)
  }
  if (!hasSavedEdges) return buildSequentialCanvasEdges(nodes, arrowMarker)
  return validEdges.map((edge) => ({ ...edge, type: 'smoothstep', markerEnd: arrowMarker, deletable: true, selectable: true }))
}

export function removeFlowCanvasEdges(edges: Edge[], edgeIds: string[]): Edge[] {
  const removedIds = new Set(edgeIds)
  return edges.filter((edge) => !removedIds.has(edge.id))
}

export function canCreateFlowCanvasEdge(connection: Connection, edges: Edge[]): boolean {
  const { source, target, sourceHandle, targetHandle } = connection
  if (!source || !target || source === target) return false
  return !edges.some((edge) =>
    edge.source === source &&
    edge.target === target &&
    (edge.sourceHandle || null) === (sourceHandle || null) &&
    (edge.targetHandle || null) === (targetHandle || null)
  )
}

function isStoredEdge(value: unknown): value is StoredFlowCanvasState['edges'][number] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.id === 'string' && typeof candidate.source === 'string' && typeof candidate.target === 'string'
}

function isViewport(value: unknown): value is ViewportTransform {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const candidate = value as Record<string, unknown>
  return Number.isFinite(candidate.x) &&
    Number.isFinite(candidate.y) &&
    Number.isFinite(candidate.zoom) &&
    (candidate.zoom as number) > 0
}
