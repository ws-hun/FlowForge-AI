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
import { createFlow, listFlows, updateFlow } from '@/api/flows'
import type {
  ApiKeyConfig,
  FlowDraft,
  FlowNode,
  PromptAsset,
  SaveApiKeyPayload,
  SaveFlowPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

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
  const flowLoading = ref(false)

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

  async function loadFlowDrafts() {
    flowLoading.value = true
    try {
      const { data } = await listFlows()
      flowDrafts.value = data

      const storedActiveFlowId = localStorage.getItem(ACTIVE_FLOW_STORAGE_KEY) || ''
      const activeFlowExists = data.some((flow) => flow.id === storedActiveFlowId)
      activeFlowId.value = activeFlowExists ? storedActiveFlowId : data[0]?.id || ''
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 草稿加载失败')
    } finally {
      flowLoading.value = false
    }
  }

  async function createFlowDraft(description: string) {
    const trimmedDescription = description.trim()
    if (!trimmedDescription) {
      return null
    }

    const title = buildFlowTitle(trimmedDescription)
    const payload: SaveFlowPayload = {
      title,
      description: trimmedDescription,
      nodes: createStarterFlowNodes(trimmedDescription)
    }

    flowLoading.value = true
    try {
      const { data } = await createFlow(payload)
      flowDrafts.value = [data, ...flowDrafts.value]
      activeFlowId.value = data.id
      localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, data.id)
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 草稿创建失败')
      return null
    } finally {
      flowLoading.value = false
    }
  }

  function selectFlowDraft(id: string) {
    activeFlowId.value = id
    localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, id)
  }

  async function addPromptToActiveFlow(prompt: PromptAsset) {
    if (!activeFlow.value) {
      return null
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

    const updatedFlow = await updateActiveFlow((flow) => {
      const outputIndex = flow.nodes.findIndex((node) => node.type === 'output')
      const insertIndex = outputIndex >= 0 ? outputIndex : flow.nodes.length
      flow.nodes.splice(insertIndex, 0, promptNode)
    })
    return updatedFlow ? promptNode : null
  }

  async function removeFlowNode(nodeId: string) {
    await updateActiveFlow((flow) => {
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

  async function updateActiveFlow(mutator: (flow: FlowDraft) => void) {
    const flow = activeFlow.value
    if (!flow) {
      return null
    }

    const nextFlow: FlowDraft = {
      ...flow,
      nodes: flow.nodes.map((node) => ({ ...node })),
      updatedAt: new Date().toISOString()
    }
    mutator(nextFlow)

    flowLoading.value = true
    try {
      const { data } = await updateFlow(nextFlow.id, toSaveFlowPayload(nextFlow))
      flowDrafts.value = flowDrafts.value.map((item) => (item.id === data.id ? data : item))
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 草稿保存失败')
      return null
    } finally {
      flowLoading.value = false
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
    await Promise.all([loadTasks(), loadApiKeys(), loadFlowDrafts()])
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
    flowLoading,
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

function toSaveFlowPayload(flow: FlowDraft): SaveFlowPayload {
  return {
    title: flow.title,
    description: flow.description,
    nodes: flow.nodes
  }
}

function createId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
