import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosHeaders } from 'axios'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  listTasks: vi.fn(),
  listApiKeys: vi.fn(),
  listFlows: vi.fn(),
  runTask: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api/tasks', () => ({
  activateApiKey: vi.fn(),
  deleteApiKey: vi.fn(),
  listApiKeys: api.listApiKeys,
  listTasks: api.listTasks,
  recoverTask: vi.fn(),
  rerunTask: vi.fn(),
  runTask: api.runTask,
  saveApiKey: vi.fn(),
  testApiKey: vi.fn()
}))

vi.mock('@/api/flows', () => ({
  createFlow: vi.fn(),
  deleteFlow: vi.fn(),
  listFlows: api.listFlows,
  restoreFlowVersion: vi.fn(),
  updateFlow: vi.fn()
}))

vi.mock('@/api/prompts', () => ({
  createPrompt: vi.fn()
}))

import { useWorkspaceStore } from '@/stores/workspace'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })
  return { promise, resolve }
}

function responseError(data: unknown, status = 502) {
  return new axios.AxiosError(
    'Request failed',
    'ERR_BAD_RESPONSE',
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

describe('workspace bootstrap', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    api.listTasks.mockResolvedValue({ data: [] })
    api.listApiKeys.mockResolvedValue({ data: [] })
    api.listFlows.mockResolvedValue({ data: [] })
  })

  it('retries incomplete initialization and caches only a fully loaded workspace', async () => {
    api.listTasks.mockRejectedValueOnce(new Error('offline'))
    const workspace = useWorkspaceStore()

    await workspace.bootstrap()
    await workspace.bootstrap()
    await workspace.bootstrap()

    expect(api.listTasks).toHaveBeenCalledTimes(2)
    expect(api.listApiKeys).toHaveBeenCalledTimes(2)
    expect(api.listFlows).toHaveBeenCalledTimes(2)
    expect(workspace.flowAssetsReady).toBe(true)
  })

  it('keeps the newest history refresh when an older request finishes first', async () => {
    const firstResponse = deferred<{ data: Array<{ id: string }> }>()
    const secondResponse = deferred<{ data: Array<{ id: string }> }>()
    api.listTasks
      .mockReturnValueOnce(firstResponse.promise)
      .mockReturnValueOnce(secondResponse.promise)
    const workspace = useWorkspaceStore()

    const firstRequest = workspace.loadTasks()
    const secondRequest = workspace.loadTasks()
    firstResponse.resolve({ data: [{ id: 'old-run' }] })
    await firstRequest

    expect(workspace.tasks).toEqual([])
    expect(workspace.historyLoading).toBe(true)

    secondResponse.resolve({ data: [{ id: 'new-run' }] })
    await secondRequest

    expect(workspace.tasks).toEqual([{ id: 'new-run' }])
    expect(workspace.historyLoading).toBe(false)
  })

  it('distinguishes unavailable Provider data from a successfully loaded empty vault', async () => {
    api.listApiKeys.mockRejectedValueOnce(new Error('offline'))
    const workspace = useWorkspaceStore()

    expect(workspace.apiKeysLoadAttempted).toBe(false)
    await workspace.loadApiKeys()
    expect(workspace.apiKeysReady).toBe(false)
    expect(workspace.apiKeysLoadAttempted).toBe(true)

    await workspace.loadApiKeys()
    expect(workspace.apiKeysReady).toBe(true)
    expect(workspace.apiKeys).toEqual([])
  })

  it('keeps a saved failed run visible when the follow-up history refresh is offline', async () => {
    api.listApiKeys.mockResolvedValueOnce({
      data: [
        {
          id: 'provider-1',
          provider: 'deepseek',
          maskedKey: 'sk-...1234',
          baseUrl: 'https://api.deepseek.com',
          model: 'deepseek-chat',
          active: true,
          updatedAt: '2026-09-14T00:00:00Z'
        }
      ]
    })
    api.runTask.mockRejectedValueOnce(responseError({ message: 'Provider 暂时不可用', runId: 'failed-run-1' }))
    api.listTasks.mockRejectedValueOnce(new Error('offline'))
    const workspace = useWorkspaceStore()

    await workspace.loadApiKeys()
    workspace.taskInput = '生成一份执行方案'
    await workspace.executeTask()

    expect(workspace.failedRunId).toBe('failed-run-1')
    expect(workspace.failedRun).toMatchObject({
      id: 'failed-run-1',
      input: '生成一份执行方案',
      status: 'failed',
      errorMessage: 'Provider 暂时不可用'
    })
  })

  it('allows only one active Flow Provider execution at a time', async () => {
    api.listFlows.mockResolvedValueOnce({
      data: [{
        id: 'flow-1',
        title: 'Release Flow',
        description: 'Prepare a release brief',
        nodes: [],
        revision: 1,
        createdAt: '2026-09-17T00:00:00Z',
        updatedAt: '2026-09-17T00:00:00Z'
      }]
    })
    const providerResponse = deferred<{ data: Record<string, unknown> }>()
    api.runTask.mockReturnValueOnce(providerResponse.promise)
    const workspace = useWorkspaceStore()
    await workspace.loadFlowDrafts()

    const firstExecution = workspace.executeActiveFlow('Release context', {})
    const duplicateExecution = await workspace.executeActiveFlow('Duplicate context', {})

    expect(duplicateExecution).toBeNull()
    expect(api.runTask).toHaveBeenCalledTimes(1)
    expect(api.runTask).toHaveBeenCalledWith(expect.objectContaining({
      flowId: 'flow-1',
      flowRunContext: 'Release context'
    }))

    providerResponse.resolve({
      data: {
        summary: 'Release ready',
        result: 'Proceed',
        raw: '{}',
        executionInput: 'Release context',
        taskId: 'run-1'
      }
    })
    await firstExecution

    expect(workspace.running).toBe(false)
    expect(workspace.latestResult?.taskId).toBe('run-1')
  })
})
