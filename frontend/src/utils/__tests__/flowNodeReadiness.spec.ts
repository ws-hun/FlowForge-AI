import { describe, expect, it } from 'vitest'
import {
  flowNodeNeedsAttention,
  flowNodeReadinessDescription,
  flowNodeReadinessIssues,
  flowNodeReadinessLabel
} from '@/utils/flowNodeReadiness'

describe('flow node readiness', () => {
  it('accepts a complete node', () => {
    const node = { title: '输入', description: '用户提供的背景', content: '产品背景' }

    expect(flowNodeReadinessIssues(node)).toEqual([])
    expect(flowNodeNeedsAttention(node)).toBe(false)
    expect(flowNodeReadinessLabel(node)).toBe('已就绪')
  })

  it('reports every missing field in the same order as the backend contract', () => {
    const node = { title: ' ', description: '', content: ' ' }

    expect(flowNodeReadinessIssues(node)).toEqual(['title', 'description', 'content'])
    expect(flowNodeReadinessLabel(node)).toBe('待补充：标题、说明、内容')
    expect(flowNodeReadinessDescription(node)).toBe('请补充标题、说明、内容，保存后才能进入真实 Flow 执行。')
  })

  it('keeps empty content as an actionable preflight state', () => {
    const node = { title: 'Prompt', description: '可复用指令', content: '' }

    expect(flowNodeNeedsAttention(node)).toBe(true)
    expect(flowNodeReadinessLabel(node)).toBe('待补充：内容')
  })
})
