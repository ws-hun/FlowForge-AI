import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  persistPromptRunDrafts,
  readPromptRunDrafts,
  removePromptRunDraft,
  sanitizePromptRunDraftVariables,
  upsertPromptRunDraft,
  type PromptRunDrafts
} from '../promptRunDrafts'
import { MemoryStorage } from './memoryStorage'

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Prompt run drafts', () => {
  it('round-trips independent variable values for each Prompt', () => {
    let drafts: PromptRunDrafts = {}
    drafts = upsertPromptRunDraft(
      drafts,
      'prompt-1',
      { ' audience ': ' product teams ', tone: ' concise ' },
      '2026-01-01T00:00:00.000Z'
    )
    drafts = upsertPromptRunDraft(
      drafts,
      'prompt-2',
      { market: 'developer tools' },
      '2026-01-02T00:00:00.000Z'
    )
    persistPromptRunDrafts(drafts)

    expect(readPromptRunDrafts()).toEqual({
      'prompt-2': {
        variableValues: { market: 'developer tools' },
        updatedAt: '2026-01-02T00:00:00.000Z'
      },
      'prompt-1': {
        variableValues: { audience: 'product teams', tone: 'concise' },
        updatedAt: '2026-01-01T00:00:00.000Z'
      }
    })
  })

  it('removes a Prompt draft when all variable values become empty', () => {
    const drafts = upsertPromptRunDraft(
      {},
      'prompt-1',
      { audience: 'teams' },
      '2026-01-01T00:00:00.000Z'
    )
    const clearedDrafts = upsertPromptRunDraft(drafts, 'prompt-1', { audience: '   ' })

    expect(clearedDrafts).toEqual({})
    expect(removePromptRunDraft(clearedDrafts, 'missing-prompt')).toBe(clearedDrafts)

    persistPromptRunDrafts(clearedDrafts)
    expect(window.localStorage.getItem('flowforge.promptRunDrafts')).toBeNull()
  })

  it('ignores corrupt or structurally invalid browser data', () => {
    window.localStorage.setItem('flowforge.promptRunDrafts', '{broken')
    expect(readPromptRunDrafts()).toEqual({})

    window.localStorage.setItem('flowforge.promptRunDrafts', JSON.stringify({
      empty: { variableValues: { audience: ' ' } },
      invalid: ['not', 'a', 'draft'],
      valid: {
        variableValues: { audience: 'Recover this' },
        updatedAt: 'not-a-date'
      }
    }))
    expect(readPromptRunDrafts()).toEqual({
      valid: {
        variableValues: { audience: 'Recover this' },
        updatedAt: '1970-01-01T00:00:00.000Z'
      }
    })
  })

  it('limits variable count, names, and values before persistence', () => {
    const variableValues = Object.fromEntries([
      [` ${'n'.repeat(140)} `, ` ${'v'.repeat(9000)} `],
      ...Array.from({ length: 60 }, (_, index) => [`variable-${index}`, `value-${index}`])
    ])
    const entries = Object.entries(sanitizePromptRunDraftVariables(variableValues))

    expect(entries).toHaveLength(50)
    expect(entries[0][0]).toHaveLength(120)
    expect(entries[0][1]).toHaveLength(8000)
  })

  it('keeps only the 30 most recently updated Prompt drafts', () => {
    let drafts: PromptRunDrafts = {}
    for (let index = 0; index < 35; index += 1) {
      drafts = upsertPromptRunDraft(
        drafts,
        `prompt-${index}`,
        { value: `${index}` },
        new Date(Date.UTC(2026, 0, index + 1)).toISOString()
      )
    }

    expect(Object.keys(drafts)).toHaveLength(30)
    expect(drafts['prompt-34']).toBeDefined()
    expect(drafts['prompt-5']).toBeDefined()
    expect(drafts['prompt-4']).toBeUndefined()
  })
})
