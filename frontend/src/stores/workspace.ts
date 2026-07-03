import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  activateApiKey,
  deleteApiKey,
  listApiKeys,
  listTasks,
  runTask,
  saveApiKey
} from '@/api/tasks'
import type { ApiKeyConfig, FlowDraft, FlowNode, PromptAsset, SaveApiKeyPayload, TaskHistoryItem, TaskRunResponse } from '@/types'

const FLOW_DRAFTS_STORAGE_KEY = 'flowforge.flowDrafts'
const ACTIVE_FLOW_STORAGE_KEY = 'flowforge.activeFlowId'

export const useWorkspaceStore = defineStore('workspace', () => {
  const tasks = ref<TaskHistoryItem[]>([])
  const apiKeys = ref<ApiKeyConfig[]>([])
  const flowDrafts = ref<FlowDraft[]>([])
  const activeFlowId = ref('')
  const latestResult = ref<TaskRunResponse | null>(null)
  const taskInput = ref('')
  const taskSourcePromptId = ref<string | null>(null)
  const taskSourcePromptTitle = ref('')
  const running = ref(false)
  const historyLoading = ref(false)
  const settingsLoading = ref(false)

  const activeProvider = computed(() => apiKeys.value.find((item) => item.active))
  const activeFlow = computed(() => flowDrafts.value.find((flow) => flow.id === activeFlowId.value) || null)

  async function loadTasks() {
    historyLoading.value = true
    try {
      const { data } = await listTasks()
      tasks.value = data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '历史记录加载失败')
    } finally {
      historyLoading.value = false
    }
  }

  async function loadApiKeys() {
    settingsLoading.value = true
    try {
      const { data } = await listApiKeys()
      apiKeys.value = data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥加载失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function executeTask() {
    if (!taskInput.value.trim()) {
      return
    }

    running.value = true
    try {
      const { data } = await runTask(taskInput.value.trim(), taskSourcePromptId.value)
      latestResult.value = data
      taskInput.value = ''
      taskSourcePromptId.value = null
      taskSourcePromptTitle.value = ''
      ElMessage.success('任务执行完成')
      await loadTasks()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '任务执行失败')
    } finally {
      running.value = false
    }
  }

  function prepareTask(input: string, sourcePrompt?: { id: string; title: string } | null) {
    taskInput.value = input
    taskSourcePromptId.value = sourcePrompt?.id || null
    taskSourcePromptTitle.value = sourcePrompt?.title || ''
  }

  function clearTaskSource() {
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
  }

  function loadFlowDrafts() {
    try {
      const rawDrafts = localStorage.getItem(FLOW_DRAFTS_STORAGE_KEY)
      flowDrafts.value = rawDrafts ? JSON.parse(rawDrafts) : []
      activeFlowId.value = localStorage.getItem(ACTIVE_FLOW_STORAGE_KEY) || flowDrafts.value[0]?.id || ''
    } catch {
      flowDrafts.value = []
      activeFlowId.value = ''
    }
  }

  function createFlowDraft(description: string) {
    const trimmedDescription = description.trim()
    if (!trimmedDescription) {
      return null
    }

    const now = new Date().toISOString()
    const title = buildFlowTitle(trimmedDescription)
    const flow: FlowDraft = {
      id: createId(),
      title,
      description: trimmedDescription,
      nodes: createStarterFlowNodes(trimmedDescription),
      createdAt: now,
      updatedAt: now
    }

    flowDrafts.value = [flow, ...flowDrafts.value]
    activeFlowId.value = flow.id
    persistFlowDrafts()
    return flow
  }

  function selectFlowDraft(id: string) {
    activeFlowId.value = id
    localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, id)
  }

  function addPromptToActiveFlow(prompt: PromptAsset) {
    if (!activeFlow.value) {
      return
    }

    const promptNode: FlowNode = {
      id: createId(),
      type: 'prompt',
      title: prompt.title,
      description: prompt.description,
      content: prompt.content,
      promptId: prompt.id,
      promptTitle: prompt.title
    }

    updateActiveFlow((flow) => {
      const outputIndex = flow.nodes.findIndex((node) => node.type === 'output')
      const insertIndex = outputIndex >= 0 ? outputIndex : flow.nodes.length
      flow.nodes.splice(insertIndex, 0, promptNode)
    })
  }

  function removeFlowNode(nodeId: string) {
    updateActiveFlow((flow) => {
      if (flow.nodes.length <= 3) {
        return
      }
      flow.nodes = flow.nodes.filter((node) => node.id !== nodeId)
    })
  }

  function sendFlowToTask() {
    if (!activeFlow.value) {
      return
    }

    const promptBlocks = activeFlow.value.nodes
      .filter((node) => node.type === 'prompt' && node.content)
      .map((node) => `## ${node.title}\n${node.content}`)
      .join('\n\n')

    prepareTask(
      [
        `请按下面的 Flow 目标执行 AI 工作流。`,
        '',
        `Flow: ${activeFlow.value.title}`,
        `目标: ${activeFlow.value.description}`,
        promptBlocks ? `\n可复用 Prompt 节点:\n${promptBlocks}` : '',
        '',
        '请输出：1. Summary 2. Key Points 3. Result 4. Next Actions'
      ].join('\n')
    )
  }

  function updateActiveFlow(mutator: (flow: FlowDraft) => void) {
    const flow = activeFlow.value
    if (!flow) {
      return
    }

    const nextFlow: FlowDraft = {
      ...flow,
      nodes: flow.nodes.map((node) => ({ ...node })),
      updatedAt: new Date().toISOString()
    }
    mutator(nextFlow)
    flowDrafts.value = flowDrafts.value.map((item) => (item.id === nextFlow.id ? nextFlow : item))
    persistFlowDrafts()
  }

  function persistFlowDrafts() {
    localStorage.setItem(FLOW_DRAFTS_STORAGE_KEY, JSON.stringify(flowDrafts.value))
    if (activeFlowId.value) {
      localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, activeFlowId.value)
    }
  }

  async function saveProvider(payload: SaveApiKeyPayload) {
    settingsLoading.value = true
    try {
      await saveApiKey(payload)
      ElMessage.success('API 密钥已保存')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥保存失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function activateProvider(id: string) {
    settingsLoading.value = true
    try {
      await activateApiKey(id)
      ElMessage.success('Provider 已激活')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Provider 激活失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function removeProvider(id: string) {
    settingsLoading.value = true
    try {
      await deleteApiKey(id)
      ElMessage.success('API 密钥已删除')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥删除失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function bootstrap() {
    loadFlowDrafts()
    await Promise.all([loadTasks(), loadApiKeys()])
  }

  return {
    tasks,
    apiKeys,
    flowDrafts,
    activeFlowId,
    latestResult,
    taskInput,
    taskSourcePromptId,
    taskSourcePromptTitle,
    running,
    historyLoading,
    settingsLoading,
    activeProvider,
    activeFlow,
    bootstrap,
    loadTasks,
    loadApiKeys,
    loadFlowDrafts,
    createFlowDraft,
    selectFlowDraft,
    addPromptToActiveFlow,
    removeFlowNode,
    sendFlowToTask,
    executeTask,
    prepareTask,
    clearTaskSource,
    saveProvider,
    activateProvider,
    removeProvider
  }
})

function createStarterFlowNodes(description: string): FlowNode[] {
  return [
    {
      id: createId(),
      type: 'input',
      title: 'Intent',
      description: '用户想完成的 AI 工作',
      content: description
    },
    {
      id: createId(),
      type: 'ai-task',
      title: 'AI Execution',
      description: '调用当前激活的模型执行结构化任务'
    },
    {
      id: createId(),
      type: 'output',
      title: 'Structured Result',
      description: '沉淀 Summary、Key Points、Result 和下一步行动'
    }
  ]
}

function buildFlowTitle(description: string) {
  const firstLine = description.split('\n').find(Boolean) || description
  const title = firstLine.replace(/[。.,，]/g, '').slice(0, 24)
  return title || 'Untitled Flow'
}

function createId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
