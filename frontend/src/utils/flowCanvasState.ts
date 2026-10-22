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
  serverRevision?: number | null
}

const STORAGE_PREFIX = 'flowforge.canvas.v1:'
export const MAX_FLOW_CANVAS_EDGES = 500
export const MAX_FLOW_CANVAS_OFFSET = 100_000
export const MAX_FLOW_CANVAS_EDGE_ID_LENGTH = 120
export const MAX_DEFAULT_FLOW_CANVAS_COLUMNS = 4
export const MIN_FLOW_CANVAS_ZOOM = 0.2
export const MAX_FLOW_CANVAS_ZOOM = 1.6
let fallbackEdgeSequence = 0

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
      focusable: true,
      deletable: false,
      width: 248,
      ariaLabel: `${node.title}，${data.kindLabel}，${data.stateLabel}，${data.operationLabel}`,
      data: { flowNode: node, ...data }
    }
  })
}

export function buildDefaultFlowCanvasPositions(
  nodes: FlowNode[],
  columnCount?: number
): StoredFlowCanvasState['positions'] {
  const columns = resolveFlowCanvasColumnCount(nodes.length, columnCount)
  return Object.fromEntries(nodes.map((node, index) => [node.id, {
    x: 80 + (index % columns) * 300,
    y: 120 + Math.floor(index / columns) * 220
  }]))
}

export function mergeFlowCanvasPositions(
  nodes: FlowNode[],
  savedPositions: StoredFlowCanvasState['positions'],
  columnCount?: number
): StoredFlowCanvasState['positions'] {
  const columns = resolveFlowCanvasColumnCount(nodes.length, columnCount)
  const positions: StoredFlowCanvasState['positions'] = {}
  const occupied = new Set<string>()

  for (const node of nodes) {
    const position = savedPositions[node.id]
    if (!position) continue
    positions[node.id] = position
    occupied.add(positionKey(position))
  }

  let cursor = 0
  for (const node of nodes) {
    if (positions[node.id]) continue
    let position = gridPosition(cursor, columns)
    while (occupied.has(positionKey(position))) {
      cursor += 1
      position = gridPosition(cursor, columns)
    }
    positions[node.id] = position
    occupied.add(positionKey(position))
    cursor += 1
  }
  return positions
}

export function resolveFlowCanvasLocalRestoreState(
  persisted: StoredFlowCanvasState,
  pending?: Pick<StoredFlowCanvasState, 'positions' | 'edges' | 'viewport'> | null
): StoredFlowCanvasState {
  if (!pending) return persisted
  return {
    ...persisted,
    positions: { ...pending.positions },
    edges: pending.edges.map((edge) => ({ ...edge })),
    viewport: pending.viewport ? { ...pending.viewport } : null,
    hasSavedEdges: true
  }
}

function resolveFlowCanvasColumnCount(nodeCount: number, columnCount?: number): number {
  if (columnCount === undefined) {
    return Math.max(1, Math.min(MAX_DEFAULT_FLOW_CANVAS_COLUMNS, Math.ceil(Math.sqrt(nodeCount))))
  }
  return Number.isFinite(columnCount) ? Math.max(1, Math.floor(columnCount)) : 1
}

export function captureFlowCanvasPositions(
  nodes: ReadonlyArray<Pick<FlowCanvasGraphNode, 'id' | 'position'>>
): StoredFlowCanvasState['positions'] {
  return Object.fromEntries(nodes.map(({ id, position }) => [id, { x: position.x, y: position.y }]))
}

export function isFlowCanvasNodeSelectionKey(key: string): boolean {
  return key === 'Enter' || key === ' '
}

export function isFlowCanvasNodeMoveKey(key: string): boolean {
  return key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight'
}

export function shouldRecordFlowCanvasKeyboardMove(repeat: boolean): boolean {
  return !repeat
}

export function normalizeFlowCanvasViewport(viewport: ViewportTransform | null): ViewportTransform | null {
  if (!viewport || !Number.isFinite(viewport.x) || !Number.isFinite(viewport.y) || !Number.isFinite(viewport.zoom)) {
    return null
  }
  return {
    x: Math.max(-MAX_FLOW_CANVAS_OFFSET, Math.min(MAX_FLOW_CANVAS_OFFSET, viewport.x)),
    y: Math.max(-MAX_FLOW_CANVAS_OFFSET, Math.min(MAX_FLOW_CANVAS_OFFSET, viewport.y)),
    zoom: Math.max(MIN_FLOW_CANVAS_ZOOM, Math.min(MAX_FLOW_CANVAS_ZOOM, viewport.zoom))
  }
}

export function buildSequentialCanvasEdges(
  nodes: FlowNode[],
  arrowMarker: MarkerType
): Edge[] {
  return nodes.slice(0, -1).map((node, index) => ({
    id: `canvas-${index}-${node.id.slice(0, 40)}-${nodes[index + 1].id.slice(0, 40)}`,
    ariaLabel: `${node.title} 连接到 ${nodes[index + 1].title}`,
    source: node.id,
    target: nodes[index + 1].id,
    sourceHandle: 'source',
    targetHandle: 'target',
    type: 'smoothstep',
    markerEnd: arrowMarker,
    deletable: true,
    selectable: true,
    focusable: true
  }))
}

export function readFlowCanvasState(flowId: string): StoredFlowCanvasState {
  if (typeof window === 'undefined') return { positions: {}, edges: [], viewport: null, hasSavedEdges: false, serverRevision: null }
  try {
    const value = JSON.parse(window.localStorage.getItem(`${STORAGE_PREFIX}${flowId}`) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { positions: {}, edges: [], viewport: null, hasSavedEdges: false, serverRevision: null }
    }

    const candidate = value as Record<string, unknown>
    const positions: StoredFlowCanvasState['positions'] = {}
    if (candidate.positions && typeof candidate.positions === 'object' && !Array.isArray(candidate.positions)) {
      for (const [id, value] of Object.entries(candidate.positions)) {
        if (
          value && typeof value === 'object' &&
          Number.isFinite((value as { x?: unknown }).x) &&
          Number.isFinite((value as { y?: unknown }).y) &&
          Math.abs((value as { x: number }).x) <= MAX_FLOW_CANVAS_OFFSET &&
          Math.abs((value as { y: number }).y) <= MAX_FLOW_CANVAS_OFFSET
        ) {
          positions[id] = { x: (value as { x: number }).x, y: (value as { y: number }).y }
        }
      }
    }

    return {
      positions,
      edges: Array.isArray(candidate.edges) ? candidate.edges.filter(isStoredEdge) : [],
      viewport: isViewport(candidate.viewport) ? candidate.viewport : null,
      hasSavedEdges: Array.isArray(candidate.edges),
      serverRevision: Number.isSafeInteger(candidate.serverRevision) && Number(candidate.serverRevision) >= 0
        ? Number(candidate.serverRevision)
        : null
    }
  } catch {
    return { positions: {}, edges: [], viewport: null, hasSavedEdges: false, serverRevision: null }
  }
}

export function writeFlowCanvasServerRevision(flowId: string, revision: number): boolean {
  if (typeof window === 'undefined' || !Number.isSafeInteger(revision) || revision < 0) return false
  const key = `${STORAGE_PREFIX}${flowId}`
  try {
    const raw = window.localStorage.getItem(key)
    if (!raw) return false
    const state = JSON.parse(raw)
    if (!state || typeof state !== 'object' || Array.isArray(state)) return false
    window.localStorage.setItem(key, JSON.stringify({ ...state, serverRevision: revision }))
    return true
  } catch {
    return false
  }
}

export function removeFlowCanvasState(flowId: string): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(`${STORAGE_PREFIX}${flowId}`)
  } catch {
    // Browser storage can be unavailable in restricted contexts.
  }
}

export function writeFlowCanvasState(
  flowId: string,
  nodes: ReadonlyArray<{ id: string; position: { x: number; y: number } }>,
  edges: ReadonlyArray<Pick<Edge, 'id' | 'source' | 'target' | 'sourceHandle' | 'targetHandle'>>,
  viewport: ViewportTransform | null = null
): boolean {
  if (typeof window === 'undefined') return false
  const state: StoredFlowCanvasState = {
    positions: Object.fromEntries(nodes.map(({ id, position }) => [id, position])),
    edges: edges.map(({ id, source, target, sourceHandle, targetHandle }) => ({
      id, source, target, sourceHandle, targetHandle
    })),
    viewport: normalizeFlowCanvasViewport(viewport),
    hasSavedEdges: true,
    serverRevision: readFlowCanvasState(flowId).serverRevision ?? null
  }
  try {
    window.localStorage.setItem(`${STORAGE_PREFIX}${flowId}`, JSON.stringify(state))
    return true
  } catch {
    return false
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
    if (edge.sourceHandle && edge.sourceHandle !== 'source') continue
    if (edge.targetHandle && edge.targetHandle !== 'target') continue
    const key = JSON.stringify([edge.source, edge.sourceHandle || null, edge.target, edge.targetHandle || null])
    if (seenConnections.has(key)) continue
    const validId = edge.id.trim().length > 0 && edge.id.length <= MAX_FLOW_CANVAS_EDGE_ID_LENGTH
    const id = !validId || seenIds.has(edge.id) ? createFlowCanvasEdgeId(seenIds) : edge.id
    seenIds.add(id)
    seenConnections.add(key)
    validEdges.push({ ...edge, id })
  }
  if (!hasSavedEdges) return buildSequentialCanvasEdges(nodes, arrowMarker)
  return validEdges.map((edge) => ({
    ...edge,
    sourceHandle: edge.sourceHandle || 'source',
    targetHandle: edge.targetHandle || 'target',
    ariaLabel: buildCanvasEdgeLabel(edge, nodes),
    type: 'smoothstep',
    markerEnd: arrowMarker,
    deletable: true,
    selectable: true,
    focusable: true
  }))
}

export function removeFlowCanvasEdges(edges: Edge[], edgeIds: string[]): Edge[] {
  const removedIds = new Set(edgeIds)
  return edges.filter((edge) => !removedIds.has(edge.id))
}

export function canCreateFlowCanvasEdge(connection: Connection, edges: Edge[]): boolean {
  const { source, target, sourceHandle, targetHandle } = connection
  if (!source || !target || source === target) return false
  if (edges.length >= MAX_FLOW_CANVAS_EDGES) return false
  return !edges.some((edge) =>
    edge.source === source &&
    edge.target === target &&
    (edge.sourceHandle || 'source') === (sourceHandle || 'source') &&
    (edge.targetHandle || 'target') === (targetHandle || 'target')
  )
}

export function createFlowCanvasEdgeId(existingIds: ReadonlySet<string> = new Set()): string {
  let id = ''
  do {
    if (globalThis.crypto?.randomUUID) {
      id = globalThis.crypto.randomUUID()
    } else {
      fallbackEdgeSequence += 1
      id = `edge-${Date.now()}-${fallbackEdgeSequence}`
    }
  } while (existingIds.has(id))
  return id
}

export function createFlowCanvasEdge(
  connection: Connection,
  id: string,
  nodes: FlowNode[],
  arrowMarker: MarkerType
): Edge {
  return {
    ...connection,
    id,
    ariaLabel: buildCanvasEdgeLabel(connection, nodes),
    type: 'smoothstep',
    markerEnd: arrowMarker,
    deletable: true,
    selectable: true,
    focusable: true
  }
}

function isStoredEdge(value: unknown): value is StoredFlowCanvasState['edges'][number] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.id === 'string' && typeof candidate.source === 'string' && typeof candidate.target === 'string'
}

function buildCanvasEdgeLabel(
  edge: Pick<Edge, 'source' | 'target'>,
  nodes: FlowNode[]
) {
  const source = nodes.find((node) => node.id === edge.source)?.title || edge.source
  const target = nodes.find((node) => node.id === edge.target)?.title || edge.target
  return `${source} 连接到 ${target}`
}

function gridPosition(index: number, columns: number) {
  return { x: 80 + (index % columns) * 300, y: 120 + Math.floor(index / columns) * 220 }
}

function positionKey(position: { x: number; y: number }) {
  return `${position.x}:${position.y}`
}

function isViewport(value: unknown): value is ViewportTransform {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const candidate = value as Record<string, unknown>
  return Number.isFinite(candidate.x) &&
    Number.isFinite(candidate.y) &&
    Math.abs(candidate.x as number) <= MAX_FLOW_CANVAS_OFFSET &&
    Math.abs(candidate.y as number) <= MAX_FLOW_CANVAS_OFFSET &&
    Number.isFinite(candidate.zoom) &&
    (candidate.zoom as number) >= MIN_FLOW_CANVAS_ZOOM &&
    (candidate.zoom as number) <= MAX_FLOW_CANVAS_ZOOM
}
