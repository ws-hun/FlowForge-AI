import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  normalizeWorkspacePreferences,
  persistWorkspacePreferences,
  readWorkspacePreferences
} from '../workspacePreferences'
import { MemoryStorage } from './memoryStorage'

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Workspace preferences', () => {
  it('round-trips normalized workspace identity within the supported limits', () => {
    const preferences = normalizeWorkspacePreferences({
      workspaceName: `  ${'W'.repeat(100)}  `,
      profileName: '  Flow Creator  '
    })

    expect(preferences).toEqual({
      workspaceName: 'W'.repeat(80),
      profileName: 'Flow Creator'
    })
    expect(persistWorkspacePreferences(preferences!)).toBe(true)
    expect(readWorkspacePreferences()).toEqual(preferences)
  })

  it('uses calm defaults for missing or corrupt browser data', () => {
    window.localStorage.setItem('flowforge.workspacePreferences', JSON.stringify({
      workspaceName: 'Product Studio'
    }))
    expect(readWorkspacePreferences()).toEqual({
      workspaceName: 'Product Studio',
      profileName: 'AI 创作者'
    })

    window.localStorage.setItem('flowforge.workspacePreferences', '{broken')
    expect(readWorkspacePreferences()).toEqual({
      workspaceName: 'FlowForge Workspace',
      profileName: 'AI 创作者'
    })
  })

  it('rejects incomplete preference updates', () => {
    expect(normalizeWorkspacePreferences({ workspaceName: 'FlowForge', profileName: ' ' })).toBeNull()
    expect(normalizeWorkspacePreferences(null)).toBeNull()
  })

  it('reports when browser persistence is unavailable', () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: () => { throw new Error('blocked') },
        setItem: () => { throw new Error('blocked') }
      }
    })

    expect(readWorkspacePreferences()).toEqual({
      workspaceName: 'FlowForge Workspace',
      profileName: 'AI 创作者'
    })
    expect(persistWorkspacePreferences({
      workspaceName: 'Product Studio',
      profileName: 'Flow Creator'
    })).toBe(false)
  })
})
