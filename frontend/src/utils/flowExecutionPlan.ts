import type {
  FlowArtifactContract,
  FlowArtifactInputResolution,
  FlowArtifactStorage,
  FlowArtifactState,
  FlowArtifactType,
  FlowExecutionFailurePolicy,
  FlowExecutionOperation,
  FlowNodeType
} from '@/types'

const nodeTypeLabels: Record<FlowNodeType, string> = {
  input: 'Input',
  prompt: 'Prompt',
  'ai-task': 'AI Task',
  output: 'Output'
}

const operationLabels: Record<FlowExecutionOperation, string> = {
  'supply-context': '提供运行上下文',
  'supply-instructions': '注入可复用指令',
  'invoke-provider': '调用 AI Provider',
  'define-delivery': '约束结果交付'
}

const nodeOperations: Record<FlowNodeType, FlowExecutionOperation> = {
  input: 'supply-context',
  prompt: 'supply-instructions',
  'ai-task': 'invoke-provider',
  output: 'define-delivery'
}

const nodeRuntimeDescriptions: Record<FlowNodeType, string> = {
  input: '将保存内容编译为本次 Provider 输入上下文，不单独调用模型。',
  prompt: '将可复用 Prompt 注入执行输入，与前置上下文共同约束 AI。',
  'ai-task': '承接此前全部上下文，是当前 Flow 唯一触发 Provider 调用的节点。',
  output: '定义结果的交付重点，与 AI Task 共用一次 Provider 结果，不额外调用模型。'
}

const artifactTypeLabels: Record<FlowArtifactType, string> = {
  'flow-objective': 'Flow 目标',
  'context-contribution': '上下文产物',
  'instruction-contribution': '指令产物',
  'provider-result': 'Provider 结果',
  'result-document': '结果文档'
}

const artifactStorageLabels: Record<FlowArtifactStorage, string> = {
  'flow-snapshot': 'Flow 快照',
  'trace-content': '节点轨迹',
  'task-result': 'Task Result',
  'node-artifact': '独立节点产物'
}

const artifactStateLabels: Record<FlowArtifactState, string> = {
  materialized: '已记录',
  failed: '未生成',
  skipped: '已跳过'
}

const artifactInputResolutionLabels: Record<FlowArtifactInputResolution, string> = {
  'compiled-reference': '单次编译引用',
  'persisted-artifact': '持久化产物输入'
}

const nodeRunTraceStatusDescriptions: Record<string, string> = {
  prepared: '已编译为共享 Provider 输入，未单独调用模型。',
  completed: '本次运行已完成该节点对应的运行职责。',
  failed: 'Provider 边界失败，本次运行在这里停止。',
  skipped: '上游运行失败，当前节点未执行。'
}

export function flowNodeTypeLabel(type: FlowNodeType) {
  return nodeTypeLabels[type]
}

export function flowExecutionOperationLabel(operation: FlowExecutionOperation) {
  return operationLabels[operation]
}

export function flowExecutionOperationForNode(type: FlowNodeType) {
  return nodeOperations[type]
}

export function flowNodeRuntimeDescription(type: FlowNodeType) {
  return nodeRuntimeDescriptions[type]
}

export function flowArtifactTypeLabel(type: FlowArtifactType) {
  return artifactTypeLabels[type]
}

export function flowArtifactStorageLabel(storage: FlowArtifactStorage) {
  return artifactStorageLabels[storage]
}

export function flowArtifactStateLabel(state: FlowArtifactState) {
  return artifactStateLabels[state]
}

export function flowNodeRunTraceStatusDescription(status: string) {
  return nodeRunTraceStatusDescriptions[status] || '运行状态已保存。'
}

export function flowArtifactInputResolutionLabel(resolution: FlowArtifactInputResolution) {
  return artifactInputResolutionLabels[resolution]
}

export function flowProviderInputSummary(artifacts: FlowArtifactContract[] | null | undefined) {
  if (!artifacts?.length) {
    return ''
  }

  const typeCounts = new Map<FlowArtifactType, number>()
  artifacts.forEach((artifact) => {
    typeCounts.set(artifact.type, (typeCounts.get(artifact.type) || 0) + 1)
  })
  const types = Array.from(typeCounts.entries()).map(([type, count]) => (
    `${flowArtifactTypeLabel(type)}${count > 1 ? ` x ${count}` : ''}`
  ))

  return `汇入 ${artifacts.length} 个已声明输入 · ${types.join(' · ')}`
}

export function flowExecutionFailurePolicySummary(policy: FlowExecutionFailurePolicy) {
  const providerFailure = policy.onProviderFailure === 'stop-run'
    ? 'Provider 失败时停止本次运行'
    : policy.onProviderFailure
  const downstream = policy.downstreamNodeAction === 'skip'
    ? '下游节点跳过'
    : policy.downstreamNodeAction
  const retry = policy.retryStrategy === 'none' && policy.maxAttempts === 1
    ? '不自动重试'
    : `最多尝试 ${policy.maxAttempts} 次`

  return `${providerFailure} · ${downstream} · ${retry}`
}
