import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { persistActiveFlowId, readActiveFlowId, resolveActiveFlowId } from '../flowSelection'
import { MemoryStorage } from './memoryStorage'

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Flow selection persistence', () => {
  it('round-trips a normalized active Flow identity and clears empty selection', () => {
    persistActiveFlowId('  flow-1  ')
    expect(readActiveFlowId()).toBe('flow-1')

    persistActiveFlowId('   ')
    expect(readActiveFlowId()).toBe('')
    expect(window.localStorage.getItem('flowforge.activeFlowId')).toBeNull()
  })

  it('bounds corrupted Flow identities before restoring them', () => {
    window.localStorage.setItem('flowforge.activeFlowId', `  ${'f'.repeat(120)}  `)
    expect(readActiveFlowId()).toHaveLength(80)
  })

  it('restores an available Flow and repairs stale selection with the first Flow', () => {
    expect(resolveActiveFlowId(['flow-1', 'flow-2'], 'flow-2')).toBe('flow-2')
    expect(resolveActiveFlowId(['flow-1', 'flow-2'], 'deleted-flow')).toBe('flow-1')
    expect(resolveActiveFlowId([], 'flow-1')).toBe('')
  })

  it('does not interrupt the workspace when browser storage is unavailable', () => {
    vi.stubGlobal('window', {
      localStorage: {
        getItem: () => { throw new Error('blocked') },
        setItem: () => { throw new Error('blocked') },
        removeItem: () => { throw new Error('blocked') }
      }
    })

    expect(readActiveFlowId()).toBe('')
    expect(() => persistActiveFlowId('flow-1')).not.toThrow()
  })
})
