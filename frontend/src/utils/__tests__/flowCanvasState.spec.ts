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
  MAX_FLOW_CANVAS_EDGES,
  MAX_FLOW_CANVAS_EDGE_ID_LENGTH,
  MAX_FLOW_CANVAS_OFFSET,
  normalizeFlowCanvasViewport,
  readFlowCanvasState,
  removeFlowCanvasState,
  removeFlowCanvasEdges,
  resolveFlowCanvasEdges,
  writeFlowCanvasServerRevision,
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
    expect(mergeFlowCanvasPositions(nodes, { prompt: { x: 380, y: 120 } })).toEqual({
      input: { x: 80, y: 120 },
      prompt: { x: 380, y: 120 },
      ai: { x: 680, y: 120 },
      output: { x: 980, y: 120 }
    })
  })

  it('matches the server node-position boundary', () => {
    expect(MAX_FLOW_CANVAS_OFFSET).toBe(100_000)
  })

  it('places nodes into unused grid cells around manually moved nodes', () => {
    expect(mergeFlowCanvasPositions(nodes, {
      input: { x: 80, y: 120 },
      prompt: { x: 980, y: 120 }
    })).toEqual({
      input: { x: 80, y: 120 },
      prompt: { x: 980, y: 120 },
      ai: { x: 380, y: 120 },
      output: { x: 680, y: 120 }
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

  it('restores valid local edges, repairs duplicate IDs and falls back to the visual chain', () => {
    const saved = [
      { id: 'saved', source: 'input', target: 'output' },
      { id: 'duplicate', source: 'input', target: 'output' },
      { id: 'saved', source: 'prompt', target: 'output' },
      { id: 'invalid-handle', source: 'prompt', target: 'output', sourceHandle: 'missing' },
      { id: 'stale', source: 'gone', target: 'output' }
    ]
    const restored = resolveFlowCanvasEdges(saved, nodes, MarkerType.ArrowClosed)
    expect(restored).toHaveLength(2)
    expect(new Set(restored.map((edge) => edge.id)).size).toBe(2)
    expect(restored.map((edge) => [edge.source, edge.target])).toEqual([
      ['input', 'output'], ['prompt', 'output']
    ])
    expect(restored[0].ariaLabel).toBe('Input 连接到 Output')
    expect(restored[0].sourceHandle).toBe('source')
    expect(restored[0].targetHandle).toBe('target')
    expect(restored[0].focusable).toBe(true)
    expect(resolveFlowCanvasEdges([], nodes, MarkerType.ArrowClosed, false)).toHaveLength(3)
    expect(resolveFlowCanvasEdges([], nodes, MarkerType.ArrowClosed, true)).toHaveLength(0)
  })

  it('repairs blank and overlong edge IDs before they reach the API', () => {
    const restored = resolveFlowCanvasEdges([
      { id: '', source: 'input', target: 'prompt' },
      { id: 'x'.repeat(MAX_FLOW_CANVAS_EDGE_ID_LENGTH + 1), source: 'prompt', target: 'ai' }
    ], nodes, MarkerType.ArrowClosed)

    expect(restored).toHaveLength(2)
    expect(restored.every((edge) => edge.id.trim().length > 0 && edge.id.length <= MAX_FLOW_CANVAS_EDGE_ID_LENGTH)).toBe(true)
    expect(new Set(restored.map((edge) => edge.id)).size).toBe(2)
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

  it('normalizes viewport coordinates and zoom to backend limits', () => {
    expect(normalizeFlowCanvasViewport({ x: 120_000, y: -120_000, zoom: 2 })).toEqual({
      x: 100_000,
      y: -100_000,
      zoom: 1.6
    })
    expect(normalizeFlowCanvasViewport({ x: Number.NaN, y: 0, zoom: 1 })).toBeNull()
  })

  it('keeps legacy canvas snapshots without a viewport readable', () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: () => JSON.stringify({
          positions: { prompt: { x: 320, y: 160 } },
          edges: [{ id: 'legacy', source: 'input', target: 'prompt' }]
        })
      }
    })
    expect(readFlowCanvasState('flow')).toMatchObject({
      positions: { prompt: { x: 320, y: 160 } },
      edges: [{ id: 'legacy', source: 'input', target: 'prompt' }],
      viewport: null,
      hasSavedEdges: true
    })
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
      viewport,
      serverRevision: null
    })
  })

  it('stores a normalized viewport rather than an API-invalid camera transform', () => {
    const storage = new Map<string, string>()
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => storage.get(key) || null,
        setItem: (key: string, value: string) => storage.set(key, value)
      }
    })
    writeFlowCanvasState('flow', [], [], { x: 150_000, y: -150_000, zoom: 0.1 })

    expect(readFlowCanvasState('flow').viewport).toEqual({
      x: 100_000,
      y: -100_000,
      zoom: 0.2
    })
  })

  it('persists the last confirmed server revision without changing canvas content', () => {
    const storage = new Map<string, string>()
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => storage.get(key) || null,
        setItem: (key: string, value: string) => storage.set(key, value)
      }
    })
    writeFlowCanvasState('flow', [{ id: 'input', position: { x: 12, y: 24 } }], [], { x: 4, y: 8, zoom: 1 })

    expect(writeFlowCanvasServerRevision('flow', 7)).toBe(true)
    expect(readFlowCanvasState('flow')).toMatchObject({
      positions: { input: { x: 12, y: 24 } },
      viewport: { x: 4, y: 8, zoom: 1 },
      serverRevision: 7
    })
    writeFlowCanvasState('flow', [{ id: 'input', position: { x: 48, y: 96 } }], [], null)
    expect(readFlowCanvasState('flow')).toMatchObject({
      positions: { input: { x: 48, y: 96 } },
      serverRevision: 7
    })
    expect(writeFlowCanvasServerRevision('flow', -1)).toBe(false)
    expect(readFlowCanvasState('flow').serverRevision).toBe(7)
  })

  it.each([-1, 1.5, '7', Number.MAX_SAFE_INTEGER + 1])(
    'treats invalid cached server revision %s as unknown',
    (serverRevision) => {
      vi.stubGlobal('window', {
        localStorage: {
          getItem: () => JSON.stringify({ positions: {}, edges: [], serverRevision })
        }
      })

      expect(readFlowCanvasState('flow').serverRevision).toBeNull()
    }
  )

  it('reports when browser storage rejects the canvas snapshot', () => {
    vi.stubGlobal('window', {
      localStorage: {
        setItem: () => { throw new Error('storage denied') }
      }
    })
    expect(writeFlowCanvasState('flow', [], [])).toBe(false)
  })

  it('removes the saved canvas state for a deleted flow', () => {
    const removeItem = vi.fn()
    vi.stubGlobal('window', { localStorage: { removeItem } })
    removeFlowCanvasState('flow')
    expect(removeItem).toHaveBeenCalledWith('flowforge.canvas.v1:flow')
  })

  it('keeps flow deletion safe when browser storage is unavailable', () => {
    vi.stubGlobal('window', { localStorage: { removeItem: () => { throw new Error('storage denied') } } })
    expect(() => removeFlowCanvasState('flow')).not.toThrow()
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

  it('enforces the backend visual connection limit', () => {
    const edges = Array.from({ length: MAX_FLOW_CANVAS_EDGES }, (_, index) => ({
      id: `edge-${index}`,
      source: 'input',
      target: `node-${index}`
    })) as Edge[]

    expect(canCreateFlowCanvasEdge({ source: 'input', target: 'output' }, edges)).toBe(false)
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
    expect(createFlowCanvasEdgeId(new Set(['edge-1234-2']))).toBe('edge-1234-3')
  })
})
