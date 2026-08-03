export type AiCommandDraft = {
  input: string
  sourcePromptId: string | null
  sourcePromptTitle: string
  sourceFlowId: string | null
  sourceFlowTitle: string
  sourceFlowVariableValues: Record<string, string>
  sourceRunId: string | null
  sourceRunSummary: string
  inputVariantOfTaskId: string | null
  inputVariantSourceTitle: string
  updatedAt: string
}

const AI_COMMAND_DRAFT_STORAGE_KEY = 'flowforge.aiCommandDraft'

export function readAiCommandDraft(): AiCommandDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(AI_COMMAND_DRAFT_STORAGE_KEY) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }

    const candidate = value as Record<string, unknown>
    const input = typeof candidate.input === 'string' ? candidate.input.slice(0, 50000) : ''
    const sourcePromptId = readId(candidate.sourcePromptId)
    const sourceFlowId = readId(candidate.sourceFlowId)
    const sourceRunId = readId(candidate.sourceRunId)
    const inputVariantOfTaskId = readId(candidate.inputVariantOfTaskId)
    const sourceCount = [sourcePromptId, sourceFlowId, sourceRunId, inputVariantOfTaskId].filter(Boolean).length
    if (sourceCount > 1) {
      return input.trim() ? standaloneDraft(input) : null
    }
    if (!input.trim() && sourceCount === 0) {
      return null
    }

    return {
      input,
      sourcePromptId,
      sourcePromptTitle: sourcePromptId ? readLabel(candidate.sourcePromptTitle) : '',
      sourceFlowId,
      sourceFlowTitle: sourceFlowId ? readLabel(candidate.sourceFlowTitle) : '',
      sourceFlowVariableValues: sourceFlowId ? readVariableValues(candidate.sourceFlowVariableValues) : {},
      sourceRunId,
      sourceRunSummary: sourceRunId ? readLabel(candidate.sourceRunSummary, 500) : '',
      inputVariantOfTaskId,
      inputVariantSourceTitle: inputVariantOfTaskId ? readLabel(candidate.inputVariantSourceTitle) : '',
      updatedAt: typeof candidate.updatedAt === 'string' ? candidate.updatedAt : new Date(0).toISOString()
    }
  } catch {
    return null
  }
}

export function persistAiCommandDraft(draft: AiCommandDraft | null) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    if (!draft) {
      window.localStorage.removeItem(AI_COMMAND_DRAFT_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(AI_COMMAND_DRAFT_STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // Keep the active command in memory when browser storage is unavailable.
  }
}

function standaloneDraft(input: string): AiCommandDraft {
  return {
    input,
    sourcePromptId: null,
    sourcePromptTitle: '',
    sourceFlowId: null,
    sourceFlowTitle: '',
    sourceFlowVariableValues: {},
    sourceRunId: null,
    sourceRunSummary: '',
    inputVariantOfTaskId: null,
    inputVariantSourceTitle: '',
    updatedAt: new Date().toISOString()
  }
}

function readId(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function readLabel(value: unknown, maxLength = 200) {
  return typeof value === 'string' ? value.trim().slice(0, maxLength) : ''
}

function readVariableValues(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(value)
      .filter(([name, variableValue]) => name.trim() && typeof variableValue === 'string')
      .slice(0, 50)
      .map(([name, variableValue]) => [name.trim().slice(0, 120), (variableValue as string).slice(0, 8000)])
  )
}
