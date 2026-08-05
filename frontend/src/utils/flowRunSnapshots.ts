import type { FlowNode, FlowRunSnapshot } from '@/types'

export type CurrentFlowSnapshot = {
  id: string
  title: string
  description: string
  nodes: FlowNode[]
}

export type FlowRunSnapshotNodeChange = {
  id: string
  kind: 'added' | 'removed' | 'updated' | 'reordered'
  title: string
}

export type FlowRunSnapshotDiff = {
  titleChanged: boolean
  descriptionChanged: boolean
  nodeChanges: FlowRunSnapshotNodeChange[]
  changeCount: number
  hasChanges: boolean
}

export type FlowRunSettings = {
  runtimeContext: string
  variableValues: Record<string, string>
}

export type FlowRunVariableChange = {
  name: string
  kind: 'added' | 'removed' | 'updated'
}

export type FlowRunSettingsDiff = {
  runtimeContextChanged: boolean
  variableChanges: FlowRunVariableChange[]
  changeCount: number
  hasChanges: boolean
}

export function compareFlowRunSnapshot(
  current: CurrentFlowSnapshot,
  snapshot: FlowRunSnapshot
): FlowRunSnapshotDiff {
  const currentNodes = new Map(current.nodes.map((node) => [node.id, node]))
  const snapshotNodes = new Map(snapshot.nodes.map((node) => [node.id, node]))
  const currentSharedOrder = current.nodes
    .filter((node) => snapshotNodes.has(node.id))
    .map((node) => node.id)
  const snapshotSharedOrder = snapshot.nodes
    .filter((node) => currentNodes.has(node.id))
    .map((node) => node.id)
  const currentSharedIndexes = new Map(currentSharedOrder.map((id, index) => [id, index]))
  const snapshotSharedIndexes = new Map(snapshotSharedOrder.map((id, index) => [id, index]))
  const nodeChanges: FlowRunSnapshotNodeChange[] = []

  snapshot.nodes.forEach((node) => {
    const currentNode = currentNodes.get(node.id)
    if (!currentNode) {
      nodeChanges.push({ id: node.id, kind: 'removed', title: node.title })
      return
    }
    if (!sameNode(currentNode, node)) {
      nodeChanges.push({ id: node.id, kind: 'updated', title: node.title })
      return
    }
    if (currentSharedIndexes.get(node.id) !== snapshotSharedIndexes.get(node.id)) {
      nodeChanges.push({ id: node.id, kind: 'reordered', title: node.title })
    }
  })

  current.nodes.forEach((node) => {
    if (!snapshotNodes.has(node.id)) {
      nodeChanges.push({ id: node.id, kind: 'added', title: node.title })
    }
  })

  const titleChanged = current.title !== snapshot.title
  const descriptionChanged = current.description !== snapshot.description
  const changeCount = Number(titleChanged) + Number(descriptionChanged) + nodeChanges.length
  return {
    titleChanged,
    descriptionChanged,
    nodeChanges,
    changeCount,
    hasChanges: changeCount > 0
  }
}

export function compareFlowRunSettings(
  current: FlowRunSettings,
  snapshot: FlowRunSettings
): FlowRunSettingsDiff {
  const currentVariables = normalizeVariableValues(current.variableValues)
  const snapshotVariables = normalizeVariableValues(snapshot.variableValues)
  const variableNames = new Set([...Object.keys(snapshotVariables), ...Object.keys(currentVariables)])
  const variableChanges = Array.from(variableNames).flatMap<FlowRunVariableChange>((name) => {
    const currentValue = currentVariables[name]
    const snapshotValue = snapshotVariables[name]
    if (currentValue === snapshotValue) {
      return []
    }
    if (snapshotValue === undefined) {
      return [{ name, kind: 'added' }]
    }
    if (currentValue === undefined) {
      return [{ name, kind: 'removed' }]
    }
    return [{ name, kind: 'updated' }]
  })
  const runtimeContextChanged = normalizeText(current.runtimeContext) !== normalizeText(snapshot.runtimeContext)
  const changeCount = Number(runtimeContextChanged) + variableChanges.length
  return {
    runtimeContextChanged,
    variableChanges,
    changeCount,
    hasChanges: changeCount > 0
  }
}

export function hasFlowRunSettings(settings: FlowRunSettings) {
  return Boolean(
    normalizeText(settings.runtimeContext) ||
      Object.values(settings.variableValues).some((value) => normalizeText(value))
  )
}

export function shouldConfirmFlowRunSettingsReplacement(
  current: FlowRunSettings,
  snapshot: FlowRunSettings
) {
  return hasFlowRunSettings(current) && compareFlowRunSettings(current, snapshot).hasChanges
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

function normalizeVariableValues(value: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(value).flatMap(([name, variableValue]) => {
      const cleanName = name.trim()
      const cleanValue = normalizeText(variableValue)
      return cleanName && cleanValue ? [[cleanName, cleanValue]] : []
    })
  )
}

function normalizeText(value: string) {
  return value.trim()
}
