export type FlowCreationDraft = {
  intent: string
  templateTitle: string
  updatedAt: string
}

const FLOW_CREATION_DRAFT_STORAGE_KEY = 'flowforge.flowCreationDraft'

export function readFlowCreationDraft(): FlowCreationDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(FLOW_CREATION_DRAFT_STORAGE_KEY) || 'null')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }
    const candidate = value as Record<string, unknown>
    const intent = typeof candidate.intent === 'string' ? candidate.intent.slice(0, 2000) : ''
    if (!intent.trim()) {
      return null
    }
    return {
      intent,
      templateTitle: typeof candidate.templateTitle === 'string'
        ? candidate.templateTitle.trim().slice(0, 120)
        : '',
      updatedAt: typeof candidate.updatedAt === 'string' ? candidate.updatedAt : new Date(0).toISOString()
    }
  } catch {
    return null
  }
}

export function persistFlowCreationDraft(draft: FlowCreationDraft | null) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    if (!draft?.intent.trim()) {
      window.localStorage.removeItem(FLOW_CREATION_DRAFT_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(FLOW_CREATION_DRAFT_STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // Keep the active creation input in memory when browser storage is unavailable.
  }
}
