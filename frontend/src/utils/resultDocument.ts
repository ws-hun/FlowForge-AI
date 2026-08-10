const FALLBACK_FILENAME = 'flowforge-result'

export function buildResultMarkdown(summary: string, result: string) {
  const normalizedSummary = summary.trim()
  const normalizedResult = result.trim()

  return [
    normalizedSummary ? '# ' + normalizedSummary : '',
    normalizedResult
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
