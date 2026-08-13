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

export function flowNodeTypeLabel(type: FlowNodeType) {
  return nodeTypeLabels[type]
}

export function flowExecutionOperationLabel(operation: FlowExecutionOperation) {
  return operationLabels[operation]
}
