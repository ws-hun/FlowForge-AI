import { describe, expect, it } from 'vitest'
import { taskHistoryKindLabel, taskSourceLabel } from '@/utils/taskLabels'

describe('task labels', () => {
  it('uses consistent Chinese labels for task source handoffs', () => {
    expect(taskSourceLabel({ sourceFlowTitle: '产品 Flow', sourcePromptTitle: '', sourceRunId: '', inputVariantOfTaskId: '' }))
      .toBe('Flow 上下文')
    expect(taskSourceLabel({ sourceFlowTitle: '', sourcePromptTitle: '', sourceRunId: 'run-1', inputVariantOfTaskId: '' }))
      .toBe('历史结果')
    expect(taskSourceLabel({ sourceFlowTitle: '', sourcePromptTitle: '需求拆解', sourceRunId: '', inputVariantOfTaskId: '' }))
      .toBe('Prompt 上下文')
  })

  it('keeps lineage types readable in History and search', () => {
    expect(taskHistoryKindLabel({ continuedFromTaskId: 'run-1', rerunOfTaskId: '', recoveryOfTaskId: '', inputVariantOfTaskId: '', sourceFlowId: '', sourcePromptId: '' }))
      .toBe('继续运行')
    expect(taskHistoryKindLabel({ continuedFromTaskId: '', rerunOfTaskId: '', recoveryOfTaskId: 'run-1', inputVariantOfTaskId: '', sourceFlowId: '', sourcePromptId: '' }))
      .toBe('恢复运行')
    expect(taskHistoryKindLabel({ continuedFromTaskId: '', rerunOfTaskId: '', recoveryOfTaskId: '', inputVariantOfTaskId: '', sourceFlowId: 'flow-1', sourcePromptId: '' }))
      .toBe('Flow 运行')
  })
})
