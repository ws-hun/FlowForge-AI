const FALLBACK_FILENAME = 'flowforge-result'
const UPPERCASE_WORDS = new Set(['api', 'url', 'id', 'json', 'http', 'https'])

export interface ResolvedResultDocument {
  markdown: string
  sourceJson: string | null
}

export function buildResultMarkdown(summary: string, result: string, provenance: string[] = []) {
  const normalizedSummary = summary.trim()
  const normalizedResult = result.trim()
  const normalizedProvenance = provenance.map((item) => item.trim()).filter(Boolean)

  return [
    normalizedSummary ? '# ' + normalizedSummary : '',
    normalizedResult,
    normalizedProvenance.length
      ? '---\n\n> FlowForge AI · ' + normalizedProvenance.join(' · ')
      : ''
  ]
    .filter(Boolean)
    .join('\n\n')
}

export function createResultDocumentFilename(summary: string) {
  const safeName = summary
    .normalize('NFKC')
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 64)
    .trim()
    .replace(/[.\s]+$/g, '')

  return (safeName || FALLBACK_FILENAME) + '.md'
}

export function resolveResultDocument(result: string): ResolvedResultDocument {
  const source = result.trim()
  if (!source || (!source.startsWith('{') && !source.startsWith('['))) {
    return { markdown: result, sourceJson: null }
  }

  try {
    const parsed: unknown = JSON.parse(source)
    if (!isRecord(parsed) && !Array.isArray(parsed)) {
      return { markdown: result, sourceJson: null }
    }
    return {
      markdown: formatStructuredValue(parsed, 2),
      sourceJson: JSON.stringify(parsed, null, 2)
    }
  } catch {
    return { markdown: result, sourceJson: null }
  }
}

function formatStructuredValue(value: unknown, headingLevel: number): string {
  if (Array.isArray(value)) {
    return formatArray(value, headingLevel)
  }
  if (isRecord(value)) {
    return formatObject(value, headingLevel)
  }
  return scalarText(value)
}

function formatObject(
  value: Record<string, unknown>,
  headingLevel: number,
  omittedKeys: ReadonlySet<string> = new Set()
): string {
  const entries = Object.entries(value).filter(([key]) => !omittedKeys.has(key))
  const blocks: string[] = []
  const scalarFields = entries.filter(([, fieldValue]) => !isContainer(fieldValue) && !isMultilineText(fieldValue))

  if (scalarFields.length) {
    blocks.push(
      scalarFields
        .map(([key, fieldValue]) => '- **' + humanizeKey(key) + ':** ' + scalarText(fieldValue))
        .join('\n')
    )
  }

  entries
    .filter(([, fieldValue]) => isContainer(fieldValue) || isMultilineText(fieldValue))
    .forEach(([key, fieldValue]) => {
      blocks.push(heading(humanizeKey(key), headingLevel))
      blocks.push(
        isMultilineText(fieldValue)
          ? fieldValue.trim()
          : formatStructuredValue(fieldValue, nextHeadingLevel(headingLevel))
      )
    })

  return blocks.length ? blocks.join('\n\n') : '- 无'
}

function formatArray(values: unknown[], headingLevel: number): string {
  if (!values.length) {
    return '- 无'
  }

  const blocks: string[] = []
  let scalarItems: string[] = []

  const flushScalarItems = () => {
    if (!scalarItems.length) return
    blocks.push(scalarItems.join('\n'))
    scalarItems = []
  }

  values.forEach((item, index) => {
    if (isRecord(item)) {
      flushScalarItems()
      const itemHeading = resolveItemHeading(item, index)
      blocks.push(heading(itemHeading.text, headingLevel))
      blocks.push(formatObject(item, nextHeadingLevel(headingLevel), itemHeading.omittedKeys))
      return
    }
    if (Array.isArray(item)) {
      flushScalarItems()
      blocks.push(heading('条目 ' + (index + 1), headingLevel))
      blocks.push(formatArray(item, nextHeadingLevel(headingLevel)))
      return
    }
    scalarItems.push('- ' + scalarText(item))
  })
  flushScalarItems()

  return blocks.join('\n\n')
}

function resolveItemHeading(value: Record<string, unknown>, index: number) {
  const method = textValue(value.method)
  const path = textValue(value.path)
  if (method && path) {
    return {
      text: method.toUpperCase() + ' ' + path,
      omittedKeys: new Set(['method', 'path'])
    }
  }

  for (const key of ['title', 'name', 'id']) {
    const itemTitle = textValue(value[key])
    if (itemTitle) {
      return { text: itemTitle, omittedKeys: new Set([key]) }
    }
  }
  return { text: '条目 ' + (index + 1), omittedKeys: new Set<string>() }
}

function humanizeKey(key: string) {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)

  if (!words.length) {
    return '字段'
  }

  return words
    .map((word, index) => {
      const lower = word.toLowerCase()
      if (UPPERCASE_WORDS.has(lower)) return lower.toUpperCase()
      if (index === 0) return word.charAt(0).toUpperCase() + word.slice(1)
      return word
    })
    .join(' ')
}

function scalarText(value: unknown) {
  if (value == null) return '未提供'
  if (typeof value === 'string') return value.trim() || '未提供'
  return String(value)
}

function textValue(value: unknown) {
  if (value == null || isContainer(value)) return ''
  return String(value).trim()
}

function isContainer(value: unknown): value is Record<string, unknown> | unknown[] {
  return Array.isArray(value) || isRecord(value)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isMultilineText(value: unknown): value is string {
  return typeof value === 'string' && value.includes('\n')
}

function heading(text: string, level: number) {
  return '#'.repeat(Math.max(2, Math.min(level, 4))) + ' ' + text
}

function nextHeadingLevel(level: number) {
  return Math.min(level + 1, 4)
}
