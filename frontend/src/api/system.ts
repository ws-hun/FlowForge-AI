import http from './client'
import type { HealthResponse } from '@/types'

export function getHealth() {
  return http.get<HealthResponse>('/api/health')
}
