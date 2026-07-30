import type { FlowDraft, FlowNode, FlowNodeType, SaveFlowPayload } from '@/types'

export type FlowEditorSnapshot = Pick<FlowDraft, 'title' | 'description' | 'nodes'>

export type FlowEditorDraft = {
  flowId: string
  baseRevision: number
  nodeId: string
  flowChanged: boolean
  nodeChanged: boolean
  flowTitle: string
  flowDescription: string
  nodeTitle: string
  nodeDescription: string
  nodeContent: string
  snapshot: FlowEditorSnapshot
  updatedAt: string
}

const FLOW_EDITOR_DRAFT_STORAGE_KEY = 'flowforge.flowEditorDraft'

export function captureFlowEditorSnapshot(flow: FlowDraft): FlowEditorSnapshot {
  return {
    title: flow.title,
    description: flow.description,
    nodes: flow.nodes.map((node) => ({ ...node }))
  }
}

export function buildRecoveredFlowSnapshot(
  draft: FlowEditorDraft
): Pick<SaveFlowPayload, 'title' | 'description' | 'nodes'> {
  const snapshot: FlowEditorSnapshot = {
    title: draft.snapshot.title,
    description: draft.snapshot.description,
    nodes: draft.snapshot.nodes.map((node) => ({ ...node }))
  }

  if (draft.flowChanged) {
    snapshot.title = draft.flowTitle.trim() || snapshot.title
    snapshot.description = draft.flowDescription.trim() || snapshot.description
    const intentNode = snapshot.nodes.find((node) => node.type === 'input')
    if (intentNode) {
      intentNode.content = snapshot.description
      intentNode.description = '用户想完成的 AI 工作'
    }
  }

  if (draft.nodeChanged) {
    const targetNode = snapshot.nodes.find((node) => node.id === draft.nodeId)
    if (targetNode) {
      targetNode.title = draft.nodeTitle.trim() || targetNode.title
      targetNode.description = draft.nodeDescription.trim() || targetNode.description
      if (typeof targetNode.content === 'string') {
        targetNode.content = draft.nodeContent.trim() || targetNode.content
      }
    }
  }
  return snapshot
}

export function readFlowEditorDraft(): FlowEditorDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(FLOW_EDITOR_DRAFT_STORAGE_KEY) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }

    const candidate = value as Record<string, unknown>
    const flowId = readText(candidate.flowId, 80)
    const nodeId = readText(candidate.nodeId, 80)
    const baseRevision = typeof candidate.baseRevision === 'number' && candidate.baseRevision >= 0
      ? Math.floor(candidate.baseRevision)
      : null
    const snapshot = readSnapshot(candidate.snapshot)
    const flowChanged = candidate.flowChanged === true
    const nodeChanged = candidate.nodeChanged === true
    if (!flowId || !nodeId || baseRevision === null || !snapshot || (!flowChanged && !nodeChanged)) {
      return null
    }
    if (nodeChanged && !snapshot.nodes.some((node) => node.id === nodeId)) {
      return null
    }

    return {
      flowId,
      baseRevision,
      nodeId,
      flowChanged,
      nodeChanged,
      flowTitle: readText(candidate.flowTitle, 120, false),
      flowDescription: readText(candidate.flowDescription, 2000, false),
      nodeTitle: readText(candidate.nodeTitle, 120, false),
      nodeDescription: readText(candidate.nodeDescription, 500, false),
      nodeContent: readText(candidate.nodeContent, 12000, false),
      snapshot,
      updatedAt: typeof candidate.updatedAt === 'string' ? candidate.updatedAt : new Date(0).toISOString()
    }
  } catch {
    return null
  }
}

export function persistFlowEditorDraft(draft: FlowEditorDraft) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(FLOW_EDITOR_DRAFT_STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // Keep the active editor state in memory when browser storage is unavailable.
  }
}

export function removeFlowEditorDraft() {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.removeItem(FLOW_EDITOR_DRAFT_STORAGE_KEY)
  } catch {
    // Ignore cleanup failures because no server state is affected.
  }
}

function readSnapshot(value: unknown): FlowEditorSnapshot | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  const candidate = value as Record<string, unknown>
  const title = readText(candidate.title, 120)
  const description = readText(candidate.description, 2000)
  if (!title || !description || !Array.isArray(candidate.nodes) || candidate.nodes.length > 50) {
    return null
  }

  const nodes = candidate.nodes.map(readNode)
  if (nodes.some((node) => !node)) {
    return null
  }
  const validNodes = nodes as FlowNode[]
  if (!validNodes.length || new Set(validNodes.map((node) => node.id)).size !== validNodes.length) {
    return null
  }
  return { title, description, nodes: validNodes }
}

function readNode(value: unknown): FlowNode | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  const candidate = value as Record<string, unknown>
  const id = readText(candidate.id, 80)
  const type = typeof candidate.type === 'string' && ['input', 'prompt', 'ai-task', 'output'].includes(candidate.type)
    ? candidate.type as FlowNodeType
    : null
  const title = readText(candidate.title, 120)
  const description = readText(candidate.description, 500)
  if (!id || !type || !title || !description) {
    return null
  }

  return {
    id,
    type,
    title,
    description,
    content: typeof candidate.content === 'string'
      ? readText(candidate.content, 12000, false)
      : undefined,
    promptId: readOptionalText(candidate.promptId, 80),
    promptTitle: readOptionalText(candidate.promptTitle, 120)
  }
}

function readText(value: unknown, maxLength: number, trim = true) {
  if (typeof value !== 'string') {
    return ''
  }
  const text = trim ? value.trim() : value
  return text.slice(0, maxLength)
}

function readOptionalText(value: unknown, maxLength: number) {
  const text = readText(value, maxLength)
  return text || null
}
