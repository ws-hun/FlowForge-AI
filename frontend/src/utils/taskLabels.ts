import type { TaskHistoryItem } from '@/types'

export interface TaskSourceLabelInput {
  sourceFlowTitle?: string | null
  sourcePromptTitle?: string | null
  sourceRunId?: string | null
  inputVariantOfTaskId?: string | null
}

export function taskSourceLabel(task: TaskSourceLabelInput) {
  if (task.sourceFlowTitle) return 'Flow 上下文'
  if (task.sourceRunId) return '历史结果'
  if (task.inputVariantOfTaskId) return '历史输入'
  if (task.sourcePromptTitle) return 'Prompt 上下文'
  return 'AI 任务'
}

export function taskHistoryKindLabel(task: Pick<TaskHistoryItem, 'continuedFromTaskId' | 'rerunOfTaskId' | 'recoveryOfTaskId' | 'inputVariantOfTaskId' | 'sourceFlowId' | 'sourcePromptId'>) {
  if (task.continuedFromTaskId) return '继续运行'
  if (task.rerunOfTaskId) return '重跑运行'
  if (task.recoveryOfTaskId) return '恢复运行'
  if (task.inputVariantOfTaskId) return '输入变体'
  if (task.sourceFlowId) return 'Flow 运行'
  if (task.sourcePromptId) return 'Prompt 运行'
  return 'AI 任务'
}
