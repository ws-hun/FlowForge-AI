import { describe, expect, it } from 'vitest'
import {
  cleanResultBlockText,
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

  it('parses Markdown tables, column alignment, escaped pipes and inline code pipes', () => {
    const source = '| Node | Role | Count |\n| :--- | :---: | ---: |\n'
      + '| Input | `text | context` | 2 |\n| Output | result\\|document | 1 |'
    expect(parseResultMarkdown(source)).toEqual([{
      type: 'table',
      headers: ['Node', 'Role', 'Count'],
      alignments: ['left', 'center', 'right'],
      rows: [
        ['Input', '`text | context`', '2'],
        ['Output', 'result|document', '1']
      ]
    }])
  })

  it('does not classify prose or malformed tables as a Markdown table', () => {
    expect(parseResultMarkdown('A | B\nnot a delimiter')).toEqual([
      { type: 'paragraph', content: 'A | B not a delimiter' }
    ])
    expect(parseResultMarkdown('| A | B |\n| --- | --- |\n| only one cell |')).toEqual([
      { type: 'table', headers: ['A', 'B'], alignments: ['left', 'left'], rows: [] },
      { type: 'paragraph', content: '| only one cell |' }
    ])
  })

  it('parses checked and unchecked task lists without merging them with bullets', () => {
    expect(parseResultMarkdown('- [x] **Prepare** the Flow\n- [ ] Review [the result](https://example.com)\n- Ordinary note')).toEqual([
      {
        type: 'checklist',
        items: [
          { checked: true, content: '**Prepare** the Flow' },
          { checked: false, content: 'Review [the result](https://example.com)' }
        ]
      },
      { type: 'list', ordered: false, items: ['Ordinary note'] }
    ])
  })

  it('ends task lists at document boundaries and exposes clean key-point text', () => {
    const source = '- [x] Review [the guide](https://example.com)\n\n> Keep the result safe\n\n| Item | State |\n| --- | --- |\n| Plan | Ready |\n\n## Next\n- [ ] Ship **the Flow**'
    const blocks = parseResultMarkdown(source)

    expect(blocks.map((block) => block.type)).toEqual(['checklist', 'quote', 'table', 'heading', 'checklist'])
    expect(cleanResultBlockText(blocks[0])).toEqual(['Review the guide'])
    expect(cleanResultBlockText(blocks[4])).toEqual(['Ship the Flow'])
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

  it('parses only absolute HTTP, HTTPS and mailto links', () => {
    expect(parseInlineMarkdown('[Docs](https://example.com/guide) [Mail](mailto:team@example.com) [Unsafe](javascript:alert(1))'))
      .toEqual([
        { type: 'link', content: 'Docs', href: 'https://example.com/guide' },
        { type: 'text', content: ' ' },
        { type: 'link', content: 'Mail', href: 'mailto:team@example.com' },
        { type: 'text', content: ' [Unsafe](javascript:alert(1))' }
      ])
    expect(parseInlineMarkdown('[Credentials](https://user:secret@example.com)'))
      .toEqual([{ type: 'text', content: '[Credentials](https://user:secret@example.com)' }])
  })

  it('creates plain key-point text without markdown markers', () => {
    const source = '**Owner:** use ' + tick + 'Flow Space' + tick + ' for *review*'
    expect(cleanResultInlineText(source)).toBe('Owner: use Flow Space for review')
  })

  it('keeps safe link labels in key points without exposing URL syntax', () => {
    expect(cleanResultInlineText('Read **[the guide](https://example.com/start)** and `ship` it'))
      .toBe('Read the guide and ship it')
    expect(cleanResultInlineText('[Unsafe](javascript:alert(1))'))
      .toBe('[Unsafe](javascript:alert(1))')
  })
})
