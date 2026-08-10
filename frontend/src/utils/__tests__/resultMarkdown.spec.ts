import { describe, expect, it } from 'vitest'
import {
  cleanResultInlineText,
  parseInlineMarkdown,
  parseResultMarkdown
} from '@/utils/resultMarkdown'

describe('resultMarkdown', () => {
  const tick = String.fromCharCode(96)

  it('parses headings, paragraphs and adjacent list items into stable blocks', () => {
    const source = '## Plan\n\nIntro text\n\n- **Owner:** Platform\n- Use ' + tick + 'v1' + tick + '\n\n1. Draft\n2. Review'
    expect(parseResultMarkdown(source)).toEqual([
      { type: 'heading', level: 2, content: 'Plan' },
      { type: 'paragraph', content: 'Intro text' },
      { type: 'list', ordered: false, items: ['**Owner:** Platform', 'Use ' + tick + 'v1' + tick] },
      { type: 'list', ordered: true, items: ['Draft', 'Review'] }
    ])
  })

  it('preserves fenced code language and content', () => {
    const fence = tick.repeat(3)
    expect(parseResultMarkdown(fence + 'json\n{"ready":true}\n' + fence)).toEqual([
      { type: 'code', language: 'json', content: '{"ready":true}' }
    ])
  })

  it('groups quote lines and recognizes document dividers', () => {
    expect(parseResultMarkdown('> Keep the scope calm.\n> Ship one useful path.\n\n---\n\nNext section')).toEqual([
      { type: 'quote', content: 'Keep the scope calm. Ship one useful path.' },
      { type: 'divider' },
      { type: 'paragraph', content: 'Next section' }
    ])
  })

  it('keeps strong, code and emphasis as safe inline segments', () => {
    const source = 'Use **Flow Space**, run ' + tick + 'v1' + tick + ', then *review*.'
    expect(parseInlineMarkdown(source)).toEqual([
      { type: 'text', content: 'Use ' },
      { type: 'strong', content: 'Flow Space' },
      { type: 'text', content: ', run ' },
      { type: 'code', content: 'v1' },
      { type: 'text', content: ', then ' },
      { type: 'emphasis', content: 'review' },
      { type: 'text', content: '.' }
    ])
  })

  it('creates plain key-point text without markdown markers', () => {
    const source = '**Owner:** use ' + tick + 'Flow Space' + tick + ' for *review*'
    expect(cleanResultInlineText(source)).toBe('Owner: use Flow Space for review')
  })
})
