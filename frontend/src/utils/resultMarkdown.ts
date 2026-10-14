const CODE_FENCE = String.fromCharCode(96).repeat(3)

export type ResultBlock =
  | { type: 'heading'; level: number; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'table'; headers: string[]; alignments: ('left' | 'center' | 'right')[]; rows: string[][] }
  | { type: 'code'; language: string; content: string }
  | { type: 'quote'; content: string }
  | { type: 'divider' }

export type ResultInlineSegment = {
  type: 'text' | 'strong' | 'code' | 'emphasis' | 'link'
  content: string
  href?: string
}

export function parseResultMarkdown(text: string): ResultBlock[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ResultBlock[] = []
  let paragraph: string[] = []
  let listItems: string[] = []
  let quoteLines: string[] = []
  let orderedList = false
  let codeLines: string[] = []
  let codeLanguage = ''
  let inCode = false

  const flushParagraph = () => {
    if (!paragraph.length) return
    blocks.push({ type: 'paragraph', content: paragraph.join(' ').trim() })
    paragraph = []
  }

  const flushList = () => {
    if (!listItems.length) return
    blocks.push({ type: 'list', ordered: orderedList, items: listItems.map((item) => item.trim()).filter(Boolean) })
    listItems = []
    orderedList = false
  }

  const flushQuote = () => {
    if (!quoteLines.length) return
    blocks.push({ type: 'quote', content: quoteLines.join(' ').trim() })
    quoteLines = []
  }

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index]
    const trimmed = rawLine.trim()

    if (trimmed.startsWith(CODE_FENCE)) {
      if (inCode) {
        blocks.push({ type: 'code', language: codeLanguage, content: codeLines.join('\n').trim() })
        codeLines = []
        codeLanguage = ''
        inCode = false
      } else {
        flushParagraph()
        flushList()
        flushQuote()
        codeLanguage = trimmed.slice(CODE_FENCE.length).trim()
        inCode = true
      }
      continue
    }

    if (inCode) {
      codeLines.push(rawLine)
      continue
    }

    const headers = parseTableRow(trimmed)
    const delimiter = parseTableRow(lines[index + 1]?.trim() || '')
    if (headers && delimiter && headers.length === delimiter.length
      && delimiter.every((cell) => /^:?-{3,}:?$/.test(cell))) {
      flushParagraph()
      flushList()
      flushQuote()
      const alignments = delimiter.map((cell) => {
        const left = cell.startsWith(':')
        const right = cell.endsWith(':')
        return left && right ? 'center' : right ? 'right' : 'left'
      })
      const rows: string[][] = []
      index += 2
      while (index < lines.length) {
        const row = parseTableRow(lines[index].trim())
        if (!row || row.length !== headers.length) break
        rows.push(row)
        index += 1
      }
      index -= 1
      blocks.push({ type: 'table', headers, alignments, rows })
      continue
    }

    if (!trimmed) {
      flushParagraph()
      flushList()
      flushQuote()
      continue
    }

    const quote = trimmed.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      flushList()
      quoteLines.push(quote[1].trim())
      continue
    }
    flushQuote()

    const heading = trimmed.match(/^(#{1,4})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      blocks.push({ type: 'heading', level: heading[1].length, content: heading[2].trim() })
      continue
    }

    if (/^(?:-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      flushParagraph()
      flushList()
      blocks.push({ type: 'divider' })
      continue
    }

    const bullet = trimmed.match(/^[-*•]\s+(.+)$/)
    const numbered = trimmed.match(/^\d+[.)、]\s+(.+)$/)
    if (bullet || numbered) {
      flushParagraph()
      const isOrdered = Boolean(numbered)
      if (listItems.length && orderedList !== isOrdered) {
        flushList()
      }
      orderedList = isOrdered
      listItems.push((bullet?.[1] || numbered?.[1] || '').trim())
      continue
    }

    flushList()
    paragraph.push(trimmed)
  }

  if (inCode) {
    blocks.push({ type: 'code', language: codeLanguage, content: codeLines.join('\n').trim() })
  }
  flushParagraph()
  flushList()
  flushQuote()

  return blocks.length ? blocks : [{ type: 'paragraph', content: text }]
}

function parseTableRow(line: string): string[] | null {
  if (!line.includes('|')) return null
  const cells: string[] = []
  let cell = ''
  let inCode = false

  for (let index = 0; index < line.length; index += 1) {
    const character = line[index]
    if (character === '\\' && line[index + 1] === '|') {
      cell += '|'
      index += 1
    } else if (character === '`') {
      inCode = !inCode
      cell += character
    } else if (character === '|' && !inCode) {
      cells.push(cell.trim())
      cell = ''
    } else {
      cell += character
    }
  }
  cells.push(cell.trim())
  if (line.trimStart().startsWith('|')) cells.shift()
  if (line.trimEnd().endsWith('|')) cells.pop()
  return cells.length > 1 ? cells : null
}

export function parseInlineMarkdown(text: string): ResultInlineSegment[] {
  const pattern = /(\[[^\]]+\]\((?:https?:\/\/|mailto:)[^)\s]+\)|\*\*[^*]+\*\*|\x60[^\x60]+\x60|\*[^*]+\*)/gi
  const segments: ResultInlineSegment[] = []
  let cursor = 0

  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > cursor) {
      segments.push({ type: 'text', content: text.slice(cursor, index) })
    }

    const token = match[0]
    if (token.startsWith('[')) {
      const labelEnd = token.indexOf('](')
      const href = token.slice(labelEnd + 2, -1)
      if (isSafeMarkdownLink(href)) {
        segments.push({ type: 'link', content: token.slice(1, labelEnd), href })
      } else {
        segments.push({ type: 'text', content: token })
      }
    } else if (token.startsWith('**')) {
      segments.push({ type: 'strong', content: token.slice(2, -2) })
    } else if (token.startsWith(String.fromCharCode(96))) {
      segments.push({ type: 'code', content: token.slice(1, -1) })
    } else {
      segments.push({ type: 'emphasis', content: token.slice(1, -1) })
    }
    cursor = index + token.length
  }

  if (cursor < text.length) {
    segments.push({ type: 'text', content: text.slice(cursor) })
  }
  return segments.length ? segments : [{ type: 'text', content: text }]
}

function isSafeMarkdownLink(href: string) {
  try {
    const url = new URL(href)
    return ['http:', 'https:', 'mailto:'].includes(url.protocol)
      && !url.username
      && !url.password
      && (url.protocol === 'mailto:' || Boolean(url.hostname))
  } catch {
    return false
  }
}

export function cleanResultInlineText(text: string) {
  const normalized = text
    .replace(/^#+\s*/, '')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (token, label: string, href: string) =>
      isSafeMarkdownLink(href) ? label : token
    )
    .trim()
  return parseInlineMarkdown(normalized)
    .map((segment) => segment.content)
    .join('')
    .trim()
}
