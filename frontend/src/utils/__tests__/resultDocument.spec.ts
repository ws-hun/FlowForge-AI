import { describe, expect, it } from 'vitest'
import { buildResultMarkdown, createResultDocumentFilename } from '@/utils/resultDocument'

describe('resultDocument', () => {
  it('builds a portable markdown document from the summary and result', () => {
    expect(buildResultMarkdown(' Launch plan ', '## Actions\n\n- Ship the beta ')).toBe(
      '# Launch plan\n\n## Actions\n\n- Ship the beta'
    )
  })

  it('does not add an empty heading when the summary is unavailable', () => {
    expect(buildResultMarkdown('  ', 'Detailed result')).toBe('Detailed result')
  })

  it('creates a filesystem-safe markdown filename without losing readable text', () => {
    expect(createResultDocumentFilename('API / Workflow: launch?')).toBe('API Workflow launch.md')
    expect(createResultDocumentFilename('产品发布计划')).toBe('产品发布计划.md')
  })

  it('uses a stable fallback for an unusable summary', () => {
    expect(createResultDocumentFilename(' /:*? ')).toBe('flowforge-result.md')
  })

  it('removes a trailing period introduced by filename truncation', () => {
    expect(createResultDocumentFilename('A'.repeat(63) + '.rest')).toBe('A'.repeat(63) + '.md')
  })
})
