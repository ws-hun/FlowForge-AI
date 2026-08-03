import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  persistFlowRunDrafts,
  readFlowRunDrafts,
  removeFlowRunDraft,
  sanitizeFlowRunDraftVariables,
  upsertFlowRunDraft,
  type FlowRunDrafts
} from '../flowRunDrafts'
import { MemoryStorage } from './memoryStorage'

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Flow run drafts', () => {
  it('round-trips independent runtime context and variables for each Flow', () => {
    let drafts: FlowRunDrafts = {}
    drafts = upsertFlowRunDraft(
      drafts,
      'flow-1',
      '  Focus on the first release.  ',
      { ' audience ': ' product teams ' },
      '2026-01-01T00:00:00.000Z'
    )
    drafts = upsertFlowRunDraft(
      drafts,
      'flow-2',
      'Compare two alternatives.',
      { format: 'decision memo' },
      '2026-01-02T00:00:00.000Z'
    )
    persistFlowRunDrafts(drafts)

    expect(readFlowRunDrafts()).toEqual({
      'flow-2': {
        runtimeContext: 'Compare two alternatives.',
        variableValues: { format: 'decision memo' },
        updatedAt: '2026-01-02T00:00:00.000Z'
      },
      'flow-1': {
        runtimeContext: 'Focus on the first release.',
        variableValues: { audience: 'product teams' },
        updatedAt: '2026-01-01T00:00:00.000Z'
      }
    })
  })

  it('removes a Flow draft when its execution context becomes empty', () => {
    const drafts = upsertFlowRunDraft(
      {},
      'flow-1',
      'Keep this context',
      { audience: 'teams' },
      '2026-01-01T00:00:00.000Z'
    )
    const clearedDrafts = upsertFlowRunDraft(drafts, 'flow-1', '   ', { audience: '   ' })

    expect(clearedDrafts).toEqual({})
    expect(removeFlowRunDraft(clearedDrafts, 'missing-flow')).toBe(clearedDrafts)

    persistFlowRunDrafts(clearedDrafts)
    expect(window.localStorage.getItem('flowforge.flowRunDrafts')).toBeNull()
  })

  it('ignores corrupt and structurally invalid browser data', () => {
    window.localStorage.setItem('flowforge.flowRunDrafts', '{broken')
    expect(readFlowRunDrafts()).toEqual({})

    window.localStorage.setItem('flowforge.flowRunDrafts', JSON.stringify({
      empty: { runtimeContext: ' ', variableValues: { audience: ' ' } },
      invalid: ['not', 'a', 'draft'],
      valid: {
        runtimeContext: 'Recover this',
        variableValues: null,
        updatedAt: 'not-a-date'
      }
    }))
    expect(readFlowRunDrafts()).toEqual({
      valid: {
        runtimeContext: 'Recover this',
        variableValues: {},
        updatedAt: '1970-01-01T00:00:00.000Z'
      }
    })
  })

  it('limits variable count, names, and values before persistence', () => {
    const variableValues = Object.fromEntries([
      [` ${'n'.repeat(140)} `, ` ${'v'.repeat(9000)} `],
      ...Array.from({ length: 60 }, (_, index) => [`variable-${index}`, `value-${index}`])
    ])
    const sanitized = sanitizeFlowRunDraftVariables(variableValues)
    const entries = Object.entries(sanitized)

    expect(entries).toHaveLength(50)
    expect(entries[0][0]).toHaveLength(120)
    expect(entries[0][1]).toHaveLength(8000)
    expect(entries[entries.length - 1]).toEqual(['variable-48', 'value-48'])
  })

  it('retains the 20 newest per-Flow drafts in newest-first order', () => {
    let drafts: FlowRunDrafts = {}
    for (let index = 0; index < 25; index += 1) {
      drafts = upsertFlowRunDraft(
        drafts,
        `flow-${index}`,
        `Context ${index}`,
        {},
        new Date(Date.UTC(2026, 0, index + 1)).toISOString()
      )
    }

    expect(Object.keys(drafts)).toHaveLength(20)
    expect(Object.keys(drafts)[0]).toBe('flow-24')
    const flowIds = Object.keys(drafts)
    expect(flowIds[flowIds.length - 1]).toBe('flow-5')
  })
})
