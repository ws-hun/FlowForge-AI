import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildFlowCanvasNodes,
  buildSequentialCanvasEdges,
  readFlowCanvasState,
  resolveFlowCanvasEdges,
  writeFlowCanvasState
} from '@/utils/flowCanvasState'
import type { FlowDraft, FlowNode } from '@/types'
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

  it('restores only valid local edges and falls back to the visual chain', () => {
    const saved = [{ id: 'saved', source: 'input', target: 'output' }, { id: 'stale', source: 'gone', target: 'output' }]
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
})
