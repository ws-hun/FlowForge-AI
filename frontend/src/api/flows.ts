import http from './client'
import type {
  FlowDraft,
  FlowExecutionPreviewRequest,
  FlowExecutionPreviewResponse,
  FlowVersion,
  SaveFlowPayload,
  TaskHistoryItem
} from '@/types'

export function listFlows() {
  return http.get<FlowDraft[]>('/api/flows')
}

export function createFlow(payload: SaveFlowPayload) {
  return http.post<FlowDraft>('/api/flows', payload)
}

export function updateFlow(id: string, payload: SaveFlowPayload) {
  return http.put<FlowDraft>(`/api/flows/${id}`, payload)
}

export function deleteFlow(id: string) {
  return http.delete<void>(`/api/flows/${id}`)
}

export function listFlowRuns(id: string) {
  return http.get<TaskHistoryItem[]>(`/api/flows/${id}/runs`)
}

export function previewFlowExecution(id: string, payload: FlowExecutionPreviewRequest) {
  return http.post<FlowExecutionPreviewResponse>(`/api/flows/${id}/execution-preview`, payload)
}

export function listFlowVersions(id: string) {
  return http.get<FlowVersion[]>(`/api/flows/${id}/versions`)
}

export function restoreFlowVersion(id: string, versionId: string) {
  return http.post<FlowDraft>(`/api/flows/${id}/versions/${versionId}/restore`)
}
