// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, shallowRef, type App } from 'vue'
import type { FlowNodeArtifactDetail, FlowRunTrace as Trace } from '@/types'
import FlowRunTrace from '../FlowRunTrace.vue'

const api = vi.hoisted(() => ({ getTaskArtifact: vi.fn(), getTaskArtifactLineage: vi.fn(), error: vi.fn(), success: vi.fn() }))
vi.mock('@/api/tasks', () => api)
vi.mock('element-plus', () => ({ ElMessage: { error: api.error, success: api.success } }))
vi.mock('@/components/flow/FlowExecutionPlan.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/flow/FlowProviderAttempts.vue', () => ({ default: { render: () => null } }))

const key = 'node:prompt-1:instruction'
function trace(runId: string): Trace {
  return {
    runId, flowId: 'flow-1', status: 'completed', nodes: [{
      nodeId: 'prompt-1', nodeType: 'prompt', title: '写作指令', status: 'prepared', compiledContent: 'instructions',
      outputArtifact: { key, type: 'instruction-contribution', storage: 'node-artifact', state: 'materialized' }
    }]
  }
}

function detail(taskId: string): FlowNodeArtifactDetail {
  return {
    id: `artifact-${taskId}`, taskId, flowId: 'flow-1', nodeId: 'prompt-1', sequence: 1, artifactKey: key,
    artifactType: 'instruction-contribution', state: 'materialized', payload: taskId,
    createdAt: '2026-10-12T00:00:00Z'
  }
}

function deferred() {
  let resolve!: (value: { data: FlowNodeArtifactDetail }) => void
  const promise = new Promise<{ data: FlowNodeArtifactDetail }>((yes) => { resolve = yes })
  return { promise, resolve }
}

async function flush() {
  for (let index = 0; index < 12; index += 1) await nextTick()
}

type Props = { trace: Trace; focusedArtifactKey?: string; onArtifactFocusInvalid?: (key: string) => void }
const apps: App[] = []
function mount(props: Props) {
  const current = shallowRef(props)
  const container = document.createElement('div')
  document.body.append(container)
  const app = createApp({ render: () => h(FlowRunTrace, current.value) })
  apps.push(app)
  app.mount(container)
  return { container, update: (value: Props) => { current.value = value } }
}

beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'scrollIntoView').mockImplementation(() => {})
})
afterEach(() => {
  apps.forEach((app) => app.unmount())
  apps.length = 0
  document.body.replaceChildren()
  vi.resetAllMocks()
  vi.restoreAllMocks()
})

describe('Flow run Artifact inspection', () => {
  it('opens only the focused payload and scrolls within its own trace', async () => {
    const outside = document.createElement('section')
    outside.dataset.artifactKey = key
    document.body.append(outside)
    const outsideScroll = vi.spyOn(outside, 'scrollIntoView')
    api.getTaskArtifact.mockResolvedValue({ data: detail('run-1') })
    const { container } = mount({ trace: trace('run-1'), focusedArtifactKey: key })
    await flush()

    expect(api.getTaskArtifact).toHaveBeenCalledTimes(1)
    expect(api.getTaskArtifact).toHaveBeenCalledWith('run-1', key)
    expect(container.querySelector<HTMLDetailsElement>('.flow-run-trace')?.open).toBe(true)
    const payload = container.querySelector('[data-artifact-key]')
    expect(payload).not.toBeNull()
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledTimes(1)
    expect(vi.mocked(HTMLElement.prototype.scrollIntoView).mock.contexts[0]).toBe(payload)
    expect(outsideScroll).not.toHaveBeenCalled()
    expect(api.getTaskArtifactLineage).not.toHaveBeenCalled()
  })

  it('does not load payloads without an explicit focus or user action', async () => {
    const { container } = mount({ trace: trace('run-1') })
    await flush()
    expect(api.getTaskArtifact).not.toHaveBeenCalled()
    expect(container.querySelector<HTMLDetailsElement>('.flow-run-trace')?.open).toBe(false)
  })

  it('rejects a missing focused Artifact before requesting detail', async () => {
    const invalid = vi.fn()
    mount({ trace: trace('run-1'), focusedArtifactKey: 'missing', onArtifactFocusInvalid: invalid })
    await flush()
    expect(api.getTaskArtifact).not.toHaveBeenCalled()
    expect(invalid).toHaveBeenCalledWith('missing')
  })

  it('shares manual and deep-link requests while the same payload is loading', async () => {
    const pending = deferred()
    api.getTaskArtifact.mockReturnValue(pending.promise)
    const { container, update } = mount({ trace: trace('run-1') })
    container.querySelector<HTMLButtonElement>('.flow-run-trace-artifact-actions button')?.click()
    update({ trace: trace('run-1'), focusedArtifactKey: key })
    await flush()
    expect(api.getTaskArtifact).toHaveBeenCalledTimes(1)
    pending.resolve({ data: detail('run-1') })
    await flush()
    expect(container.querySelector('[data-artifact-key]')).not.toBeNull()
  })

  it('ignores the previous run completing while a new same-key request is pending', async () => {
    const old = deferred()
    const current = deferred()
    api.getTaskArtifact.mockImplementation((taskId) => taskId === 'run-1' ? old.promise : current.promise)
    const { container, update } = mount({ trace: trace('run-1'), focusedArtifactKey: key })
    await flush()
    update({ trace: trace('run-2'), focusedArtifactKey: key })
    await flush()
    old.resolve({ data: detail('run-1') })
    await flush()
    expect(container.querySelector('[data-artifact-key]')).toBeNull()
    current.resolve({ data: detail('run-2') })
    await flush()
    expect(container.querySelector('[data-artifact-key] pre')?.textContent).toBe('run-2')
    expect(api.error).not.toHaveBeenCalled()
  })

  it('prevents completion from opening payload after focus has been cleared', async () => {
    const pending = deferred()
    api.getTaskArtifact.mockReturnValue(pending.promise)
    const savedTrace = trace('run-1')
    const { container, update } = mount({ trace: savedTrace, focusedArtifactKey: key })
    await flush()
    update({ trace: savedTrace, focusedArtifactKey: '' })
    await flush()
    pending.resolve({ data: detail('run-1') })
    await flush()
    expect(container.querySelector('[data-artifact-key]')).toBeNull()
    expect(HTMLElement.prototype.scrollIntoView).not.toHaveBeenCalled()
  })

  it.each(['skipped', 'failed'] as const)('does not request a %s instruction Artifact', async (state) => {
    const savedTrace = trace('run-1')
    savedTrace.nodes[0].outputArtifact!.state = state
    const invalid = vi.fn()
    mount({ trace: savedTrace, focusedArtifactKey: key, onArtifactFocusInvalid: invalid })
    await flush()
    expect(api.getTaskArtifact).not.toHaveBeenCalled()
    expect(invalid).toHaveBeenCalledWith(key)
  })

  it('opens a failed Provider Artifact to inspect its saved call evidence', async () => {
    const savedTrace = trace('run-1')
    savedTrace.status = 'failed'
    savedTrace.nodes[0].nodeType = 'ai-task'
    savedTrace.nodes[0].status = 'failed'
    savedTrace.nodes[0].outputArtifact!.type = 'provider-result'
    savedTrace.nodes[0].outputArtifact!.state = 'failed'
    api.getTaskArtifact.mockResolvedValue({ data: {
      ...detail('run-1'), artifactType: 'provider-result', state: 'failed', payload: null
    } })
    const { container } = mount({ trace: savedTrace, focusedArtifactKey: key })
    await flush()
    expect(api.getTaskArtifact).toHaveBeenCalledTimes(1)
    expect(container.querySelector('[data-artifact-key]')).not.toBeNull()
    expect(container.querySelector('[data-artifact-key] pre')).toBeNull()
    expect(container.querySelector('[aria-label="复制节点产物"]')).toBeNull()
  })

  it('renders a Provider document and copies the exact saved payload rather than its readable conversion', async () => {
    const savedTrace = trace('run-1')
    savedTrace.nodes[0].nodeType = 'ai-task'
    savedTrace.nodes[0].status = 'completed'
    savedTrace.nodes[0].outputArtifact!.type = 'provider-result'
    const payload = '  # 摘要\r\n\r\n- **交付**文档  \r\n'
    api.getTaskArtifact.mockResolvedValue({ data: {
      ...detail('run-1'), artifactType: 'provider-result', mediaType: 'text/markdown', payload
    } })
    const copy = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined)
    const { container } = mount({ trace: savedTrace, focusedArtifactKey: key })
    await flush()
    expect(container.querySelector('[data-artifact-key] h1')?.textContent?.trim()).toBe('摘要')
    expect(container.querySelector<HTMLDetailsElement>('.flow-artifact-raw')?.open).toBe(false)
    container.querySelector<HTMLButtonElement>('[aria-label="复制节点产物"]')!.click()
    await flush()
    expect(copy).toHaveBeenCalledWith(payload)
    expect(api.success).toHaveBeenCalledWith('节点产物已复制')
    expect(api.getTaskArtifact).toHaveBeenCalledTimes(1)
  })

  it('does not scroll or report a late response after leaving the trace', async () => {
    const pending = deferred()
    api.getTaskArtifact.mockReturnValue(pending.promise)
    mount({ trace: trace('run-1'), focusedArtifactKey: key })
    await flush()
    apps.pop()!.unmount()
    pending.resolve({ data: detail('run-1') })
    await flush()
    expect(HTMLElement.prototype.scrollIntoView).not.toHaveBeenCalled()
    expect(api.error).not.toHaveBeenCalled()
  })
})
