import { describe, expect, it, vi } from 'vitest'
import type { FlowNodeArtifactDetail } from '@/types'
import { createFlowArtifactLoader } from '../flowArtifactLoader'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function detail(overrides: Partial<FlowNodeArtifactDetail> = {}): FlowNodeArtifactDetail {
  return {
    id: 'artifact-1', taskId: 'run-1', flowId: 'flow-1', nodeId: 'prompt-1', sequence: 1,
    artifactKey: 'node:prompt-1:instruction', artifactType: 'instruction-contribution',
    state: 'materialized', payload: 'saved instructions', createdAt: '2026-10-12T00:00:00Z',
    ...overrides
  }
}

const key = detail().artifactKey

describe('Flow Artifact loading', () => {
  it('shares one in-flight request and caches only the requested payload', async () => {
    const pending = deferred<FlowNodeArtifactDetail>()
    const fetch = vi.fn(() => pending.promise)
    const loader = createFlowArtifactLoader(fetch, vi.fn())
    loader.reset('run-1', 'flow-1')
    expect(fetch).not.toHaveBeenCalled()

    const first = loader.load('run-1', key)
    const second = loader.load('run-1', key)
    expect(first).toBe(second)
    await Promise.resolve()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith('run-1', key)
    expect(loader.loading.value[key]).toBe(true)
    pending.resolve(detail())
    expect(await first).toEqual(detail())
    expect(await second).toEqual(detail())
    expect(loader.loading.value).toEqual({})
    expect(Object.keys(loader.details.value)).toEqual([key])
    expect(await loader.load('run-1', key)).toEqual(detail())
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('keeps loading independent for two different Artifacts', async () => {
    const one = deferred<FlowNodeArtifactDetail>()
    const two = deferred<FlowNodeArtifactDetail>()
    const otherKey = 'node:output-1:result-document'
    const fetch = vi.fn((_taskId: string, artifactKey: string) => artifactKey === key ? one.promise : two.promise)
    const loader = createFlowArtifactLoader(fetch, vi.fn())
    loader.reset('run-1', 'flow-1')
    const first = loader.load('run-1', key)
    const second = loader.load('run-1', otherKey)
    one.resolve(detail())
    await first
    expect(loader.loading.value[key]).toBeUndefined()
    expect(loader.loading.value[otherKey]).toBe(true)
    two.resolve(detail({ artifactKey: otherKey }))
    await second
    expect(loader.loading.value).toEqual({})
  })

  it('ignores late old-run payloads without clearing a new same-key request', async () => {
    const old = deferred<FlowNodeArtifactDetail>()
    const current = deferred<FlowNodeArtifactDetail>()
    const fetch = vi.fn((taskId: string) => taskId === 'run-1' ? old.promise : current.promise)
    const onFailure = vi.fn()
    const loader = createFlowArtifactLoader(fetch, onFailure)
    loader.reset('run-1', 'flow-1')
    const first = loader.load('run-1', key)
    loader.reset('run-2', 'flow-1')
    const second = loader.load('run-2', key)
    old.resolve(detail())
    expect(await first).toBeNull()
    expect(loader.details.value).toEqual({})
    expect(loader.loading.value[key]).toBe(true)
    expect(loader.load('run-2', key)).toBe(second)
    current.resolve(detail({ taskId: 'run-2', payload: 'new instructions' }))
    expect(await second).toMatchObject({ taskId: 'run-2', payload: 'new instructions' })
    expect(onFailure).not.toHaveBeenCalled()
  })

  it('invalidates old scope even when returning to the same run', async () => {
    const pending = deferred<FlowNodeArtifactDetail>()
    const loader = createFlowArtifactLoader(() => pending.promise, vi.fn())
    loader.reset('run-1', 'flow-1')
    const first = loader.load('run-1', key)
    loader.reset('run-2', 'flow-1')
    loader.reset('run-1', 'flow-1')
    pending.resolve(detail())
    expect(await first).toBeNull()
    expect(loader.details.value).toEqual({})
  })

  it('does not report an old-run failure in the newly selected run', async () => {
    const pending = deferred<FlowNodeArtifactDetail>()
    const onFailure = vi.fn()
    const loader = createFlowArtifactLoader(() => pending.promise, onFailure)
    loader.reset('run-1', 'flow-1')
    const request = loader.load('run-1', key)
    loader.reset('run-2', 'flow-1')
    pending.reject(new Error('offline'))
    expect(await request).toBeNull()
    expect(onFailure).not.toHaveBeenCalled()
  })

  it('releases a failed request for retry and reports concurrent failure once', async () => {
    const onFailure = vi.fn()
    const fetch = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(detail())
    const loader = createFlowArtifactLoader(fetch, onFailure)
    loader.reset('run-1', 'flow-1')
    const first = loader.load('run-1', key)
    const second = loader.load('run-1', key)
    expect(await first).toBeNull()
    expect(await second).toBeNull()
    expect(onFailure).toHaveBeenCalledTimes(1)
    expect(loader.loading.value).toEqual({})
    expect(await loader.load('run-1', key)).toEqual(detail())
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it.each([
    { taskId: 'wrong-run' }, { flowId: 'wrong-flow' }, { artifactKey: 'wrong-key' }
  ])('rejects payload identity mismatches: %j', async (identity) => {
    const onFailure = vi.fn()
    const loader = createFlowArtifactLoader(async () => detail(identity), onFailure)
    loader.reset('run-1', 'flow-1')
    expect(await loader.load('run-1', key)).toBeNull()
    expect(loader.details.value).toEqual({})
    expect(onFailure).toHaveBeenCalledTimes(1)
  })

  it('does not request a missing or inactive run and drops data after disposal', async () => {
    const pending = deferred<FlowNodeArtifactDetail>()
    const fetch = vi.fn(() => pending.promise)
    const loader = createFlowArtifactLoader(fetch, vi.fn())
    loader.reset('run-1', 'flow-1')
    expect(await loader.load('', key)).toBeNull()
    expect(await loader.load('other-run', key)).toBeNull()
    expect(await loader.load('run-1', '')).toBeNull()
    expect(fetch).not.toHaveBeenCalled()
    const request = loader.load('run-1', key)
    loader.reset('', '')
    pending.resolve(detail())
    expect(await request).toBeNull()
    expect(loader.details.value).toEqual({})
  })
})
