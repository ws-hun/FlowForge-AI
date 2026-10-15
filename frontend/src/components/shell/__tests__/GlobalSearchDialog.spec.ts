// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App, type SetupContext } from 'vue'
import GlobalSearchDialog from '../GlobalSearchDialog.vue'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  bootstrap: vi.fn().mockResolvedValue(true),
  listPrompts: vi.fn().mockResolvedValue({ data: [] })
}))
const apps: App[] = []

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush })
}))

vi.mock('@/stores/workspace', () => ({
  useWorkspaceStore: () => ({ flowDrafts: [], tasks: [], bootstrap: mocks.bootstrap })
}))

vi.mock('@/api/prompts', () => ({ listPrompts: mocks.listPrompts }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => {
  const Icon = { render: () => h('span') }
  return { Clock: Icon, Close: Icon, Connection: Icon, Document: Icon, Plus: Icon, Search: Icon }
})

async function mountSearch() {
  const container = document.createElement('div')
  document.body.append(container)
  const app = createApp(GlobalSearchDialog, { open: true })
  app.component('el-dialog', {
    props: ['modelValue'],
    setup(_props: Record<string, unknown>, context: SetupContext) {
      return () => h('div', context.slots.default?.())
    }
  })
  apps.push(app)
  app.mount(container)
  await nextTick()
  await Promise.resolve()
  await Promise.resolve()
  return container
}

afterEach(() => {
  apps.forEach((app) => app.unmount())
  apps.length = 0
  document.body.replaceChildren()
  mocks.routerPush.mockClear()
  mocks.bootstrap.mockClear()
  mocks.listPrompts.mockClear()
})

describe('Global search combobox', () => {
  it('links the active option to the labeled combobox as selection moves', async () => {
    const container = await mountSearch()
    const input = container.querySelector<HTMLInputElement>('[role="combobox"]')!
    const listbox = container.querySelector('[role="listbox"]')!
    const options = Array.from(container.querySelectorAll<HTMLElement>('[role="option"]'))
    expect(input.getAttribute('aria-label')).toBe('搜索 Flow、Prompt、结果或操作')
    expect(input.getAttribute('aria-expanded')).toBe('true')
    expect(input.getAttribute('aria-controls')).toBe(listbox.id)
    expect(input.getAttribute('aria-activedescendant')).toBe(options[0].id)
    expect(options[0].getAttribute('aria-selected')).toBe('true')
    input.focus()
    expect(document.activeElement).toBe(input)

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await nextTick()
    expect(input.getAttribute('aria-activedescendant')).toBe(options[1].id)
    expect(options[0].getAttribute('aria-selected')).toBe('false')
    expect(options[1].getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(input)

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(mocks.routerPush).toHaveBeenCalledWith('/workflows')
  })

  it('leaves IME candidate navigation and confirmation keys to the input method', async () => {
    const container = await mountSearch()
    const input = container.querySelector<HTMLInputElement>('[role="combobox"]')!
    const initialActiveOption = input.getAttribute('aria-activedescendant')

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, isComposing: true }))
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, isComposing: true }))
    await nextTick()

    expect(input.getAttribute('aria-activedescendant')).toBe(initialActiveOption)
    expect(mocks.routerPush).not.toHaveBeenCalled()
  })
})
