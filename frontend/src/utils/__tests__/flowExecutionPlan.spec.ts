import { describe, expect, it } from 'vitest'
import { flowExecutionOperationLabel, flowNodeTypeLabel } from '@/utils/flowExecutionPlan'

describe('flow execution plan labels', () => {
  it('keeps every node responsibility explicit', () => {
    expect(flowNodeTypeLabel('ai-task')).toBe('AI Task')
    expect(flowExecutionOperationLabel('supply-context')).toBe('提供运行上下文')
    expect(flowExecutionOperationLabel('supply-instructions')).toBe('注入可复用指令')
    expect(flowExecutionOperationLabel('invoke-provider')).toBe('调用 AI Provider')
    expect(flowExecutionOperationLabel('define-delivery')).toBe('约束结果交付')
  })
})
