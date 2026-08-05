import type { FlowNode } from '@/types'

export const MAX_FLOW_CONTEXT_LENGTH = 12000

export function normalizeFlowContextContent(value: string) {
  return value.trim()
}

export function canPersistFlowContext(value: string) {
  const content = normalizeFlowContextContent(value)
  return Boolean(content && content.length <= MAX_FLOW_CONTEXT_LENGTH)
}

export function createFlowContextNode(id: string, content = ''): FlowNode {
  const cleanContent = normalizeFlowContextContent(content)
  return {
    id,
    type: 'input',
    title: cleanContent ? 'Run Brief Context' : 'Context',
    description: cleanContent
      ? '从 Run Brief 固化的可复用背景、约束或已有材料'
      : '补充本次 Flow 需要参考的背景、约束或已有材料',
    content: cleanContent
  }
}
