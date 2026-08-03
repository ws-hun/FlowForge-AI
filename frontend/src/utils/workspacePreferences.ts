export type WorkspacePreferences = {
  workspaceName: string
  profileName: string
}

const WORKSPACE_PREFERENCES_STORAGE_KEY = 'flowforge.workspacePreferences'
const MAX_PREFERENCE_LENGTH = 80
const DEFAULT_WORKSPACE_NAME = 'FlowForge Workspace'
const DEFAULT_PROFILE_NAME = 'AI 创作者'

export function readWorkspacePreferences(): WorkspacePreferences {
  const defaults = createDefaultWorkspacePreferences()
  if (typeof window === 'undefined') {
    return defaults
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(WORKSPACE_PREFERENCES_STORAGE_KEY) || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return defaults
    }
    const candidate = value as Record<string, unknown>
    return {
      workspaceName: readPreference(candidate.workspaceName) || defaults.workspaceName,
      profileName: readPreference(candidate.profileName) || defaults.profileName
    }
  } catch {
    return defaults
  }
}

export function normalizeWorkspacePreferences(value: unknown): WorkspacePreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  const candidate = value as Record<string, unknown>
  const workspaceName = readPreference(candidate.workspaceName)
  const profileName = readPreference(candidate.profileName)
  return workspaceName && profileName ? { workspaceName, profileName } : null
}

export function persistWorkspacePreferences(preferences: WorkspacePreferences) {
  if (typeof window === 'undefined') {
    return false
  }
  const normalizedPreferences = normalizeWorkspacePreferences(preferences)
  if (!normalizedPreferences) {
    return false
  }
  try {
    window.localStorage.setItem(WORKSPACE_PREFERENCES_STORAGE_KEY, JSON.stringify(normalizedPreferences))
    return true
  } catch {
    return false
  }
}

function createDefaultWorkspacePreferences(): WorkspacePreferences {
  return {
    workspaceName: DEFAULT_WORKSPACE_NAME,
    profileName: DEFAULT_PROFILE_NAME
  }
}

function readPreference(value: unknown) {
  return typeof value === 'string' ? value.trim().slice(0, MAX_PREFERENCE_LENGTH) : ''
}
