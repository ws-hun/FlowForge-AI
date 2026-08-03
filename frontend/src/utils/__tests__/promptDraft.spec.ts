import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  parsePromptDraftTags,
  persistPromptEditorDraft,
  readPromptEditorDraft,
  removePromptEditorDraft,
  type PromptEditorDraft
} from '../promptEditorDraft'
import { MemoryStorage } from './memoryStorage'

const savedDraft: PromptEditorDraft = {
  promptId: 'prompt-1',
  baseRevision: 7,
  title: 'API review',
  category: 'Engineering',
  description: 'Review an API contract before implementation',
  content: 'Review {contract} for compatibility and edge cases.',
  tagInput: 'API, Review, Backend',
  favorite: true,
  updatedAt: '2026-01-03T00:00:00.000Z'
}

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Prompt editor drafts', () => {
  it('round-trips a revision-linked draft and clears it explicitly', () => {
    persistPromptEditorDraft(savedDraft)

    expect(readPromptEditorDraft()).toEqual(savedDraft)

    removePromptEditorDraft()
    expect(readPromptEditorDraft()).toBeNull()
  })

  it('keeps unsaved creation drafts without a source Prompt', () => {
    const creationDraft: PromptEditorDraft = {
      ...savedDraft,
      promptId: null,
      baseRevision: null,
      favorite: false
    }
    persistPromptEditorDraft(creationDraft)

    expect(readPromptEditorDraft()).toEqual(creationDraft)
  })

  it('normalizes revision and field boundaries while preserving editor whitespace', () => {
    window.localStorage.setItem('flowforge.promptEditorDraft', JSON.stringify({
      ...savedDraft,
      promptId: `  ${'p'.repeat(90)}  `,
      baseRevision: 9.8,
      title: `  ${'t'.repeat(130)}  `,
      category: `  ${'c'.repeat(90)}  `,
      description: `  ${'d'.repeat(310)}  `,
      content: '  keep content spacing  ',
      tagInput: '  API, Review  '
    }))

    const draft = readPromptEditorDraft()
    expect(draft).not.toBeNull()
    expect(draft?.promptId).toHaveLength(80)
    expect(draft?.baseRevision).toBe(9)
    expect(draft?.title).toHaveLength(120)
    expect(draft?.category).toHaveLength(80)
    expect(draft?.description).toHaveLength(300)
    expect(draft?.content).toBe('  keep content spacing  ')
    expect(draft?.tagInput).toBe('  API, Review  ')
  })

  it('rejects malformed or empty browser data and parses reusable tags', () => {
    window.localStorage.setItem('flowforge.promptEditorDraft', '{broken')
    expect(readPromptEditorDraft()).toBeNull()

    window.localStorage.setItem('flowforge.promptEditorDraft', JSON.stringify({ favorite: false }))
    expect(readPromptEditorDraft()).toBeNull()
    expect(parsePromptDraftTags(' API, Review, API, , Backend ')).toEqual([
      'API',
      'Review',
      'API',
      'Backend'
    ])
  })
})
