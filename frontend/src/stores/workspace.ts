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
import { createFlow, deleteFlow, listFlows, updateFlow } from '@/api/flows'
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
  const taskSourceFlowId = ref<string | null>(null)
  const taskSourceFlowTitle = ref('')
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
      const { data } = await runTask(taskInput.value.trim(), taskSourcePromptId.value, taskSourceFlowId.value)
      latestResult.value = data
      taskInput.value = ''
      taskSourcePromptId.value = null
      taskSourcePromptTitle.value = ''
      taskSourceFlowId.value = null
      taskSourceFlowTitle.value = ''
      ElMessage.success('任务执行完成')
      await loadTasks()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '任务执行失败')
    } finally {
      running.value = false
    }
  }

  function prepareTask(
    input: string,
    sourcePrompt?: { id: string; title: string } | null,
    sourceFlow?: { id: string; title: string } | null
  ) {
    taskInput.value = input
    taskSourcePromptId.value = sourcePrompt?.id || null
    taskSourcePromptTitle.value = sourcePrompt?.title || ''
    taskSourceFlowId.value = sourceFlow?.id || null
    taskSourceFlowTitle.value = sourceFlow?.title || ''
  }

  function clearTaskSource() {
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
    taskSourceFlowId.value = null
    taskSourceFlowTitle.value = ''
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
      const aiTaskIndex = flow.nodes.findIndex((node) => node.type === 'ai-task')
      const outputIndex = flow.nodes.findIndex((node) => node.type === 'output')
      const insertIndex = aiTaskIndex >= 0 ? aiTaskIndex : outputIndex >= 0 ? outputIndex : flow.nodes.length
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

  async function updateFlowNode(nodeId: string, patch: Pick<FlowNode, 'title' | 'description'> & { content?: string }) {
    const cleanTitle = patch.title.trim()
    const cleanDescription = patch.description.trim()

    if (!cleanTitle || !cleanDescription) {
      ElMessage.warning('请补全节点标题和说明')
      return null
    }

    return updateActiveFlow((flow) => {
      const targetNode = flow.nodes.find((node) => node.id === nodeId)
      if (!targetNode) {
        return
      }

      targetNode.title = cleanTitle
      targetNode.description = cleanDescription
      if (typeof patch.content === 'string') {
        targetNode.content = patch.content.trim()
      }
    })
  }

  async function moveFlowPromptNode(nodeId: string, direction: 'up' | 'down') {
    return updateActiveFlow((flow) => {
      const promptNodes = flow.nodes.filter((node) => node.type === 'prompt')
      const currentIndex = promptNodes.findIndex((node) => node.id === nodeId)
      const nextIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1

      if (currentIndex < 0 || nextIndex < 0 || nextIndex >= promptNodes.length) {
        return
      }

      const currentNode = promptNodes[currentIndex]
      promptNodes[currentIndex] = promptNodes[nextIndex]
      promptNodes[nextIndex] = currentNode
      flow.nodes = [
        ...flow.nodes.filter((node) => node.type === 'input'),
        ...promptNodes,
        ...flow.nodes.filter((node) => node.type === 'ai-task'),
        ...flow.nodes.filter((node) => node.type === 'output')
      ]
    })
  }

  async function updateFlowMeta(title: string, description: string) {
    const cleanTitle = title.trim()
    const cleanDescription = description.trim()

    if (!cleanTitle || !cleanDescription) {
      ElMessage.warning('请补全 Flow 标题和目标')
      return null
    }

    return updateActiveFlow((flow) => {
      flow.title = cleanTitle
      flow.description = cleanDescription
      const intentNode = flow.nodes.find((node) => node.type === 'input')
      if (intentNode) {
        intentNode.content = cleanDescription
        intentNode.description = '用户想完成的 AI 工作'
      }
    })
  }

  async function deleteFlowDraft(id: string) {
    flowLoading.value = true
    try {
      await deleteFlow(id)
      flowDrafts.value = flowDrafts.value.filter((flow) => flow.id !== id)
      if (activeFlowId.value === id) {
        activeFlowId.value = flowDrafts.value[0]?.id || ''
        if (activeFlowId.value) {
          localStorage.setItem(ACTIVE_FLOW_STORAGE_KEY, activeFlowId.value)
        } else {
          localStorage.removeItem(ACTIVE_FLOW_STORAGE_KEY)
        }
      }
      return true
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 草稿删除失败')
      return false
    } finally {
      flowLoading.value = false
    }
  }

  function composeActiveFlowInput(runtimeContext = '') {
    if (!activeFlow.value) {
      return ''
    }

    return buildFlowTaskInput(activeFlow.value, runtimeContext)
  }

  function sendFlowToTask(runtimeContext = '') {
    if (!activeFlow.value) {
      return
    }

    prepareTask(
      buildFlowTaskInput(activeFlow.value, runtimeContext),
      null,
      { id: activeFlow.value.id, title: activeFlow.value.title }
    )
  }

  async function executeActiveFlow(runtimeContext = '') {
    if (!activeFlow.value) {
      return null
    }

    running.value = true
    try {
      const { data } = await runTask(buildFlowTaskInput(activeFlow.value, runtimeContext), null, activeFlow.value.id)
      latestResult.value = data
      ElMessage.success('Flow 执行完成')
      await loadTasks()
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 执行失败')
      return null
    } finally {
      running.value = false
    }
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
    taskSourceFlowId,
    taskSourceFlowTitle,
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
    updateFlowNode,
    moveFlowPromptNode,
    updateFlowMeta,
    deleteFlowDraft,
    composeActiveFlowInput,
    sendFlowToTask,
    executeActiveFlow,
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

function buildFlowTaskInput(flow: FlowDraft, runtimeContext = '') {
  const inputBlocks = flow.nodes
    .filter((node) => node.type === 'input' && node.content && node.content.trim() !== flow.description.trim())
    .map((node) => `## ${node.title}\n${node.content}`)
    .join('\n\n')
  const promptBlocks = flow.nodes
    .filter((node) => node.type === 'prompt' && node.content)
    .map((node) => `## ${node.title}\n${node.content}`)
    .join('\n\n')
  const cleanRuntimeContext = runtimeContext.trim()

  return [
    `请按下面的 Flow 目标执行 AI 工作流。`,
    '',
    `Flow: ${flow.title}`,
    `目标: ${flow.description}`,
    inputBlocks ? `\n输入节点上下文:\n${inputBlocks}` : '',
    cleanRuntimeContext ? `\n本次运行上下文:\n${cleanRuntimeContext}` : '',
    promptBlocks ? `\n可复用 Prompt 节点:\n${promptBlocks}` : '',
    '',
    '请输出：1. Summary 2. Key Points 3. Result 4. Next Actions'
  ].join('\n')
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
