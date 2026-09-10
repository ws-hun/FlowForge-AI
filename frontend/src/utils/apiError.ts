import axios from 'axios'

type ApiErrorPayload = {
  message?: unknown
  runId?: unknown
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) {
    return fallback
  }

  const payload = error.response?.data
  if (typeof payload === 'string' && payload.trim()) {
    return payload.trim()
  }
  if (payload && typeof payload === 'object') {
    const message = (payload as ApiErrorPayload).message
    if (typeof message === 'string' && message.trim()) {
      return message.trim()
    }
  }

  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return `${fallback}（请求超时）`
  }

  return fallback
}

export function apiErrorStatus(error: unknown) {
  return axios.isAxiosError(error) ? error.response?.status || null : null
}

export function apiErrorRunId(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return ''
  }

  const runId = error.response?.data && typeof error.response.data === 'object'
    ? (error.response.data as ApiErrorPayload).runId
    : null
  return typeof runId === 'string' ? runId : ''
}
