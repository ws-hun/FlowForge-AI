import { describe, expect, it } from 'vitest'
import {
  MAX_FLOW_CONTEXT_LENGTH,
  canPersistFlowContext,
  createFlowContextNode,
  normalizeFlowContextContent
} from '../flowContext'

describe('Flow context creation', () => {
  it('builds an empty editable Context node', () => {
    expect(createFlowContextNode('context-1')).toEqual({
      id: 'context-1',
      type: 'input',
      title: 'Context',
      description: '补充本次 Flow 需要参考的背景、约束或已有材料',
      content: ''
    })
  })

  it('turns a trimmed Run Brief into reusable Flow context', () => {
    expect(createFlowContextNode('context-2', '  Keep the launch scope focused.  ')).toEqual({
      id: 'context-2',
      type: 'input',
      title: 'Run Brief Context',
      description: '从 Run Brief 固化的可复用背景、约束或已有材料',
      content: 'Keep the launch scope focused.'
    })
  })

  it('only accepts meaningful content within the persisted node limit', () => {
    expect(normalizeFlowContextContent('  reusable context  ')).toBe('reusable context')
    expect(canPersistFlowContext('   ')).toBe(false)
    expect(canPersistFlowContext('x'.repeat(MAX_FLOW_CONTEXT_LENGTH))).toBe(true)
    expect(canPersistFlowContext('x'.repeat(MAX_FLOW_CONTEXT_LENGTH + 1))).toBe(false)
  })
})
