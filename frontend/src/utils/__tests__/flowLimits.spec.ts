import { describe, expect, it } from 'vitest'
import { canAddFlowNode, MAX_FLOW_NODES } from '@/utils/flowLimits'

describe('Flow node capacity', () => {
  it('allows adding nodes up to the server limit', () => {
    expect(canAddFlowNode(MAX_FLOW_NODES - 1)).toBe(true)
    expect(canAddFlowNode(MAX_FLOW_NODES)).toBe(false)
    expect(canAddFlowNode(MAX_FLOW_NODES + 1)).toBe(false)
  })

  it('rejects invalid node counts', () => {
    expect(canAddFlowNode(-1)).toBe(false)
    expect(canAddFlowNode(1.5)).toBe(false)
    expect(canAddFlowNode(Number.NaN)).toBe(false)
  })
})
