export type PromptRunDraft = {
  variableValues: Record<string, string>
  updatedAt: string
}

export type PromptRunDrafts = Record<string, PromptRunDraft>

const PROMPT_RUN_DRAFTS_STORAGE_KEY = 'flowforge.promptRunDrafts'
const MAX_PROMPT_RUN_DRAFTS = 30
const MAX_PROMPT_RUN_VARIABLES = 50
const MAX_PROMPT_ID_LENGTH = 80
const MAX_VARIABLE_NAME_LENGTH = 120
const MAX_VARIABLE_VALUE_LENGTH = 8000
const FALLBACK_UPDATED_AT = new Date(0).toISOString()

export function readPromptRunDrafts(): PromptRunDrafts {
  if (typeof window === 'undefined') {
    return {}
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(PROMPT_RUN_DRAFTS_STORAGE_KEY) || '{}')
    return normalizePromptRunDrafts(value)
  } catch {
    return {}
  }
}

export function upsertPromptRunDraft(
  drafts: PromptRunDrafts,
  promptId: string,
  variableValues: Record<string, string>,
  updatedAt = new Date().toISOString()
): PromptRunDrafts {
  const cleanPromptId = readText(promptId, MAX_PROMPT_ID_LENGTH)
  if (!cleanPromptId) {
    return drafts
  }

  const cleanVariableValues = sanitizePromptRunDraftVariables(variableValues)
  if (!Object.keys(cleanVariableValues).length) {
    return removePromptRunDraft(drafts, cleanPromptId)
  }

  const currentDraft = drafts[cleanPromptId]
  if (currentDraft && JSON.stringify(currentDraft.variableValues) === JSON.stringify(cleanVariableValues)) {
    return drafts
  }

  return limitPromptRunDrafts({
    ...drafts,
    [cleanPromptId]: {
      variableValues: cleanVariableValues,
      updatedAt: normalizeUpdatedAt(updatedAt)
    }
  })
}

export function removePromptRunDraft(drafts: PromptRunDrafts, promptId: string): PromptRunDrafts {
  if (!drafts[promptId]) {
    return drafts
  }
  const nextDrafts = { ...drafts }
  delete nextDrafts[promptId]
  return nextDrafts
}

export function persistPromptRunDrafts(drafts: PromptRunDrafts) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    if (!Object.keys(drafts).length) {
      window.localStorage.removeItem(PROMPT_RUN_DRAFTS_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(PROMPT_RUN_DRAFTS_STORAGE_KEY, JSON.stringify(drafts))
  } catch {
    // Keep active Prompt preparation in memory when browser storage is unavailable.
  }
}

export function sanitizePromptRunDraftVariables(value: unknown): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  return Object.fromEntries(
    Object.entries(value)
      .flatMap(([name, variableValue]) => {
        const cleanName = readText(name, MAX_VARIABLE_NAME_LENGTH)
        const cleanValue = readText(variableValue, MAX_VARIABLE_VALUE_LENGTH)
        return cleanName && cleanValue ? [[cleanName, cleanValue]] : []
      })
      .slice(0, MAX_PROMPT_RUN_VARIABLES)
  )
}

function normalizePromptRunDrafts(value: unknown): PromptRunDrafts {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  const drafts = Object.fromEntries(
    Object.entries(value).flatMap(([promptId, draft]) => {
      const cleanPromptId = readText(promptId, MAX_PROMPT_ID_LENGTH)
      if (!cleanPromptId || !draft || typeof draft !== 'object' || Array.isArray(draft)) {
        return []
      }

      const candidate = draft as Record<string, unknown>
      const variableValues = sanitizePromptRunDraftVariables(candidate.variableValues)
      if (!Object.keys(variableValues).length) {
        return []
      }

      return [[
        cleanPromptId,
        {
          variableValues,
          updatedAt: normalizeUpdatedAt(candidate.updatedAt)
        }
      ]]
    })
  )

  return limitPromptRunDrafts(drafts)
}

function limitPromptRunDrafts(drafts: PromptRunDrafts): PromptRunDrafts {
  return Object.fromEntries(
    Object.entries(drafts)
      .sort(([, left], [, right]) => right.updatedAt.localeCompare(left.updatedAt))
      .slice(0, MAX_PROMPT_RUN_DRAFTS)
  )
}

function readText(value: unknown, maxLength: number): string {
  return typeof value === 'string' ? value.trim().slice(0, maxLength) : ''
}

function normalizeUpdatedAt(value: unknown): string {
  if (typeof value !== 'string') {
    return FALLBACK_UPDATED_AT
  }
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? FALLBACK_UPDATED_AT : new Date(timestamp).toISOString()
}
