import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  persistAiCommandDraft,
  readAiCommandDraft,
  type AiCommandDraft
} from '../aiCommandDraft'
import { MemoryStorage } from './memoryStorage'

const standaloneDraft: AiCommandDraft = {
  input: 'Review this architecture proposal',
  sourcePromptId: null,
  sourcePromptTitle: '',
  sourceFlowId: null,
  sourceFlowTitle: '',
  sourceFlowVariableValues: {},
  sourceRunId: null,
  sourceRunSummary: '',
  inputVariantOfTaskId: null,
  inputVariantSourceTitle: '',
  updatedAt: '2026-01-03T00:00:00.000Z'
}

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AI Command drafts', () => {
  it('round-trips standalone input and clears it explicitly', () => {
    persistAiCommandDraft(standaloneDraft)

    expect(readAiCommandDraft()).toEqual(standaloneDraft)

    persistAiCommandDraft(null)
    expect(readAiCommandDraft()).toBeNull()
  })

  it('restores one Flow source and bounds its variable context', () => {
    const variableValues = Object.fromEntries([
      [`  ${'n'.repeat(130)}  `, 'v'.repeat(9000)],
      ...Array.from({ length: 54 }, (_, index) => [`variable_${index}`, `value_${index}`])
    ])
    window.localStorage.setItem('flowforge.aiCommandDraft', JSON.stringify({
      ...standaloneDraft,
      input: '  keep runtime spacing  ',
      sourceFlowId: ' flow-1 ',
      sourceFlowTitle: '  API Review Flow  ',
      sourceFlowVariableValues: variableValues
    }))

    const draft = readAiCommandDraft()
    expect(draft).not.toBeNull()
    expect(draft?.input).toBe('  keep runtime spacing  ')
    expect(draft?.sourceFlowId).toBe('flow-1')
    expect(draft?.sourceFlowTitle).toBe('API Review Flow')
    expect(Object.keys(draft?.sourceFlowVariableValues || {})).toHaveLength(50)
    expect(Object.keys(draft?.sourceFlowVariableValues || {})[0]).toHaveLength(120)
    expect(Object.values(draft?.sourceFlowVariableValues || {})[0]).toHaveLength(8000)
  })

  it('detaches conflicting sources while preserving usable input', () => {
    window.localStorage.setItem('flowforge.aiCommandDraft', JSON.stringify({
      ...standaloneDraft,
      sourcePromptId: 'prompt-1',
      sourcePromptTitle: 'Prompt source',
      sourceRunId: 'run-1',
      sourceRunSummary: 'Result source'
    }))

    expect(readAiCommandDraft()).toMatchObject({
      input: standaloneDraft.input,
      sourcePromptId: null,
      sourceFlowId: null,
      sourceRunId: null,
      inputVariantOfTaskId: null,
      updatedAt: expect.any(String)
    })
  })

  it('keeps a source-only Prompt draft but rejects unusable conflicts', () => {
    window.localStorage.setItem('flowforge.aiCommandDraft', JSON.stringify({
      ...standaloneDraft,
      input: '',
      sourcePromptId: 'prompt-1',
      sourcePromptTitle: 'Reusable prompt'
    }))
    expect(readAiCommandDraft()).toMatchObject({
      input: '',
      sourcePromptId: 'prompt-1',
      sourcePromptTitle: 'Reusable prompt'
    })

    window.localStorage.setItem('flowforge.aiCommandDraft', JSON.stringify({
      input: '',
      sourcePromptId: 'prompt-1',
      sourceFlowId: 'flow-1'
    }))
    expect(readAiCommandDraft()).toBeNull()
  })

  it('ignores malformed or empty browser data', () => {
    window.localStorage.setItem('flowforge.aiCommandDraft', '{broken')
    expect(readAiCommandDraft()).toBeNull()

    window.localStorage.setItem('flowforge.aiCommandDraft', JSON.stringify({ input: '   ' }))
    expect(readAiCommandDraft()).toBeNull()
  })
})
