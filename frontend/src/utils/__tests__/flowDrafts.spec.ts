import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { persistFlowCreationDraft, readFlowCreationDraft } from '../flowCreationDraft'
import {
  buildFlowEditorPreview,
  buildRecoveredFlowSnapshot,
  captureFlowEditorSnapshot,
  persistFlowEditorDraft,
  readFlowEditorDraft,
  removeFlowEditorDraft,
  type FlowEditorDraft
} from '../flowEditorDraft'
import type { FlowDraft } from '@/types'
import { MemoryStorage } from './memoryStorage'

const sourceFlow: FlowDraft = {
  id: 'flow-1',
  title: 'Original Flow',
  description: 'Original goal',
  revision: 4,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-02T00:00:00.000Z',
  nodes: [
    {
      id: 'input-1',
      type: 'input',
      title: 'Intent',
      description: '用户想完成的 AI 工作',
      content: 'Original goal'
    },
    {
      id: 'prompt-1',
      type: 'prompt',
      title: 'Draft contract',
      description: 'Create the first draft',
      content: 'Original prompt',
      promptId: 'asset-1',
      promptTitle: 'API contract'
    },
    {
      id: 'task-1',
      type: 'ai-task',
      title: 'AI execution',
      description: 'Run the provider',
      content: 'Return structured output'
    },
    {
      id: 'output-1',
      type: 'output',
      title: 'Result',
      description: 'Keep the result',
      content: 'Summary and actions'
    }
  ]
}

function createEditorDraft(): FlowEditorDraft {
  return {
    flowId: sourceFlow.id,
    baseRevision: sourceFlow.revision,
    nodeId: 'prompt-1',
    flowChanged: true,
    nodeChanged: true,
    flowTitle: 'Recovered Flow',
    flowDescription: 'Recovered goal',
    nodeTitle: 'Recovered contract',
    nodeDescription: 'Recovered description',
    nodeContent: 'Recovered prompt',
    snapshot: captureFlowEditorSnapshot(sourceFlow),
    updatedAt: '2026-01-03T00:00:00.000Z'
  }
}

beforeEach(() => {
  vi.stubGlobal('window', { localStorage: new MemoryStorage() })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Flow creation drafts', () => {
  it('round-trips unfinished creation input and clears empty drafts', () => {
    persistFlowCreationDraft({
      intent: 'Build an API review flow',
      templateTitle: 'Spec to API',
      updatedAt: '2026-01-03T00:00:00.000Z'
    })

    expect(readFlowCreationDraft()).toEqual({
      intent: 'Build an API review flow',
      templateTitle: 'Spec to API',
      updatedAt: '2026-01-03T00:00:00.000Z'
    })

    persistFlowCreationDraft(null)
    expect(readFlowCreationDraft()).toBeNull()
  })

  it('ignores malformed browser data', () => {
    window.localStorage.setItem('flowforge.flowCreationDraft', '{broken')
    expect(readFlowCreationDraft()).toBeNull()
  })
})

describe('Flow editor drafts', () => {
  it('round-trips a valid editor draft and removes it explicitly', () => {
    const draft = createEditorDraft()
    persistFlowEditorDraft(draft)

    expect(readFlowEditorDraft()).toMatchObject(draft)

    removeFlowEditorDraft()
    expect(readFlowEditorDraft()).toBeNull()
  })

  it('applies pending metadata and node edits without mutating the saved snapshot', () => {
    const draft = createEditorDraft()
    const recovered = buildRecoveredFlowSnapshot(draft)

    expect(recovered.title).toBe('Recovered Flow')
    expect(recovered.description).toBe('Recovered goal')
    expect(recovered.nodes.find((node) => node.id === 'input-1')).toMatchObject({
      description: '用户想完成的 AI 工作',
      content: 'Recovered goal'
    })
    expect(recovered.nodes.find((node) => node.id === 'prompt-1')).toMatchObject({
      title: 'Recovered contract',
      description: 'Recovered description',
      content: 'Recovered prompt'
    })
    expect(draft.snapshot).toEqual(captureFlowEditorSnapshot(sourceFlow))
  })

  it('builds a current comparison preview without committing pending editor state', () => {
    const preview = buildFlowEditorPreview(sourceFlow, {
      flowChanged: true,
      flowTitle: '  Pending Flow  ',
      flowDescription: '  Pending goal  ',
      nodeChanged: true,
      nodeId: 'prompt-1',
      nodeTitle: '  Pending contract  ',
      nodeDescription: '  Pending description  ',
      nodeContent: '  Pending prompt  '
    })

    expect(preview).toMatchObject({
      title: 'Pending Flow',
      description: 'Pending goal'
    })
    expect(preview.nodes.find((node) => node.id === 'input-1')).toMatchObject({
      description: '用户想完成的 AI 工作',
      content: 'Pending goal'
    })
    expect(preview.nodes.find((node) => node.id === 'prompt-1')).toMatchObject({
      title: 'Pending contract',
      description: 'Pending description',
      content: 'Pending prompt'
    })
    expect(sourceFlow.title).toBe('Original Flow')
    expect(sourceFlow.description).toBe('Original goal')
    expect(sourceFlow.nodes.find((node) => node.id === 'prompt-1')?.content).toBe('Original prompt')
  })

  it('rejects snapshots with duplicate node identities', () => {
    const draft = createEditorDraft()
    draft.snapshot.nodes[1].id = draft.snapshot.nodes[0].id
    window.localStorage.setItem('flowforge.flowEditorDraft', JSON.stringify(draft))

    expect(readFlowEditorDraft()).toBeNull()
  })
})
