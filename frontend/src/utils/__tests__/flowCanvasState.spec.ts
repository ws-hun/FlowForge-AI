import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildFlowCanvasNodes,
  buildSequentialCanvasEdges,
  canCreateFlowCanvasEdge,
  captureFlowCanvasPositions,
  readFlowCanvasState,
  removeFlowCanvasEdges,
  resolveFlowCanvasEdges,
  writeFlowCanvasState
} from '@/utils/flowCanvasState'
import type { FlowDraft, FlowNode } from '@/types'
import type { Edge } from '@vue-flow/core'
import { MarkerType } from '@vue-flow/core'

const nodes: FlowNode[] = [
  { id: 'input', type: 'input', title: 'Input', description: 'Goal' },
  { id: 'prompt', type: 'prompt', title: 'Prompt', description: 'Shape output' },
  { id: 'ai', type: 'ai-task', title: 'AI Task', description: 'Execute' },
  { id: 'output', type: 'output', title: 'Output', description: 'Deliver' }
]

const flow = { id: 'flow', nodes } as FlowDraft
const nodeData = () => ({ kindLabel: 'Node', stateLabel: 'Ready', operationLabel: 'Prepare', incomplete: false })

describe('flow canvas state', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('restores saved positions while assigning defaults to newly added nodes', () => {
    const canvasNodes = buildFlowCanvasNodes(flow, { prompt: { x: 420, y: 260 } }, nodeData)
    expect(canvasNodes[1].position).toEqual({ x: 420, y: 260 })
    expect(canvasNodes[2].position).toEqual({ x: 680, y: 120 })
    expect(canvasNodes[0].deletable).toBe(false)
  })

  it('starts with a visual linear chain without changing the Flow model', () => {
    const edges = buildSequentialCanvasEdges(nodes, MarkerType.ArrowClosed)
    expect(edges.map(({ source, target }) => [source, target])).toEqual([
      ['input', 'prompt'], ['prompt', 'ai'], ['ai', 'output']
    ])
  })

  it('captures current canvas positions for content-only node refreshes', () => {
    const graphNodes = buildFlowCanvasNodes(flow, { prompt: { x: 420, y: 260 } }, nodeData)
    expect(captureFlowCanvasPositions(graphNodes)).toEqual({
      input: { x: 80, y: 120 },
      prompt: { x: 420, y: 260 },
      ai: { x: 680, y: 120 },
      output: { x: 980, y: 144 }
    })
  })

  it('provides accessible canvas labels with node type, state and responsibility', () => {
    const [canvasNode] = buildFlowCanvasNodes(flow, {}, (node) => ({
      kindLabel: node.type,
      stateLabel: '待补充',
      operationLabel: '准备上下文',
      incomplete: true
    }))

    expect(canvasNode.ariaLabel).toBe('Input，input，待补充，准备上下文')
  })

  it('restores only valid local edges and falls back to the visual chain', () => {
    const saved = [
      { id: 'saved', source: 'input', target: 'output' },
      { id: 'duplicate', source: 'input', target: 'output' },
      { id: 'saved', source: 'prompt', target: 'output' },
      { id: 'stale', source: 'gone', target: 'output' }
    ]
    expect(resolveFlowCanvasEdges(saved, nodes, MarkerType.ArrowClosed).map((edge) => edge.id)).toEqual(['saved'])
    expect(resolveFlowCanvasEdges([], nodes, MarkerType.ArrowClosed, false)).toHaveLength(3)
    expect(resolveFlowCanvasEdges([], nodes, MarkerType.ArrowClosed, true)).toHaveLength(0)
  })

  it('restores a valid saved viewport and ignores malformed viewport data', () => {
    const getItem = vi.fn().mockReturnValue(JSON.stringify({
      positions: {},
      edges: [],
      viewport: { x: -120, y: 48, zoom: 0.85 }
    }))
    vi.stubGlobal('window', { localStorage: { getItem } })
    expect(readFlowCanvasState('flow').viewport).toEqual({ x: -120, y: 48, zoom: 0.85 })

    getItem.mockReturnValueOnce(JSON.stringify({ viewport: { x: 0, y: 0, zoom: 0 } }))
    expect(readFlowCanvasState('flow').viewport).toBeNull()
    getItem.mockReturnValueOnce(JSON.stringify({ viewport: { x: 0, y: 0, zoom: 3 } }))
    expect(readFlowCanvasState('flow').viewport).toBeNull()
    getItem.mockReturnValueOnce(JSON.stringify({ viewport: { x: 1_000_000, y: 0, zoom: 1 } }))
    expect(readFlowCanvasState('flow').viewport).toBeNull()
  })

  it('persists viewport together with canvas positions and connections', () => {
    const storage = new Map<string, string>()
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => storage.get(key) || null,
        setItem: (key: string, value: string) => storage.set(key, value)
      }
    })
    const viewport = { x: -240, y: 96, zoom: 1.2 }
    writeFlowCanvasState('flow', [{ id: 'input', position: { x: 12, y: 24 } }], [], viewport)

    expect(readFlowCanvasState('flow')).toMatchObject({
      positions: { input: { x: 12, y: 24 } },
      edges: [],
      viewport
    })
  })

  it('removes visual connections by selected graph IDs', () => {
    const edges = [
      { id: 'keep', source: 'input', target: 'prompt' },
      { id: 'remove', source: 'prompt', target: 'ai' }
    ] as Edge[]

    expect(removeFlowCanvasEdges(edges, ['remove']).map((edge) => edge.id)).toEqual(['keep'])
  })

  it('rejects self-connections and duplicate visual connections', () => {
    const edges = [{ id: 'existing', source: 'input', target: 'prompt', sourceHandle: 'out', targetHandle: 'in' }] as Edge[]
    expect(canCreateFlowCanvasEdge({ source: 'input', target: 'input' }, edges)).toBe(false)
    expect(canCreateFlowCanvasEdge({ source: 'input', target: 'prompt', sourceHandle: 'out', targetHandle: 'in' }, edges)).toBe(false)
    expect(canCreateFlowCanvasEdge({ source: 'prompt', target: 'ai' }, edges)).toBe(true)
  })
})
