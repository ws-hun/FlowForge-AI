import { describe, expect, it } from 'vitest'
import { compareFlowRevision } from '../flowRevisions'
import type { FlowNode, FlowVersion } from '@/types'

function node(id: string, title: string, content = title): FlowNode {
  return {
    id,
    type: 'prompt',
    title,
    description: `${title} description`,
    content
  }
}

function revision(nodes: FlowNode[]): FlowVersion {
  return {
    id: 'version-1',
    flowId: 'flow-1',
    versionNumber: 1,
    title: 'Launch Flow',
    description: 'Prepare the launch plan',
    nodes,
    createdAt: '2026-01-01T00:00:00.000Z'
  }
}

describe('Flow revision comparison', () => {
  it('reports metadata, content, and real shared-node order changes', () => {
    const savedRevision = revision([node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')])
    const current = {
      title: 'Launch Flow v2',
      description: 'Prepare and review the launch plan',
      nodes: [
        node('c', 'Deliver'),
        node('a', 'Intent', 'Updated intent'),
        node('d', 'Review')
      ]
    }

    expect(compareFlowRevision(current, savedRevision)).toEqual({
      titleChanged: true,
      descriptionChanged: true,
      nodeChanges: [
        { id: 'a', kind: 'update', title: 'Intent', detail: '会还原节点内容与说明' },
        { id: 'b', kind: 'restore', title: 'Draft', detail: '会重新加入这个节点' },
        { id: 'c', kind: 'reorder', title: 'Deliver', detail: '会恢复这个节点的执行顺序' },
        { id: 'd', kind: 'remove', title: 'Review', detail: '会移除这个较新的节点' }
      ],
      changeCount: 6,
      hasChanges: true
    })
  })

  it('does not report reorder when only node membership changed', () => {
    const savedRevision = revision([node('a', 'Intent'), node('b', 'Draft'), node('c', 'Deliver')])
    const current = {
      title: savedRevision.title,
      description: savedRevision.description,
      nodes: [node('a', 'Intent'), node('c', 'Deliver'), node('d', 'Review')]
    }

    expect(compareFlowRevision(current, savedRevision).nodeChanges).toEqual([
      { id: 'b', kind: 'restore', title: 'Draft', detail: '会重新加入这个节点' },
      { id: 'd', kind: 'remove', title: 'Review', detail: '会移除这个较新的节点' }
    ])
  })
})
