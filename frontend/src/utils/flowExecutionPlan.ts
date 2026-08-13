import type { FlowExecutionOperation, FlowNodeType } from '@/types'

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
