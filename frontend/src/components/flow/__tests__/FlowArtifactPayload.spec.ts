// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, shallowRef, type App } from 'vue'
import type { FlowNodeArtifactDetail } from '@/types'
import FlowArtifactPayload from '../FlowArtifactPayload.vue'

const apps: App[] = []
function artifact(overrides: Partial<FlowNodeArtifactDetail> = {}): FlowNodeArtifactDetail {
  return {
    id: 'artifact-1', taskId: 'run-1', flowId: 'flow-1', nodeId: 'output-1', sequence: 4,
    artifactKey: 'node:output-1:result', artifactType: 'result-document', state: 'materialized',
    mediaType: 'text/markdown', payload: '# 项目计划\n\n- **构建**工作空间\n- 发布预览',
    createdAt: '2026-10-12T00:00:00Z', ...overrides
  }
}

function mount(detail: FlowNodeArtifactDetail) {
  const current = shallowRef(detail)
  const container = document.createElement('div')
  document.body.append(container)
  const app = createApp({ render: () => h(FlowArtifactPayload, { detail: current.value }) })
  apps.push(app)
  app.mount(container)
  return { container, update: (value: FlowNodeArtifactDetail) => { current.value = value } }
}

afterEach(() => {
  apps.forEach((app) => app.unmount())
  apps.length = 0
  document.body.replaceChildren()
})

describe('Flow Artifact payload presentation', () => {
  it.each([
    ['text/markdown', 'flowforge-result-document.md'],
    ['application/json; charset=utf-8', 'flowforge-result-document.json'],
    ['text/plain', 'flowforge-result-document.txt']
  ])('downloads the exact saved payload as %s', async (mediaType, filename) => {
    const payload = '  # 原始结果\r\n{"kept": true}  \n'
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:artifact')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const downloadedFilenames: string[] = []
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloadedFilenames.push(this.download)
    })
    const { container } = mount(artifact({ mediaType, payload }))
    container.querySelector<HTMLButtonElement>('[aria-label="下载原始产物"]')!.click()

    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(await blob.text()).toBe(payload)
    expect(blob.type).toBe(mediaType.split(';')[0] + ';charset=utf-8')
    expect(click).toHaveBeenCalledOnce()
    expect(downloadedFilenames).toEqual([filename])
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:artifact')
  })

  it('renders result headings, lists, emphasis and code using the shared document body', () => {
    const { container } = mount(artifact({ payload: '# 计划\n\n- **构建**空间\n\n```js\nconst ready = true\n```' }))
    expect(container.querySelector('h4')?.textContent?.trim()).toBe('计划')
    expect(container.querySelector('li')?.textContent?.trim()).toBe('构建空间')
    expect(container.querySelector('.result-inline-strong')?.textContent).toBe('构建')
    expect(container.querySelector('.rendered-code')?.textContent).toBe('const ready = true')
    expect(container.querySelector('.rendered-document--unframed')).not.toBeNull()
  })

  it('keeps the saved AI Task summary and result as separate document sections', () => {
    const { container } = mount(artifact({
      artifactType: 'provider-result',
      payload: '## 摘要\n\n> 给产品团队的计划\n\n## 结果\n\n分三步交付。'
    }))
    expect(Array.from(container.querySelectorAll('h4')).map((heading) => heading.textContent?.trim()))
      .toEqual(['摘要', '结果'])
    expect(container.querySelector('blockquote')?.textContent?.trim()).toBe('给产品团队的计划')
    expect(container.querySelector('.rendered-document')?.textContent).toContain('分三步交付。')
  })

  it('keeps the exact saved payload in a collapsed raw view', () => {
    const payload = '  # 计划\r\n\r\n- 工作空间  \r\n'
    const { container } = mount(artifact({ payload }))
    const raw = container.querySelector<HTMLDetailsElement>('.flow-artifact-raw')!
    expect(raw.open).toBe(false)
    raw.open = true
    expect(raw.querySelector('pre')?.textContent).toBe(payload)
  })

  it.each(['text/markdown', 'application/json; charset=utf-8'])('renders whole structured results for %s without changing their raw bytes', (mediaType) => {
    const payload = ' {"endpoints":[{"method":"post","path":"/workflows","description":"创建工作流"}]} \n'
    const { container } = mount(artifact({ mediaType, payload }))
    expect(container.querySelector('h4')?.textContent?.trim()).toBe('Endpoints')
    expect(container.querySelector('.rendered-document')?.textContent).toContain('POST /workflows')
    expect(container.querySelector('.flow-artifact-raw pre')?.textContent).toBe(payload)
  })

  it.each(['context-contribution', 'instruction-contribution'] as const)('keeps %s literal even when it declares Markdown', (artifactType) => {
    const payload = '# 指令\n- 不改变格式\n{"input":"原文"}'
    const { container } = mount(artifact({ artifactType, payload }))
    expect(container.querySelector('.flow-artifact-text')?.textContent).toBe(payload)
    expect(container.querySelector('.rendered-document')).toBeNull()
    expect(container.querySelector('details')).toBeNull()
  })

  it.each([undefined, 'text/plain', 'text/html'])('keeps results literal for an absent or unsupported media type: %s', (mediaType) => {
    const payload = '# 原始内容\n<script>alert(1)</script>'
    const { container } = mount(artifact({ mediaType, payload }))
    expect(container.querySelector('pre')?.textContent).toBe(payload)
    expect(container.querySelector('h4')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
  })

  it('escapes HTML in rendered text, inline code and fenced code', () => {
    const { container } = mount(artifact({ payload: '# <img src=x onerror=alert(1)>\n\n`<script>alert(1)</script>`\n\n```html\n<iframe src=x></iframe>\n```' }))
    expect(container.querySelectorAll('img, script, iframe')).toHaveLength(0)
    expect(container.querySelector('h4')?.textContent).toContain('<img src=x onerror=alert(1)>')
    expect(container.querySelector('.result-inline-code')?.textContent).toBe('<script>alert(1)</script>')
    expect(container.querySelector('.rendered-code')?.textContent).toBe('<iframe src=x></iframe>')
  })

  it.each(['failed', 'skipped'] as const)('does not fabricate a document for %s Artifacts', (state) => {
    const { container } = mount(artifact({ state, payload: null }))
    expect(container.querySelector('.flow-artifact-payload')).toBeNull()
  })

  it('resets raw expansion when a different saved Artifact replaces the current detail', async () => {
    const { container, update } = mount(artifact())
    container.querySelector<HTMLDetailsElement>('.flow-artifact-raw')!.open = true
    update(artifact({ id: 'artifact-2', taskId: 'run-2', payload: '# 新计划' }))
    await nextTick()
    expect(container.querySelector<HTMLDetailsElement>('.flow-artifact-raw')?.open).toBe(false)
    expect(container.querySelector('h4')?.textContent?.trim()).toBe('新计划')
  })
})
