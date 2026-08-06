import { describe, expect, it } from 'vitest'
import {
  applyPromptVariables,
  extractPromptVariables,
  findMissingPromptVariables,
  isValidPromptVariableName,
  renamePromptVariable
} from '../promptVariables'

describe('Prompt variables', () => {
  it('extracts unique variables in their first visible order', () => {
    expect(extractPromptVariables('For {audience}, use {tone}. Repeat {audience}.')).toEqual([
      'audience',
      'tone'
    ])
  })

  it('keeps unresolved placeholders while applying prepared values', () => {
    expect(applyPromptVariables('For {audience}, use {tone}.', {
      audience: 'product teams',
      tone: '   '
    })).toBe('For product teams, use {tone}.')
  })

  it('reports missing values once and ignores surrounding whitespace', () => {
    expect(findMissingPromptVariables(
      'For {audience}, use {tone}. Repeat {audience}.',
      { audience: ' teams ', tone: ' ' }
    )).toEqual(['tone'])
  })

  it('validates and renames only supported variable names', () => {
    expect(isValidPromptVariableName('目标-用户')).toBe(true)
    expect(isValidPromptVariableName('invalid name')).toBe(false)
    expect(renamePromptVariable('Write for {audience}.', 'audience', '目标-用户')).toBe(
      'Write for {目标-用户}.'
    )
    expect(renamePromptVariable('Write for {audience}.', 'audience', 'invalid name')).toBe(
      'Write for {audience}.'
    )
  })
})
