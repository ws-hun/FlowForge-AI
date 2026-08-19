import http from './client'
import type {
  ApiKeyConfig,
  FlowNodeArtifactDetail,
  FlowNodeArtifactLineage,
  FlowNodeArtifactSummary,
  ProviderConnectionTestResponse,
  RunTaskPayload,
  SaveApiKeyPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

export function runTask(payload: RunTaskPayload) {
  const { flowRunContext, flowVariableValues, ...request } = payload
  return http.post<TaskRunResponse>('/api/tasks/run', {
    ...request,
    ...(payload.flowId
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

export function listTaskArtifacts(taskId: string) {
  return http.get<FlowNodeArtifactSummary[]>(`/api/tasks/${taskId}/artifacts`)
}

export function getTaskArtifact(taskId: string, artifactKey: string) {
  return http.get<FlowNodeArtifactDetail>(
    `/api/tasks/${taskId}/artifacts/${encodeURIComponent(artifactKey)}`
  )
}

export function getTaskArtifactLineage(taskId: string, artifactKey: string) {
  return http.get<FlowNodeArtifactLineage>(
    `/api/tasks/${taskId}/artifacts/${encodeURIComponent(artifactKey)}/lineage`
  )
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

export function testApiKey(id: string) {
  return http.post<ProviderConnectionTestResponse>(`/api/settings/api-keys/${id}/test`)
}

export function deleteApiKey(id: string) {
  return http.delete<void>(`/api/settings/api-keys/${id}`)
}
