// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { FlowExecutionPreviewResponse } from '@/types'
import { previewFlowExecution } from '@/api/flows'
import FlowExecutionInputPreview from '../FlowExecutionInputPreview.vue'

vi.mock('@/api/flows', () => ({
  previewFlowExecution: vi.fn()
}))

const apps: App[] = []
const response = {
  executionMode: 'single-pass',
  providerCallCount: 1,
  compilerVersion: 'flow-plan-v5',
  executionInputFingerprint: 'a'.repeat(64),
  executionInput: 'compiled execution input',
  flowRunSnapshot: { nodes: [] },
  sections: [],
  executionPlan: { version: 'flow-plan-v5', scheduling: 'linear', steps: [] },
  executable: true,
  missingVariables: [],
  incompleteNodes: []
} as unknown as FlowExecutionPreviewResponse

async function mountPreview() {
  vi.mocked(previewFlowExecution).mockResolvedValue({ data: response } as never)
  const container = document.createElement('div')
  document.body.append(container)
  const app = createApp({ render: () => h(FlowExecutionInputPreview, { flowId: 'flow-1' }) })
  apps.push(app)
  app.mount(container)

  const details = container.querySelector('details')!
  details.open = true
  details.dispatchEvent(new Event('toggle'))
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
  return container
}

afterEach(() => {
  apps.forEach((app) => app.unmount())
  apps.length = 0
  document.body.replaceChildren()
  vi.clearAllMocks()
})

describe('Flow execution preview tabs', () => {
  it('associates each tab with its labeled panel and exposes one tab stop', async () => {
    const container = await mountPreview()
    const tabs = Array.from(container.querySelectorAll<HTMLButtonElement>('[role="tab"]'))

    expect(tabs).toHaveLength(2)
    expect(tabs.map((tab) => tab.tabIndex)).toEqual([0, -1])
    expect(tabs.map((tab) => container.querySelector(`[role="tabpanel"][aria-labelledby="${tab.id}"]`)?.id))
      .toEqual(tabs.map((tab) => tab.getAttribute('aria-controls')))
    expect(container.querySelector('[role="tabpanel"]')?.textContent).toContain('flow-plan-v5')
  })

  it('moves focus and selection with arrow, Home and End keys', async () => {
    const container = await mountPreview()
    const outlineTab = container.querySelector<HTMLButtonElement>('[role="tab"]')!
    outlineTab.focus()
    outlineTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await nextTick()

    const rawTab = container.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')!
    expect(rawTab.textContent?.trim()).toBe('原始输入')
    expect(document.activeElement).toBe(rawTab)
    expect(container.querySelector(`#${rawTab.getAttribute('aria-controls')}`)?.textContent)
      .toContain('compiled execution input')

    rawTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }))
    await nextTick()
    expect(container.querySelector('[role="tab"][aria-selected="true"]')?.textContent?.trim()).toBe('执行结构')
    expect(document.activeElement).toBe(container.querySelector('[role="tab"][aria-selected="true"]'))

    container.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }))
    await nextTick()
    expect(container.querySelector('[role="tab"][aria-selected="true"]')?.textContent?.trim()).toBe('原始输入')
  })

  it('switches views when a tab is activated by click', async () => {
    const container = await mountPreview()
    container.querySelector<HTMLButtonElement>('[role="tab"]:nth-child(2)')!.click()
    await nextTick()

    const selectedTab = container.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')!
    expect(selectedTab.textContent?.trim()).toBe('原始输入')
    expect(container.querySelector(`#${selectedTab.getAttribute('aria-controls')}`)?.textContent)
      .toContain('compiled execution input')
  })
})
