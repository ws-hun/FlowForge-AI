const ACTIVE_FLOW_STORAGE_KEY = 'flowforge.activeFlowId'
const MAX_FLOW_ID_LENGTH = 80

export function readActiveFlowId(): string {
  if (typeof window === 'undefined') {
    return ''
  }
  try {
    return normalizeFlowId(window.localStorage.getItem(ACTIVE_FLOW_STORAGE_KEY))
  } catch {
    return ''
  }
}

export function persistActiveFlowId(flowId: string) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    const cleanFlowId = normalizeFlowId(flowId)
    if (!cleanFlowId) {
      window.localStorage.removeItem(ACTIVE_FLOW_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, cleanFlowId)
  } catch {
    // Keep the active Flow in memory when browser storage is unavailable.
  }
}

export function resolveActiveFlowId(flowIds: string[], storedActiveFlowId = readActiveFlowId()) {
  const availableFlowIds = flowIds.map(normalizeFlowId).filter(Boolean)
  return availableFlowIds.includes(storedActiveFlowId) ? storedActiveFlowId : availableFlowIds[0] || ''
}

function normalizeFlowId(value: unknown) {
  return typeof value === 'string' ? value.trim().slice(0, MAX_FLOW_ID_LENGTH) : ''
}
