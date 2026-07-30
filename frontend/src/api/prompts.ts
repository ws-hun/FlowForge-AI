import http from './client'
import type { PromptAsset, PromptVersion, SavePromptPayload, TaskHistoryItem } from '@/types'

export interface PromptQuery {
  query?: string
  category?: string
  favorite?: boolean
}

export function listPrompts(params: PromptQuery = {}) {
  return http.get<PromptAsset[]>('/api/prompts', { params })
}

export function createPrompt(payload: SavePromptPayload) {
  return http.post<PromptAsset>('/api/prompts', payload)
}

export function updatePrompt(id: string, payload: SavePromptPayload) {
  return http.put<PromptAsset>(`/api/prompts/${id}`, payload)
}

export function togglePromptFavorite(id: string, revision: number) {
  return http.patch<PromptAsset>(`/api/prompts/${id}/favorite`, { revision })
}

export function deletePrompt(id: string, revision: number) {
  return http.delete<void>(`/api/prompts/${id}`, { params: { revision } })
}

export function listPromptRuns(id: string) {
  return http.get<TaskHistoryItem[]>(`/api/prompts/${id}/runs`)
}

export function listPromptVersions(id: string) {
  return http.get<PromptVersion[]>(`/api/prompts/${id}/versions`)
}

export function restorePromptVersion(id: string, versionId: string, revision: number) {
  return http.post<PromptAsset>(`/api/prompts/${id}/versions/${versionId}/restore`, { revision })
}
