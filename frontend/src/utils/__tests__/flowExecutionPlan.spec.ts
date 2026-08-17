import { describe, expect, it } from 'vitest'
import {
  flowArtifactStorageLabel,
  flowArtifactTypeLabel,
  flowExecutionOperationForNode,
  flowExecutionOperationLabel,
  flowNodeRuntimeDescription,
  flowNodeTypeLabel
} from '@/utils/flowExecutionPlan'

describe('flow execution plan labels', () => {
  it('keeps every node responsibility explicit', () => {
    expect(flowNodeTypeLabel('ai-task')).toBe('AI Task')
    expect(flowExecutionOperationLabel('supply-context')).toBe('提供运行上下文')
    expect(flowExecutionOperationLabel('supply-instructions')).toBe('注入可复用指令')
    expect(flowExecutionOperationLabel('invoke-provider')).toBe('调用 AI Provider')
    expect(flowExecutionOperationLabel('define-delivery')).toBe('约束结果交付')
  })

  it('keeps editor guidance aligned with the single provider runtime', () => {
    expect(flowExecutionOperationForNode('input')).toBe('supply-context')
    expect(flowExecutionOperationForNode('prompt')).toBe('supply-instructions')
    expect(flowExecutionOperationForNode('ai-task')).toBe('invoke-provider')
    expect(flowExecutionOperationForNode('output')).toBe('define-delivery')
    expect(flowNodeRuntimeDescription('ai-task')).toContain('唯一触发 Provider 调用')
    expect(flowNodeRuntimeDescription('output')).toContain('不额外调用模型')
  })

  it('names artifact contracts by their real persisted record', () => {
    expect(flowArtifactTypeLabel('flow-objective')).toBe('Flow 目标')
    expect(flowArtifactTypeLabel('context-contribution')).toBe('上下文产物')
    expect(flowArtifactTypeLabel('provider-result')).toBe('Provider 结果')
    expect(flowArtifactStorageLabel('flow-snapshot')).toBe('Flow 快照')
    expect(flowArtifactStorageLabel('trace-content')).toBe('节点轨迹')
    expect(flowArtifactStorageLabel('task-result')).toBe('Task Result')
  })
})
