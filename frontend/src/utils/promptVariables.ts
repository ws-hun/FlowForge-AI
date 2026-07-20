export type PromptVariableValues = Record<string, string>

const PROMPT_VARIABLE_PATTERN = /\{[a-zA-Z0-9_\u4e00-\u9fa5-]+\}/g
const PROMPT_VARIABLE_NAME_PATTERN = /^[a-zA-Z0-9_\u4e00-\u9fa5-]+$/

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

export function isValidPromptVariableName(value: string): boolean {
  return PROMPT_VARIABLE_NAME_PATTERN.test(value.trim())
}

export function renamePromptVariable(content: string, currentName: string, nextName: string): string {
  if (!isValidPromptVariableName(currentName) || !isValidPromptVariableName(nextName)) {
    return content
  }

  return content.split(`{${currentName.trim()}}`).join(`{${nextName.trim()}}`)
}
