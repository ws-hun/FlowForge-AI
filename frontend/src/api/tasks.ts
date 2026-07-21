import http from './client'
import type {
  ApiKeyConfig,
  SaveApiKeyPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

export function runTask(
  input: string,
  promptId?: string | null,
  flowId?: string | null,
  flowRunContext?: string,
  flowVariableValues?: Record<string, string>
) {
  return http.post<TaskRunResponse>('/api/tasks/run', {
    input,
    promptId,
    flowId,
    ...(flowId
      ? {
          flowRunContext: flowRunContext || '',
          flowVariableValues: flowVariableValues || {}
        }
      : {})
  })
}

export function listTasks() {
  return http.get<TaskHistoryItem[]>('/api/tasks')
}

export function rerunTask(id: string) {
  return http.post<TaskRunResponse>(`/api/tasks/${id}/rerun`)
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
