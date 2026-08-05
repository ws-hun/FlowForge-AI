import type { FlowNode, FlowVersion } from '@/types'

type FlowRevisionSource = {
  title: string
  description: string
  nodes: FlowNode[]
}

export type FlowRevisionNodeChange = {
  id: string
  kind: 'restore' | 'remove' | 'update' | 'reorder'
  title: string
  detail: string
}

export type FlowRevisionDiff = {
  titleChanged: boolean
  descriptionChanged: boolean
  nodeChanges: FlowRevisionNodeChange[]
  changeCount: number
  hasChanges: boolean
}

export function compareFlowRevision(current: FlowRevisionSource, revision: FlowVersion): FlowRevisionDiff {
  const currentNodes = new Map(current.nodes.map((node) => [node.id, node]))
  const revisionNodes = new Map(revision.nodes.map((node) => [node.id, node]))
  const currentSharedOrder = current.nodes
    .filter((node) => revisionNodes.has(node.id))
    .map((node) => node.id)
  const revisionSharedOrder = revision.nodes
    .filter((node) => currentNodes.has(node.id))
    .map((node) => node.id)
  const currentSharedIndexes = new Map(currentSharedOrder.map((id, index) => [id, index]))
  const revisionSharedIndexes = new Map(revisionSharedOrder.map((id, index) => [id, index]))
  const nodeChanges: FlowRevisionNodeChange[] = []

  revision.nodes.forEach((node) => {
    const currentNode = currentNodes.get(node.id)
    if (!currentNode) {
      nodeChanges.push({
        id: node.id,
        kind: 'restore',
        title: node.title,
        detail: '会重新加入这个节点'
      })
      return
    }

    if (!sameNode(currentNode, node)) {
      nodeChanges.push({
        id: node.id,
        kind: 'update',
        title: node.title,
        detail: '会还原节点内容与说明'
      })
      return
    }

    if (currentSharedIndexes.get(node.id) !== revisionSharedIndexes.get(node.id)) {
      nodeChanges.push({
        id: node.id,
        kind: 'reorder',
        title: node.title,
        detail: '会恢复这个节点的执行顺序'
      })
    }
  })

  current.nodes.forEach((node) => {
    if (!revisionNodes.has(node.id)) {
      nodeChanges.push({
        id: node.id,
        kind: 'remove',
        title: node.title,
        detail: '会移除这个较新的节点'
      })
    }
  })

  const titleChanged = current.title !== revision.title
  const descriptionChanged = current.description !== revision.description
  const changeCount = Number(titleChanged) + Number(descriptionChanged) + nodeChanges.length

  return {
    titleChanged,
    descriptionChanged,
    nodeChanges,
    changeCount,
    hasChanges: changeCount > 0
  }
}

function sameNode(left: FlowNode, right: FlowNode) {
  return (
    left.type === right.type &&
    left.title === right.title &&
    left.description === right.description &&
    left.content === right.content &&
    left.promptId === right.promptId &&
    left.promptTitle === right.promptTitle
  )
}
