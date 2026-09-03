import type { FlowNode } from '@/types'

export type FlowNodeReadinessIssue = 'title' | 'description' | 'content'

const issueLabels: Record<FlowNodeReadinessIssue, string> = {
  title: '标题',
  description: '说明',
  content: '内容'
}

export function flowNodeReadinessIssues(node: Pick<FlowNode, 'title' | 'description' | 'content'>) {
  const issues: FlowNodeReadinessIssue[] = []
  if (!node.title?.trim()) {
    issues.push('title')
  }
  if (!node.description?.trim()) {
    issues.push('description')
  }
  if (!node.content?.trim()) {
    issues.push('content')
  }
  return issues
}

export function flowNodeNeedsAttention(node: Pick<FlowNode, 'title' | 'description' | 'content'>) {
  return flowNodeReadinessIssues(node).length > 0
}

export function flowNodeReadinessLabel(node: Pick<FlowNode, 'title' | 'description' | 'content'>) {
  const issues = flowNodeReadinessIssues(node)
  return issues.length ? `待补充：${issues.map((issue) => issueLabels[issue]).join('、')}` : '已就绪'
}

export function flowNodeReadinessDescription(node: Pick<FlowNode, 'title' | 'description' | 'content'>) {
  const issues = flowNodeReadinessIssues(node)
  if (!issues.length) {
    return '节点内容已完整，可以进入 Flow 执行。'
  }
  return `请补充${issues.map((issue) => issueLabels[issue]).join('、')}，保存后才能进入真实 Flow 执行。`
}
