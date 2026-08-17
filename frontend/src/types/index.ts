export type Provider = 'deepseek' | 'openai'

export interface HealthResponse {
  status: 'up'
  database: 'reachable'
  timestamp: string
}

export interface RunTaskPayload {
  input: string
  promptId?: string | null
  flowId?: string | null
  flowRunContext?: string
  flowVariableValues?: Record<string, string>
  continuedFromTaskId?: string | null
  inputVariantOfTaskId?: string | null
}

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
  inputVariantOfTaskId?: string | null
  executionInput: string
  taskId?: string | null
  flowRunSnapshot?: FlowRunSnapshot | null
  flowRunTrace?: FlowRunTrace | null
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
  inputVariantOfTaskId?: string | null
  status?: 'completed' | 'failed' | null
  errorMessage?: string | null
  sourcePromptId?: string | null
  sourcePromptTitle?: string | null
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
  flowRunSnapshot?: FlowRunSnapshot | null
  flowRunTrace?: FlowRunTrace | null
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

export interface ProviderConnectionTestResponse {
  provider: Provider
  model: string
  status: 'connected'
  checkedAt: string
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
  revision: number
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
  revision?: number
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
  revision: number
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
  sourceFlowId?: string | null
  sourceFlowTitle?: string | null
  sourceFlowVersionId?: string | null
  sourceFlowVersionNumber?: number | null
  flowUpdatedAt: string
  runtimeContext: string
  variableValues: Record<string, string>
}

export type FlowNodeRunTraceStatus = 'prepared' | 'completed' | 'failed' | 'skipped'
export type FlowExecutionMode = 'single-pass' | 'node-sequential'
export type FlowExecutionOperation =
  | 'supply-context'
  | 'supply-instructions'
  | 'invoke-provider'
  | 'define-delivery'
export type FlowArtifactType =
  | 'flow-objective'
  | 'context-contribution'
  | 'instruction-contribution'
  | 'provider-result'
  | 'result-document'
export type FlowArtifactStorage = 'flow-snapshot' | 'trace-content' | 'task-result'

export interface FlowArtifactContract {
  key: string
  type: FlowArtifactType
  storage: FlowArtifactStorage
}

export interface FlowExecutionStep {
  sequence: number
  nodeId: string
  nodeType: FlowNodeType
  title: string
  operation: FlowExecutionOperation
  dependsOnNodeIds: string[]
  providerBoundary: boolean
  inputArtifact?: FlowArtifactContract | null
  outputArtifact?: FlowArtifactContract | null
}

export interface FlowExecutionPlan {
  version: string
  scheduling: 'linear'
  steps: FlowExecutionStep[]
}

export interface FlowNodeRunTrace {
  nodeId: string
  nodeType: FlowNodeType
  title: string
  status: FlowNodeRunTraceStatus
  compiledContent: string
  outputSummary?: string | null
  errorMessage?: string | null
}

export interface FlowRunTrace {
  runId?: string | null
  flowId: string
  status: 'completed' | 'failed'
  executionMode?: FlowExecutionMode | null
  providerCallCount: number
  compilerVersion?: string | null
  executionInputFingerprint?: string | null
  inputSource?: 'compiled-flow' | 'stored-input-replay' | null
  replayedFromTaskId?: string | null
  executionPlan?: FlowExecutionPlan | null
  nodes: FlowNodeRunTrace[]
}

export interface FlowExecutionPreviewRequest {
  runtimeContext?: string
  variableValues?: Record<string, string>
}

export type FlowExecutionSectionKind =
  | 'objective'
  | 'input-context'
  | 'runtime-context'
  | 'prompt'
  | 'execution-guidance'
  | 'delivery-focus'
  | 'response-contract'

export interface FlowExecutionSection {
  kind: FlowExecutionSectionKind
  nodeId?: string | null
  title: string
  content: string
}

export interface FlowExecutionPreviewResponse {
  executionMode: FlowExecutionMode
  providerCallCount: number
  compilerVersion: string
  executionInputFingerprint: string
  executionInput: string
  flowRunSnapshot: FlowRunSnapshot
  sections: FlowExecutionSection[]
  executionPlan: FlowExecutionPlan
  executable: boolean
  missingVariables: string[]
  incompleteNodes: string[]
}

export interface SaveFlowPayload {
  title: string
  description: string
  nodes: FlowNode[]
  sourceFlowId?: string | null
  sourceFlowVersionId?: string | null
  revision?: number
}
