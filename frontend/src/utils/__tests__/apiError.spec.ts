import { describe, expect, it } from 'vitest'
import axios, { AxiosHeaders } from 'axios'
import { apiErrorMessage, apiErrorRunId, apiErrorStatus } from '@/utils/apiError'

function responseError(data: unknown, status = 400) {
  return new axios.AxiosError(
    'Request failed',
    'ERR_BAD_REQUEST',
    { headers: new AxiosHeaders() },
    undefined,
    {
      data,
      status,
      statusText: '',
      headers: {},
      config: { headers: new AxiosHeaders() }
    }
  )
}

describe('api error adapter', () => {
  it('reads the shared backend message and failed run identity', () => {
    const error = responseError({ message: 'Provider 暂时不可用', runId: 'run-1' }, 502)

    expect(apiErrorMessage(error, '执行失败')).toBe('Provider 暂时不可用')
    expect(apiErrorStatus(error)).toBe(502)
    expect(apiErrorRunId(error)).toBe('run-1')
  })

  it('supports text responses from a proxy or gateway', () => {
    expect(apiErrorMessage(responseError('服务暂时不可用', 503), '执行失败'))
      .toBe('服务暂时不可用')
  })

  it('extracts the shared contract when a gateway forwards JSON as text', () => {
    const error = responseError('{"message":"Provider 认证失败","runId":"run-2"}', 502)

    expect(apiErrorMessage(error, '执行失败')).toBe('Provider 认证失败')
    expect(apiErrorRunId(error)).toBe('run-2')
  })

  it('gives timeout requests a calm actionable suffix', () => {
    const error = new axios.AxiosError('timeout', 'ECONNABORTED')
    expect(apiErrorMessage(error, '任务执行失败')).toBe('任务执行失败（请求超时）')
  })

  it('keeps non-axios and malformed responses on the caller fallback', () => {
    expect(apiErrorMessage(new Error('offline'), '无法连接服务')).toBe('无法连接服务')
    expect(apiErrorMessage(responseError({ error: 'bad' }), '请求失败')).toBe('请求失败')
    expect(apiErrorRunId(responseError({ runId: 42 }))).toBe('')
  })
})
