export type Provider = 'deepseek' | 'openai'

export interface TaskRunResponse {
  summary: string
  result: string
  raw: string
  provider?: Provider | null
  model?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  totalTokens?: number | null
  durationMs?: number | null
  rerunOfTaskId?: string | null
  continuedFromTaskId?: string | null
  executionInput: string
  taskId?: string | null
  flowRunSnapshot?: FlowRunSnapshot | null
}

export interface TaskHistoryItem {
  id: string
  input: string
  summary: string
  result: string
  provider?: Provider | null
  model?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  totalTokens?: number | null
  durationMs?: number | null
  rerunOfTaskId?: string | null
  continuedFromTaskId?: string | null
  status?: 'completed' | 'failed' | null
  errorMessage?: string | null
  sourcePromptId?: string | null
  sourcePromptTitle?: string | null
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
  flowRunSnapshot?: FlowRunSnapshot | null
  createdAt: string
}

export interface ApiKeyConfig {
  id: string
  provider: Provider
  maskedKey: string
  baseUrl: string
  model: string
  active: boolean
  updatedAt: string
}

export interface SaveApiKeyPayload {
  provider: Provider
  apiKey: string
  baseUrl: string
  model: string
  active: boolean
}

export interface PromptAsset {
  id: string
  title: string
  category: string
  description: string
  content: string
  tags: string[]
  favorite: boolean
  sourceTaskId?: string | null
  sourceTaskSummary?: string | null
  sourcePromptId?: string | null
  sourcePromptTitle?: string | null
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
  sourceNodeId?: string | null
  sourceNodeTitle?: string | null
  createdAt: string
  updatedAt: string
}

export interface PromptVersion {
  id: string
  promptId: string
  versionNumber: number
  title: string
  category: string
  description: string
  content: string
  tags: string[]
  favorite: boolean
  createdAt: string
}

export interface SavePromptPayload {
  title: string
  category: string
  description: string
  content: string
  tags: string[]
  favorite: boolean
  sourceTaskId?: string | null
  sourcePromptId?: string | null
  sourceFlowId?: string | null
  sourceNodeId?: string | null
}

export type FlowNodeType = 'input' | 'prompt' | 'ai-task' | 'output'

export interface FlowNode {
  id: string
  type: FlowNodeType
  title: string
  description: string
  content?: string
  promptId?: string | null
  promptTitle?: string | null
}

export interface FlowDraft {
  id: string
  title: string
  description: string
  nodes: FlowNode[]
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
  sourceFlowVersionId?: string | null
  sourceFlowVersionNumber?: number | null
  createdAt: string
  updatedAt: string
}

export interface FlowVersion {
  id: string
  flowId: string
  versionNumber: number
  title: string
  description: string
  nodes: FlowNode[]
  createdAt: string
}

export interface FlowRunSnapshot {
  flowId: string
  title: string
  description: string
  nodes: FlowNode[]
  flowUpdatedAt: string
  runtimeContext: string
  variableValues: Record<string, string>
}

export interface FlowExecutionPreviewRequest {
  runtimeContext?: string
  variableValues?: Record<string, string>
}

export interface FlowExecutionPreviewResponse {
  executionInput: string
  flowRunSnapshot: FlowRunSnapshot
}

export interface SaveFlowPayload {
  title: string
  description: string
  nodes: FlowNode[]
  sourceFlowId?: string | null
  sourceFlowVersionId?: string | null
}
