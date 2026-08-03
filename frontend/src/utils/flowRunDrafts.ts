export type FlowRunDraft = {
  runtimeContext: string
  variableValues: Record<string, string>
  updatedAt: string
}

export type FlowRunDrafts = Record<string, FlowRunDraft>

const FLOW_RUN_DRAFTS_STORAGE_KEY = 'flowforge.flowRunDrafts'
const MAX_FLOW_RUN_DRAFTS = 20
const MAX_FLOW_RUN_VARIABLES = 50
const MAX_FLOW_ID_LENGTH = 80
const MAX_RUNTIME_CONTEXT_LENGTH = 50000
const MAX_VARIABLE_NAME_LENGTH = 120
const MAX_VARIABLE_VALUE_LENGTH = 8000
const FALLBACK_UPDATED_AT = new Date(0).toISOString()

export function readFlowRunDrafts(): FlowRunDrafts {
  if (typeof window === 'undefined') {
    return {}
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(FLOW_RUN_DRAFTS_STORAGE_KEY) || '{}')
    return normalizeFlowRunDrafts(value)
  } catch {
    return {}
  }
}

export function upsertFlowRunDraft(
  drafts: FlowRunDrafts,
  flowId: string,
  runtimeContext: string,
  variableValues: Record<string, string>,
  updatedAt = new Date().toISOString()
): FlowRunDrafts {
  const cleanFlowId = readText(flowId, MAX_FLOW_ID_LENGTH)
  if (!cleanFlowId) {
    return drafts
  }

  const cleanContext = readText(runtimeContext, MAX_RUNTIME_CONTEXT_LENGTH)
  const cleanVariableValues = sanitizeFlowRunDraftVariables(variableValues)
  if (!cleanContext && !Object.keys(cleanVariableValues).length) {
    return removeFlowRunDraft(drafts, cleanFlowId)
  }

  const currentDraft = drafts[cleanFlowId]
  if (
    currentDraft?.runtimeContext === cleanContext &&
    JSON.stringify(currentDraft.variableValues) === JSON.stringify(cleanVariableValues)
  ) {
    return drafts
  }

  return limitFlowRunDrafts({
    ...drafts,
    [cleanFlowId]: {
      runtimeContext: cleanContext,
      variableValues: cleanVariableValues,
      updatedAt: normalizeUpdatedAt(updatedAt)
    }
  })
}

export function removeFlowRunDraft(drafts: FlowRunDrafts, flowId: string): FlowRunDrafts {
  if (!drafts[flowId]) {
    return drafts
  }
  const nextDrafts = { ...drafts }
  delete nextDrafts[flowId]
  return nextDrafts
}

export function persistFlowRunDrafts(drafts: FlowRunDrafts) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    if (!Object.keys(drafts).length) {
      window.localStorage.removeItem(FLOW_RUN_DRAFTS_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(FLOW_RUN_DRAFTS_STORAGE_KEY, JSON.stringify(drafts))
  } catch {
    // Keep the active run brief in memory when browser storage is unavailable.
  }
}

export function sanitizeFlowRunDraftVariables(value: unknown): Record<string, string> {
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
      .slice(0, MAX_FLOW_RUN_VARIABLES)
  )
}

function normalizeFlowRunDrafts(value: unknown): FlowRunDrafts {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  const drafts = Object.fromEntries(
    Object.entries(value).flatMap(([flowId, draft]) => {
      const cleanFlowId = readText(flowId, MAX_FLOW_ID_LENGTH)
      if (!cleanFlowId || !draft || typeof draft !== 'object' || Array.isArray(draft)) {
        return []
      }

      const candidate = draft as Record<string, unknown>
      const runtimeContext = readText(candidate.runtimeContext, MAX_RUNTIME_CONTEXT_LENGTH)
      const variableValues = sanitizeFlowRunDraftVariables(candidate.variableValues)
      if (!runtimeContext && !Object.keys(variableValues).length) {
        return []
      }

      return [[
        cleanFlowId,
        {
          runtimeContext,
          variableValues,
          updatedAt: normalizeUpdatedAt(candidate.updatedAt)
        }
      ]]
    })
  )

  return limitFlowRunDrafts(drafts)
}

function limitFlowRunDrafts(drafts: FlowRunDrafts): FlowRunDrafts {
  return Object.fromEntries(
    Object.entries(drafts)
      .sort(([, left], [, right]) => right.updatedAt.localeCompare(left.updatedAt))
      .slice(0, MAX_FLOW_RUN_DRAFTS)
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
