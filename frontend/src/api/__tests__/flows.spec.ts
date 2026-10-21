import { describe, expect, it, vi } from 'vitest'
import { getFlowCanvasState, saveFlowCanvasState } from '@/api/flows'

const { get, put } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))

vi.mock('@/api/client', () => ({ default: { get, put } }))

describe('flow canvas API', () => {
  it('loads saved canvas state for a Flow', () => {
    getFlowCanvasState('flow-1')
    expect(get).toHaveBeenCalledWith('/api/flows/flow-1/canvas')
  })

  it('saves canvas state with its independent revision', () => {
    const payload = {
      revision: 3,
      positions: { input: { x: 80, y: 120 } },
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 }
    }
    saveFlowCanvasState('flow-1', payload)
    expect(put).toHaveBeenCalledWith('/api/flows/flow-1/canvas', payload)
  })
})
