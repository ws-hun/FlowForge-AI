export type PromptVariableValues = Record<string, string>

const PROMPT_VARIABLE_PATTERN = /\{[a-zA-Z0-9_\u4e00-\u9fa5-]+\}/g

export function extractPromptVariables(content: string): string[] {
  const matches = content.match(PROMPT_VARIABLE_PATTERN) || []
  return Array.from(new Set(matches.map((match) => match.slice(1, -1))))
}

export function applyPromptVariables(content: string, values: PromptVariableValues): string {
  return content.replace(PROMPT_VARIABLE_PATTERN, (match) => {
    const variable = match.slice(1, -1)
    const value = values[variable]
    return value?.trim() ? value : match
  })
}
