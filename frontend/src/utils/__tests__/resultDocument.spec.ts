import { describe, expect, it } from 'vitest'
import {
  buildResultMarkdown,
  createResultDocumentFilename,
  resolveResultDocument
} from '@/utils/resultDocument'

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

  it('converts a legacy structured API result into readable markdown', () => {
    const resolved = resolveResultDocument(
      JSON.stringify({
        base_url: '/api/v1',
        endpoints: [
          {
            method: 'post',
            path: '/flows',
            description: 'Create a flow',
            request_body: { name: 'Launch', active: true }
          }
        ]
      })
    )

    expect(resolved.markdown).toBe(
      [
        '- **Base URL:** /api/v1',
        '## Endpoints',
        '### POST /flows',
        '- **Description:** Create a flow',
        '#### Request body',
        '- **Name:** Launch\n- **Active:** true'
      ].join('\n\n')
    )
    expect(JSON.parse(resolved.sourceJson || '')).toMatchObject({ base_url: '/api/v1' })
  })

  it('formats nested arrays, empty values and nulls without JSON punctuation', () => {
    const resolved = resolveResultDocument(
      JSON.stringify({
        risks: ['Scope drift', 'Missing owner'],
        groups: [['Design', 'Engineering']],
        empty: [],
        owner: null
      })
    )

    expect(resolved.markdown).toContain('## Risks\n\n- Scope drift\n- Missing owner')
    expect(resolved.markdown).toContain('## Groups\n\n### 条目 1\n\n- Design\n- Engineering')
    expect(resolved.markdown).toContain('## Empty\n\n- 无')
    expect(resolved.markdown).toContain('- **Owner:** 未提供')
  })

  it('leaves plain text and malformed JSON untouched', () => {
    expect(resolveResultDocument('## Result\n\nKeep this markdown')).toEqual({
      markdown: '## Result\n\nKeep this markdown',
      sourceJson: null
    })
    expect(resolveResultDocument('{"result":')).toEqual({
      markdown: '{"result":',
      sourceJson: null
    })
  })
})
