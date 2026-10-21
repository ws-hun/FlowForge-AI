import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildFlowCanvasNodes,
  buildDefaultFlowCanvasPositions,
  buildSequentialCanvasEdges,
  canCreateFlowCanvasEdge,
  captureFlowCanvasPositions,
  createFlowCanvasEdge,
  createFlowCanvasEdgeId,
  mergeFlowCanvasPositions,
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
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('restores saved positions while assigning defaults to newly added nodes', () => {
    const canvasNodes = buildFlowCanvasNodes(flow, { prompt: { x: 420, y: 260 } }, nodeData)
    expect(canvasNodes[1].position).toEqual({ x: 420, y: 260 })
    expect(canvasNodes[2].position).toEqual({ x: 680, y: 120 })
    expect(canvasNodes[0].deletable).toBe(false)
    expect(canvasNodes[0].focusable).toBe(true)
  })

  it('ignores out-of-range saved node positions', () => {
    const getItem = vi.fn().mockReturnValue(JSON.stringify({
      positions: {
        input: { x: 999_999, y: 12 },
        prompt: { x: -24, y: -48 }
      },
      edges: []
    }))
    vi.stubGlobal('window', { localStorage: { getItem } })
    const saved = readFlowCanvasState('flow')
    expect(saved.positions).toEqual({ prompt: { x: -24, y: -48 } })
  })

  it('starts with a visual linear chain without changing the Flow model', () => {
    const edges = buildSequentialCanvasEdges(nodes, MarkerType.ArrowClosed)
    expect(edges.map(({ source, target }) => [source, target])).toEqual([
      ['input', 'prompt'], ['prompt', 'ai'], ['ai', 'output']
    ])
    expect(edges[0].ariaLabel).toBe('Input 连接到 Prompt')
    expect(edges[0].focusable).toBe(true)
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

  it('places long flows on a balanced two-dimensional default layout', () => {
    const extendedNodes = Array.from({ length: 6 }, (_, index) => ({
      id: `node-${index}`,
      type: 'prompt' as const,
      title: `Node ${index}`,
      description: ''
    }))
    expect(buildDefaultFlowCanvasPositions(extendedNodes)).toEqual({
      'node-0': { x: 80, y: 120 },
      'node-1': { x: 380, y: 120 },
      'node-2': { x: 680, y: 120 },
      'node-3': { x: 980, y: 120 },
      'node-4': { x: 80, y: 340 },
      'node-5': { x: 380, y: 340 }
    })
  })

  it('keeps a one-column canvas layout valid for narrow workspaces', () => {
    const narrowFlow: FlowNode[] = [nodes[0], nodes[1], nodes[2]]
    expect(buildDefaultFlowCanvasPositions(narrowFlow, 1)).toEqual({
      input: { x: 80, y: 120 },
      prompt: { x: 80, y: 340 },
      ai: { x: 80, y: 560 }
    })
  })

  it('keeps saved positions while laying out newly added nodes on the default grid', () => {
    expect(mergeFlowCanvasPositions(nodes, { prompt: { x: 512, y: 288 } })).toEqual({
      input: { x: 80, y: 120 },
      prompt: { x: 512, y: 288 },
      ai: { x: 680, y: 120 },
      output: { x: 980, y: 120 }
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
      { id: 'invalid-handle', source: 'prompt', target: 'output', sourceHandle: 'missing' },
      { id: 'stale', source: 'gone', target: 'output' }
    ]
    const restored = resolveFlowCanvasEdges(saved, nodes, MarkerType.ArrowClosed)
    expect(restored.map((edge) => edge.id)).toEqual(['saved'])
    expect(restored[0].ariaLabel).toBe('Input 连接到 Output')
    expect(restored[0].focusable).toBe(true)
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
    expect(writeFlowCanvasState('flow', [{ id: 'input', position: { x: 12, y: 24 } }], [], viewport)).toBe(true)

    expect(readFlowCanvasState('flow')).toMatchObject({
      positions: { input: { x: 12, y: 24 } },
      edges: [],
      viewport
    })
  })

  it('reports when browser storage rejects the canvas snapshot', () => {
    vi.stubGlobal('window', {
      localStorage: {
        setItem: () => { throw new Error('storage denied') }
      }
    })
    expect(writeFlowCanvasState('flow', [], [])).toBe(false)
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

  it('allows distinct handles to represent separate visual relationships', () => {
    const edges = [{ id: 'existing', source: 'input', target: 'prompt', sourceHandle: 'source', targetHandle: 'target' }] as Edge[]
    expect(canCreateFlowCanvasEdge({ source: 'input', target: 'prompt', sourceHandle: 'alternate', targetHandle: 'target' }, edges)).toBe(true)
  })

  it('builds new connections with labels and keyboard interaction enabled', () => {
    const edge = createFlowCanvasEdge(
      { source: 'input', target: 'prompt', sourceHandle: 'source', targetHandle: 'target' },
      'new-edge',
      nodes,
      MarkerType.ArrowClosed
    )
    expect(edge).toMatchObject({
      id: 'new-edge',
      ariaLabel: 'Input 连接到 Prompt',
      type: 'smoothstep',
      deletable: true,
      selectable: true,
      focusable: true
    })
  })

  it('creates unique fallback edge IDs when randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', {})
    vi.spyOn(Date, 'now').mockReturnValue(1234)
    expect(createFlowCanvasEdgeId()).toBe('edge-1234-1')
    expect(createFlowCanvasEdgeId()).toBe('edge-1234-2')
  })
})
