const CODE_FENCE = String.fromCharCode(96).repeat(3)

export type ResultBlock =
  | { type: 'heading'; level: number; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; language: string; content: string }
  | { type: 'quote'; content: string }
  | { type: 'divider' }

export type ResultInlineSegment = {
  type: 'text' | 'strong' | 'code' | 'emphasis'
  content: string
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

  for (const rawLine of lines) {
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

export function parseInlineMarkdown(text: string): ResultInlineSegment[] {
  const pattern = /(\*\*[^*]+\*\*|\x60[^\x60]+\x60|\*[^*]+\*)/g
  const segments: ResultInlineSegment[] = []
  let cursor = 0

  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > cursor) {
      segments.push({ type: 'text', content: text.slice(cursor, index) })
    }

    const token = match[0]
    if (token.startsWith('**')) {
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

export function cleanResultInlineText(text: string) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/\x60([^\x60]+)\x60/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/^#+\s*/, '')
    .trim()
}
