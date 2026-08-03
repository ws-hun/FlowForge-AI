import { describe, expect, it } from 'vitest'
import { compareFlowRunSnapshot } from '../flowRunSnapshots'
import type { FlowDraft, FlowNode, FlowRunSnapshot } from '@/types'

function node(id: string, title: string, content = title): FlowNode {
  return {
    id,
    type: 'prompt',
    title,
    description: `${title} description`,
    content
  }
}

function snapshot(nodes: FlowNode[]): FlowRunSnapshot {
  return {
    flowId: 'flow-1',
    title: 'Launch Flow',
    description: 'Prepare the launch plan',
    nodes,
    flowUpdatedAt: '2026-01-01T00:00:00.000Z',
    runtimeContext: '',
    variableValues: {}
  }
}

function currentFlow(nodes: FlowNode[]): FlowDraft {
  return {
    id: 'flow-1',
    title: 'Launch Flow',
    description: 'Prepare the launch plan',
    nodes,
    revision: 2,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-02T00:00:00.000Z'
  }
}

describe('Flow run snapshot comparison', () => {
  it('reports an unchanged current Flow', () => {
    const nodes = [node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')]
    expect(compareFlowRunSnapshot(currentFlow(nodes), snapshot(nodes))).toEqual({
      titleChanged: false,
      descriptionChanged: false,
      nodeChanges: [],
      changeCount: 0,
      hasChanges: false
    })
  })

  it('describes metadata and node changes since the historical run', () => {
    const historicalNodes = [node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')]
    const current = currentFlow([
      node('c', 'Deliver'),
      node('a', 'Intent', 'Updated intent'),
      node('d', 'Review')
    ])
    current.title = 'Launch Flow v2'
    current.description = 'Prepare and review the launch plan'

    expect(compareFlowRunSnapshot(current, snapshot(historicalNodes))).toEqual({
      titleChanged: true,
      descriptionChanged: true,
      nodeChanges: [
        { id: 'a', kind: 'updated', title: 'Intent' },
        { id: 'b', kind: 'removed', title: 'Draft' },
        { id: 'c', kind: 'reordered', title: 'Deliver' },
        { id: 'd', kind: 'added', title: 'Review' }
      ],
      changeCount: 6,
      hasChanges: true
    })
  })

  it('does not report reorder when shared node order is unchanged', () => {
    const historicalNodes = [node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')]
    const current = currentFlow([node('a', 'Intent'), node('c', 'Deliver'), node('d', 'Review')])
    const diff = compareFlowRunSnapshot(current, snapshot(historicalNodes))

    expect(diff.nodeChanges).toEqual([
      { id: 'b', kind: 'removed', title: 'Draft' },
      { id: 'd', kind: 'added', title: 'Review' }
    ])
  })
})
