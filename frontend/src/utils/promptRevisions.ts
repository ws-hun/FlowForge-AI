import type { PromptAsset, PromptVersion } from '@/types'
import { extractPromptVariables } from '@/utils/promptVariables'

type PromptRevisionSource = Pick<
  PromptAsset,
  'title' | 'category' | 'description' | 'content' | 'tags' | 'favorite'
>

export type PromptRevisionChange = {
  key: string
  kind: 'update' | 'restore' | 'remove'
  label: string
  detail: string
}

export type PromptRevisionDiff = {
  changes: PromptRevisionChange[]
  changeCount: number
  hasChanges: boolean
}

export function comparePromptRevision(
  current: PromptRevisionSource,
  revision: PromptVersion
): PromptRevisionDiff {
  const changes: PromptRevisionChange[] = []

  if (current.title !== revision.title) {
    changes.push({
      key: 'title',
      kind: 'update',
      label: '名称',
      detail: `「${compact(current.title)}」→「${compact(revision.title)}」`
    })
  }
  if (current.category !== revision.category) {
    changes.push({
      key: 'category',
      kind: 'update',
      label: '分类',
      detail: `${current.category} → ${revision.category}`
    })
  }
  if (current.description !== revision.description) {
    changes.push({
      key: 'description',
      kind: 'update',
      label: '用途说明',
      detail: '会恢复该版本保存时的用途与适用场景说明'
    })
  }
  if (current.content !== revision.content) {
    changes.push({
      key: 'content',
      kind: 'update',
      label: 'Prompt 正文',
      detail: `${textMetrics(current.content)} → ${textMetrics(revision.content)}`
    })
  }
  if (current.favorite !== revision.favorite) {
    changes.push({
      key: 'favorite',
      kind: 'update',
      label: '收藏状态',
      detail: revision.favorite ? '会恢复为已收藏' : '会恢复为未收藏'
    })
  }

  appendSetChanges(changes, 'tags', '标签', current.tags, revision.tags, '#')
  appendSetChanges(
    changes,
    'variables',
    '变量',
    extractPromptVariables(current.content),
    extractPromptVariables(revision.content),
    '{',
    '}'
  )

  return {
    changes,
    changeCount: changes.length,
    hasChanges: changes.length > 0
  }
}

function appendSetChanges(
  changes: PromptRevisionChange[],
  key: string,
  label: string,
  currentValues: string[],
  revisionValues: string[],
  prefix: string,
  suffix = ''
) {
  const currentSet = new Set(currentValues)
  const revisionSet = new Set(revisionValues)
  const restoredValues = revisionValues.filter((value) => !currentSet.has(value))
  const removedValues = currentValues.filter((value) => !revisionSet.has(value))

  if (restoredValues.length) {
    changes.push({
      key: `${key}-restore`,
      kind: 'restore',
      label,
      detail: `会重新加入 ${formatValues(restoredValues, prefix, suffix)}`
    })
  }
  if (removedValues.length) {
    changes.push({
      key: `${key}-remove`,
      kind: 'remove',
      label,
      detail: `会移除较新的 ${formatValues(removedValues, prefix, suffix)}`
    })
  }
}

function formatValues(values: string[], prefix: string, suffix: string) {
  return values.map((value) => `${prefix}${value}${suffix}`).join('、')
}

function textMetrics(value: string) {
  const lineCount = value ? value.split(/\r?\n/).length : 0
  return `${value.length} 字符 / ${lineCount} 行`
}

function compact(value: string) {
  const cleanValue = value.trim()
  return cleanValue.length > 32 ? `${cleanValue.slice(0, 32)}...` : cleanValue
}
