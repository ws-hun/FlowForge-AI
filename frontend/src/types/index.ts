export type Provider = 'deepseek' | 'openai'

export interface TaskRunResponse {
  summary: string
  result: string
  raw: string
}

export interface TaskHistoryItem {
  id: string
  input: string
  summary: string
  result: string
  createdAt: string
}

export interface ApiKeyConfig {
  id: string
  provider: Provider
  maskedKey: string
  baseUrl: string
  model: string
  active: boolean
  updatedAt: string
}

export interface SaveApiKeyPayload {
  provider: Provider
  apiKey: string
  baseUrl: string
  model: string
  active: boolean
}
