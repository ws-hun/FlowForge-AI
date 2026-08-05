import { describe, expect, it } from 'vitest'
import type { PromptAsset, PromptVersion } from '@/types'
import { buildPromptEditorPreview } from '../promptEditorDraft'
import { comparePromptRevision } from '../promptRevisions'

function prompt(overrides: Partial<PromptAsset> = {}): PromptAsset {
  return {
    id: 'prompt-1',
    title: 'Launch Brief',
    category: 'Product',
    description: 'Prepare a launch brief',
    content: 'Write a launch brief for {audience}.',
    tags: ['launch', 'brief'],
    favorite: false,
    revision: 3,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-03T00:00:00.000Z',
    ...overrides
  }
}

function version(overrides: Partial<PromptVersion> = {}): PromptVersion {
  return {
    id: 'version-1',
    promptId: 'prompt-1',
    versionNumber: 1,
    title: 'Launch Brief',
    category: 'Product',
    description: 'Prepare a launch brief',
    content: 'Write a launch brief for {audience}.',
    tags: ['launch', 'brief'],
    favorite: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    ...overrides
  }
}

describe('Prompt revision comparison', () => {
  it('compares a revision with trimmed pending editor content', () => {
    const current = buildPromptEditorPreview(prompt(), {
      title: '  Updated Brief  ',
      category: '  Strategy  ',
      description: '  Prepare and review a launch brief  ',
      content: '  Build a launch plan for {market}.  ',
      tagInput: ' strategy, launch ',
      favorite: true
    })

    const diff = comparePromptRevision(current, version())

    expect(current).toEqual({
      title: 'Updated Brief',
      category: 'Strategy',
      description: 'Prepare and review a launch brief',
      content: 'Build a launch plan for {market}.',
      tags: ['strategy', 'launch'],
      favorite: true
    })
    expect(diff.changes.map((change) => change.key)).toEqual([
      'title',
      'category',
      'description',
      'content',
      'favorite',
      'tags-restore',
      'tags-remove',
      'variables-restore',
      'variables-remove'
    ])
  })

  it('reports tag and favorite changes independently', () => {
    const diff = comparePromptRevision(
      prompt({ tags: ['launch', 'review'], favorite: true }),
      version()
    )

    expect(diff.changes).toEqual([
      {
        key: 'favorite',
        kind: 'update',
        label: '收藏状态',
        detail: '会恢复为未收藏'
      },
      {
        key: 'tags-restore',
        kind: 'restore',
        label: '标签',
        detail: '会重新加入 #brief'
      },
      {
        key: 'tags-remove',
        kind: 'remove',
        label: '标签',
        detail: '会移除较新的 #review'
      }
    ])
  })

  it('reports variables introduced and removed by content changes', () => {
    const diff = comparePromptRevision(
      prompt({ content: 'Write for {market} in {tone}.' }),
      version({ content: 'Write for {audience} in {tone}.' })
    )

    expect(diff.changes.map((change) => change.key)).toEqual([
      'content',
      'variables-restore',
      'variables-remove'
    ])
    expect(diff.changes[1].detail).toBe('会重新加入 {audience}')
    expect(diff.changes[2].detail).toBe('会移除较新的 {market}')
  })

  it('returns no changes for an identical visible editor state', () => {
    const current = buildPromptEditorPreview(prompt(), null)

    expect(comparePromptRevision(current, version())).toEqual({
      changes: [],
      changeCount: 0,
      hasChanges: false
    })
  })
})
