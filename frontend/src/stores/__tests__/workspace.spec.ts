import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  listTasks: vi.fn(),
  listApiKeys: vi.fn(),
  listFlows: vi.fn()
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
  runTask: vi.fn(),
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
})
