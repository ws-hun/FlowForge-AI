export type PromptEditorDraft = {
  promptId: string | null
  baseRevision: number | null
  title: string
  category: string
  description: string
  content: string
  tagInput: string
  favorite: boolean
  updatedAt: string
}

const PROMPT_EDITOR_DRAFT_STORAGE_KEY = 'flowforge.promptEditorDraft'

export function readPromptEditorDraft(): PromptEditorDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(PROMPT_EDITOR_DRAFT_STORAGE_KEY) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }
    const candidate = value as Record<string, unknown>
    const promptId = typeof candidate.promptId === 'string' && candidate.promptId.trim()
      ? candidate.promptId.trim().slice(0, 80)
      : null
    const baseRevision = typeof candidate.baseRevision === 'number' && candidate.baseRevision >= 0
      ? Math.floor(candidate.baseRevision)
      : null
    const draft: PromptEditorDraft = {
      promptId,
      baseRevision,
      title: readText(candidate.title, 120),
      category: readText(candidate.category, 80),
      description: readText(candidate.description, 300),
      content: readText(candidate.content, 12000, false),
      tagInput: readText(candidate.tagInput, 2000, false),
      favorite: candidate.favorite === true,
      updatedAt: typeof candidate.updatedAt === 'string' ? candidate.updatedAt : new Date(0).toISOString()
    }
    const hasDraftContent = Boolean(
      draft.title || draft.category || draft.description || draft.content || draft.tagInput || draft.favorite
    )
    return promptId || hasDraftContent ? draft : null
  } catch {
    return null
  }
}

export function parsePromptDraftTags(tagInput: string) {
  return tagInput
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
}

export function persistPromptEditorDraft(draft: PromptEditorDraft) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(PROMPT_EDITOR_DRAFT_STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // Keep the active editor state in memory when browser storage is unavailable.
  }
}

export function removePromptEditorDraft() {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.removeItem(PROMPT_EDITOR_DRAFT_STORAGE_KEY)
  } catch {
    // Ignore cleanup failures because no server state is affected.
  }
}

function readText(value: unknown, maxLength: number, trim = true) {
  if (typeof value !== 'string') {
    return ''
  }
  const text = trim ? value.trim() : value
  return text.slice(0, maxLength)
}
