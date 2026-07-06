import http from './client'
import type { FlowDraft, SaveFlowPayload } from '@/types'

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
