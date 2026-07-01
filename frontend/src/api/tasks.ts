import http from './client'
import type {
  ApiKeyConfig,
  SaveApiKeyPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

export function runTask(input: string) {
  return http.post<TaskRunResponse>('/api/tasks/run', { input })
}

export function listTasks() {
  return http.get<TaskHistoryItem[]>('/api/tasks')
}

export function listApiKeys() {
  return http.get<ApiKeyConfig[]>('/api/settings/api-keys')
}

export function saveApiKey(payload: SaveApiKeyPayload) {
  return http.post<ApiKeyConfig>('/api/settings/api-keys', payload)
}

export function activateApiKey(id: string) {
  return http.patch<ApiKeyConfig>(`/api/settings/api-keys/${id}/activate`)
}

export function deleteApiKey(id: string) {
  return http.delete<void>(`/api/settings/api-keys/${id}`)
}
