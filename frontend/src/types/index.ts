export type Provider = 'deepseek' | 'openai'

export interface TaskRunResponse {
  summary: string
  result: string
  raw: string
}

export interface TaskHistoryItem {
  id: string
  input: string
  summary: string
  result: string
  sourcePromptId?: string | null
  sourcePromptTitle?: string | null
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
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

export interface SaveFlowPayload {
  title: string
  description: string
  nodes: FlowNode[]
}
