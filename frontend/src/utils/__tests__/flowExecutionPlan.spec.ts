import { describe, expect, it } from 'vitest'
import {
  flowArtifactInputResolutionLabel,
  flowArtifactStorageLabel,
  flowArtifactStateLabel,
  flowArtifactTypeLabel,
  flowExecutionModeLabel,
  flowExecutionFailurePolicySummary,
  flowExecutionOperationForNode,
  flowExecutionOperationLabel,
  flowNodeRuntimeDescription,
  flowNodeRunTraceStatusDescription,
  flowNodeTypeLabel,
  flowProviderCallCountLabel,
  flowProviderInputSummary
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
    expect(flowArtifactStorageLabel('node-artifact')).toBe('独立节点产物')
    expect(flowArtifactStateLabel('materialized')).toBe('已记录')
    expect(flowArtifactStateLabel('failed')).toBe('未生成')
    expect(flowArtifactStateLabel('skipped')).toBe('已跳过')
    expect(flowArtifactInputResolutionLabel('compiled-reference')).toBe('单次编译引用')
    expect(flowArtifactInputResolutionLabel('persisted-artifact')).toBe('持久化产物输入')
  })

  it('explains the persisted single-pass failure policy without implying retries', () => {
    expect(flowExecutionFailurePolicySummary({
      version: 'flow-failure-policy-v1',
      onProviderFailure: 'stop-run',
      downstreamNodeAction: 'skip',
      retryStrategy: 'none',
      maxAttempts: 1
    })).toBe('Provider 失败时停止本次运行 · 下游节点跳过 · 不自动重试')
  })

  it('summarizes the declared provider fan-in without implying sequential execution', () => {
    expect(flowProviderInputSummary([
      { key: 'flow:objective', type: 'flow-objective', storage: 'flow-snapshot' },
      { key: 'node:input-1:context-contribution', type: 'context-contribution', storage: 'node-artifact' },
      { key: 'node:input-2:context-contribution', type: 'context-contribution', storage: 'node-artifact' },
      { key: 'node:prompt-1:instruction-contribution', type: 'instruction-contribution', storage: 'node-artifact' }
    ])).toBe('汇入 4 个已声明输入 · Flow 目标 · 上下文产物 x 2 · 指令产物')
    expect(flowProviderInputSummary(null)).toBe('')
  })

  it('explains single-pass node trace statuses without implying node execution', () => {
    expect(flowNodeRunTraceStatusDescription('prepared'))
      .toBe('已编译为共享 Provider 输入，未单独调用模型。')
    expect(flowNodeRunTraceStatusDescription('failed'))
      .toBe('Provider 边界失败，本次运行在这里停止。')
    expect(flowNodeRunTraceStatusDescription('skipped'))
      .toBe('上游运行失败，当前节点未执行。')
  })

  it('keeps missing legacy runtime evidence unknown', () => {
    expect(flowExecutionModeLabel('single-pass')).toBe('单次编译执行')
    expect(flowExecutionModeLabel(null)).toBe('运行模式未记录')
    expect(flowProviderCallCountLabel(1)).toBe('1 次 Provider 调用')
    expect(flowProviderCallCountLabel(undefined)).toBe('调用次数未记录')
  })

  it('explains the skipped Output state used after a Provider failure', () => {
    expect(flowNodeRunTraceStatusDescription('skipped'))
      .toBe('上游运行失败，当前节点未执行。')
  })

})
