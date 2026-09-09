import { describe, expect, it } from 'vitest'
import { presentFlowExecutionError } from '@/utils/flowExecutionError'

describe('flow execution error presentation', () => {
  it('turns node validation into an actionable readiness message', () => {
    expect(presentFlowExecutionError(400, 'Flow 节点标题不能为空: input-1')).toEqual({
      kind: 'node',
      title: '节点信息需要补充',
      detail: 'Flow 节点标题不能为空: input-1',
      actionLabel: '重试预览'
    })
  })

  it('keeps provider failures distinct from editable Flow problems', () => {
    expect(presentFlowExecutionError(502, 'Provider 暂时不可用')).toMatchObject({
      kind: 'provider',
      title: 'Provider 暂时无法执行'
    })
  })

  it('does not hide version conflicts behind a generic retry', () => {
    expect(presentFlowExecutionError(409, '资产已在其他窗口更新，请基于最新版本重新确认修改')).toMatchObject({
      kind: 'conflict',
      title: 'Flow 已在其他位置更新',
      actionLabel: '重新读取并预览'
    })
  })

  it('preserves an unknown backend message for inspection', () => {
    expect(presentFlowExecutionError(400, '自定义校验原因')).toMatchObject({
      kind: 'unknown',
      detail: '自定义校验原因'
    })
  })

  it('localizes backend node validation messages', () => {
    expect(presentFlowExecutionError(400, 'Unsupported Flow node type: context')).toMatchObject({
      kind: 'node',
      title: '节点信息需要补充',
      detail: '不支持的 Flow 节点类型：context'
    })
  })

  it('localizes missing provider configuration', () => {
    expect(presentFlowExecutionError(502, 'No active AI API key configured')).toMatchObject({
      kind: 'provider',
      detail: '还没有配置已激活的 AI Provider。'
    })
  })

  it('localizes missing Flow resources without losing the recovery action', () => {
    expect(presentFlowExecutionError(404, 'Flow not found')).toMatchObject({
      kind: 'missing',
      detail: '找不到这个 Flow，请重新读取工作区。',
      actionLabel: '重新读取'
    })
  })
})
