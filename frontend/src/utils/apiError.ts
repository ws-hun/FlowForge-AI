import axios from 'axios'

type ApiErrorPayload = {
  message?: unknown
  runId?: unknown
}

function objectPayload(payload: unknown): ApiErrorPayload | null {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    return payload as ApiErrorPayload
  }

  if (typeof payload !== 'string' || !payload.trim()) {
    return null
  }

  try {
    const parsed = JSON.parse(payload)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as ApiErrorPayload
      : null
  } catch {
    return null
  }
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) {
    return fallback
  }

  const payload = error.response?.data
  const textPayload = typeof payload === 'string' ? payload.trim() : ''
  const message = objectPayload(payload)?.message
  if (typeof message === 'string' && message.trim()) {
    return message.trim()
  }
  if (textPayload) {
    return textPayload
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

  const runId = objectPayload(error.response?.data)?.runId
  return typeof runId === 'string' ? runId : ''
}
