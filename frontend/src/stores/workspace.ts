import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  activateApiKey,
  deleteApiKey,
  listApiKeys,
  listTasks,
  rerunTask as rerunTaskRequest,
  runTask,
  saveApiKey,
  testApiKey
} from '@/api/tasks'
import { createFlow, deleteFlow, listFlows, restoreFlowVersion, updateFlow } from '@/api/flows'
import { createPrompt } from '@/api/prompts'
import { persistAiCommandDraft, readAiCommandDraft } from '@/utils/aiCommandDraft'
import { persistActiveFlowId, readActiveFlowId, resolveActiveFlowId } from '@/utils/flowSelection'
import { canPersistFlowContext, createFlowContextNode } from '@/utils/flowContext'
import {
  persistFlowRunDrafts,
  readFlowRunDrafts,
  removeFlowRunDraft,
  upsertFlowRunDraft
} from '@/utils/flowRunDrafts'
import { extractPromptVariables, isValidPromptVariableName, renamePromptVariable } from '@/utils/promptVariables'
import {
  normalizeWorkspacePreferences,
  persistWorkspacePreferences,
  readWorkspacePreferences
} from '@/utils/workspacePreferences'
import type { AiCommandDraft } from '@/utils/aiCommandDraft'
import type { FlowRunDraft } from '@/utils/flowRunDrafts'
import type { WorkspacePreferences } from '@/utils/workspacePreferences'
import type {
  ApiKeyConfig,
  FlowDraft,
  FlowNode,
  FlowRunSnapshot,
  FlowVersion,
  PromptAsset,
  ProviderConnectionTestResponse,
  SaveApiKeyPayload,
  SaveFlowPayload,
  SavePromptPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

const DEFAULT_AI_TASK_EXECUTION_GUIDANCE =
  '综合上游上下文与 Prompt，给出清晰、可执行的结构化结果。\n优先保留关键判断、行动建议和必要的边界条件。'
const DEFAULT_OUTPUT_DELIVERY_FOCUS =
  '以清晰的 Summary、Key Points、Result 和 Next Actions 交付。\n结果应便于复盘、分享，并可作为下一轮工作的可靠起点。'

type TaskFlowSource = {
  id: string
  title: string
  variableValues?: Record<string, string>
}

type FlowRunSeed = {
  flowId: string
  runtimeContext: string
  variableValues: Record<string, string>
}

type WorkspacePreferenceUpdateResult = 'saved' | 'memory-only' | 'invalid'

export const useWorkspaceStore = defineStore('workspace', () => {
  let bootstrapPromise: Promise<void> | null = null
  let bootstrapped = false
  const initialTaskDraft = readAiCommandDraft()
  const tasks = ref<TaskHistoryItem[]>([])
  const apiKeys = ref<ApiKeyConfig[]>([])
  const flowDrafts = ref<FlowDraft[]>([])
  const activeFlowId = ref('')
  const latestResult = ref<TaskRunResponse | null>(null)
  const latestTaskInput = ref('')
  const latestTaskPrompt = ref<PromptAsset | null>(null)
  const taskPromptsByRunId = ref<Record<string, PromptAsset>>({})
  const taskInput = ref(initialTaskDraft?.input || '')
  const taskSourcePromptId = ref<string | null>(initialTaskDraft?.sourcePromptId || null)
  const taskSourcePromptTitle = ref(initialTaskDraft?.sourcePromptTitle || '')
  const taskSourceFlowId = ref<string | null>(initialTaskDraft?.sourceFlowId || null)
  const taskSourceFlowTitle = ref(initialTaskDraft?.sourceFlowTitle || '')
  const taskSourceFlowVariableValues = ref<Record<string, string>>({
    ...(initialTaskDraft?.sourceFlowVariableValues || {})
  })
  const taskSourceRunId = ref<string | null>(initialTaskDraft?.sourceRunId || null)
  const taskSourceRunSummary = ref(initialTaskDraft?.sourceRunSummary || '')
  const taskInputVariantOfTaskId = ref<string | null>(initialTaskDraft?.inputVariantOfTaskId || null)
  const taskInputVariantSourceTitle = ref(initialTaskDraft?.inputVariantSourceTitle || '')
  const taskDraftRecovered = ref(Boolean(initialTaskDraft))
  const pendingFlowRunSeed = ref<FlowRunSeed | null>(null)
  const flowRunDrafts = ref<Record<string, FlowRunDraft>>(readFlowRunDrafts())
  const workspacePreferences = ref<WorkspacePreferences>(readWorkspacePreferences())
  const workspacePreferencesPersisted = ref(true)
  const running = ref(false)
  const historyLoading = ref(false)
  const settingsLoading = ref(false)
  const providerTestLoadingId = ref('')
  const providerConnectionChecks = ref<Record<string, ProviderConnectionTestResponse>>({})
  const flowLoading = ref(false)
  const flowAssetsReady = ref(false)
  const flowConflictId = ref('')
  const taskAssetLoading = ref(false)

  const activeProvider = computed(() => apiKeys.value.find((item) => item.active))
  const activeFlow = computed(() => flowDrafts.value.find((flow) => flow.id === activeFlowId.value) || null)
  const workspaceName = computed(() => workspacePreferences.value.workspaceName)
  const profileName = computed(() => workspacePreferences.value.profileName)
  const profileInitial = computed(() => Array.from(profileName.value.trim())[0]?.toUpperCase() || 'A')
  const canPromoteLatestTask = computed(() =>
    Boolean(latestResult.value && latestTaskInput.value.trim() && !taskSourceRunId.value)
  )
  const taskSourceFlowVariables = computed(() => Object.keys(taskSourceFlowVariableValues.value))
  const missingTaskSourceFlowVariables = computed(() =>
    taskSourceFlowVariables.value.filter((variable) => !taskSourceFlowVariableValues.value[variable]?.trim())
  )
  const canExecuteTask = computed(() =>
    taskSourceFlowId.value ? missingTaskSourceFlowVariables.value.length === 0 : Boolean(taskInput.value.trim())
  )

  watch(
    [
      taskInput,
      taskSourcePromptId,
      taskSourcePromptTitle,
      taskSourceFlowId,
      taskSourceFlowTitle,
      taskSourceFlowVariableValues,
      taskSourceRunId,
      taskSourceRunSummary,
      taskInputVariantOfTaskId,
      taskInputVariantSourceTitle
    ],
    () => persistAiCommandDraft(captureAiCommandDraft()),
    { deep: true }
  )

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

  function updateWorkspacePreferences(
    nextPreferences: WorkspacePreferences
  ): WorkspacePreferenceUpdateResult {
    const normalizedPreferences = normalizeWorkspacePreferences(nextPreferences)
    if (!normalizedPreferences) {
      return 'invalid'
    }
    workspacePreferences.value = normalizedPreferences
    workspacePreferencesPersisted.value = persistWorkspacePreferences(normalizedPreferences)
    return workspacePreferencesPersisted.value ? 'saved' : 'memory-only'
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
    const isFlowRun = Boolean(taskSourceFlowId.value)
    const input = taskInput.value.trim()

    if (!isFlowRun && !input) {
      return
    }

    if (!activeProvider.value) {
      ElMessage.warning('请先配置并激活 AI Provider')
      return
    }

    if (isFlowRun && missingTaskSourceFlowVariables.value.length) {
      ElMessage.warning(`请先填写 Flow 变量：${missingTaskSourceFlowVariables.value.join('、')}`)
      return
    }

    saveTaskSourceFlowRunDraft()

    running.value = true
    try {
      const { data } = await runTask({
        input,
        promptId: taskSourcePromptId.value,
        flowId: taskSourceFlowId.value,
        flowRunContext: isFlowRun ? input : undefined,
        flowVariableValues: taskSourceFlowVariableValues.value,
        continuedFromTaskId: taskSourceRunId.value,
        inputVariantOfTaskId: taskInputVariantOfTaskId.value
      })
      latestResult.value = data
      latestTaskInput.value = data.executionInput
      latestTaskPrompt.value = null
      clearTaskSource()
      taskInput.value = ''
      ElMessage.success('任务执行完成')
      await loadTasks()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '任务执行失败')
      await loadTasks()
    } finally {
      running.value = false
    }
  }

  async function rerunHistoricalTask(taskId: string) {
    if (!activeProvider.value) {
      ElMessage.warning('请先配置并激活 AI Provider')
      return null
    }

    running.value = true
    try {
      const { data } = await rerunTaskRequest(taskId)
      latestResult.value = data
      latestTaskInput.value = data.executionInput
      latestTaskPrompt.value = null
      clearTaskSource()
      taskInput.value = ''
      ElMessage.success('已使用当前 Provider 重新执行')
      await loadTasks()
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '历史任务重新执行失败')
      await loadTasks()
      return null
    } finally {
      running.value = false
    }
  }

  function prepareTask(
    input: string,
    sourcePrompt?: { id: string; title: string } | null,
    sourceFlow?: TaskFlowSource | null
  ) {
    saveTaskSourceFlowRunDraft()
    taskDraftRecovered.value = false
    taskInput.value = input
    taskSourcePromptId.value = sourcePrompt?.id || null
    taskSourcePromptTitle.value = sourcePrompt?.title || ''
    taskSourceFlowId.value = sourceFlow?.id || null
    taskSourceFlowTitle.value = sourceFlow?.title || ''
    taskSourceFlowVariableValues.value = { ...(sourceFlow?.variableValues || {}) }
    taskSourceRunId.value = null
    taskSourceRunSummary.value = ''
    taskInputVariantOfTaskId.value = null
    taskInputVariantSourceTitle.value = ''
  }

  function prepareTaskContinuation(sourceRun: TaskHistoryItem) {
    saveTaskSourceFlowRunDraft()
    taskDraftRecovered.value = false
    taskInput.value = ''
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
    taskSourceFlowId.value = null
    taskSourceFlowTitle.value = ''
    taskSourceFlowVariableValues.value = {}
    taskSourceRunId.value = sourceRun.id
    taskSourceRunSummary.value = sourceRun.summary
    taskInputVariantOfTaskId.value = null
    taskInputVariantSourceTitle.value = ''
    latestResult.value = {
      summary: sourceRun.summary,
      result: sourceRun.result,
      raw: '',
      provider: sourceRun.provider,
      model: sourceRun.model,
      inputTokens: sourceRun.inputTokens,
      outputTokens: sourceRun.outputTokens,
      totalTokens: sourceRun.totalTokens,
      durationMs: sourceRun.durationMs,
      rerunOfTaskId: sourceRun.rerunOfTaskId,
      continuedFromTaskId: sourceRun.continuedFromTaskId,
      inputVariantOfTaskId: sourceRun.inputVariantOfTaskId,
      executionInput: sourceRun.input,
      taskId: sourceRun.id,
      flowRunSnapshot: sourceRun.flowRunSnapshot || null,
      flowRunTrace: sourceRun.flowRunTrace || null
    }
    latestTaskInput.value = sourceRun.input
    latestTaskPrompt.value = null
  }

  function prepareTaskInputVariant(sourceRun: TaskHistoryItem) {
    saveTaskSourceFlowRunDraft()
    taskDraftRecovered.value = false
    taskInput.value = sourceRun.input
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
    taskSourceFlowId.value = null
    taskSourceFlowTitle.value = ''
    taskSourceFlowVariableValues.value = {}
    taskSourceRunId.value = null
    taskSourceRunSummary.value = ''
    taskInputVariantOfTaskId.value = sourceRun.id
    taskInputVariantSourceTitle.value =
      sourceRun.sourceFlowTitle || sourceRun.sourcePromptTitle || sourceRun.summary || 'Historical input'
    latestResult.value = null
    latestTaskInput.value = ''
    latestTaskPrompt.value = null
  }

  function prepareLatestResultContinuation() {
    const taskId = latestResult.value?.taskId
    if (!taskId) {
      ElMessage.warning('当前结果还没有可复用的运行记录')
      return null
    }

    const sourceRun = tasks.value.find((task) => task.id === taskId)
    if (!sourceRun || sourceRun.status === 'failed') {
      ElMessage.warning('当前结果暂时无法继续')
      return null
    }

    prepareTaskContinuation(sourceRun)
    return sourceRun
  }

  function clearTaskSource() {
    saveTaskSourceFlowRunDraft()
    taskDraftRecovered.value = false
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
    taskSourceFlowId.value = null
    taskSourceFlowTitle.value = ''
    taskSourceFlowVariableValues.value = {}
    taskSourceRunId.value = null
    taskSourceRunSummary.value = ''
    taskInputVariantOfTaskId.value = null
    taskInputVariantSourceTitle.value = ''
  }

  function acknowledgeTaskDraftRecovery() {
    taskDraftRecovered.value = false
  }

  function captureAiCommandDraft(): AiCommandDraft | null {
    const hasSource = Boolean(
      taskSourcePromptId.value ||
        taskSourceFlowId.value ||
        taskSourceRunId.value ||
        taskInputVariantOfTaskId.value
    )
    if (!taskInput.value.trim() && !hasSource) {
      return null
    }

    return {
      input: taskInput.value,
      sourcePromptId: taskSourcePromptId.value,
      sourcePromptTitle: taskSourcePromptTitle.value,
      sourceFlowId: taskSourceFlowId.value,
      sourceFlowTitle: taskSourceFlowTitle.value,
      sourceFlowVariableValues: { ...taskSourceFlowVariableValues.value },
      sourceRunId: taskSourceRunId.value,
      sourceRunSummary: taskSourceRunSummary.value,
      inputVariantOfTaskId: taskInputVariantOfTaskId.value,
      inputVariantSourceTitle: taskInputVariantSourceTitle.value,
      updatedAt: new Date().toISOString()
    }
  }

  function reconcileAiCommandDraftSource() {
    let detached = false
    if (taskSourceFlowId.value && !flowDrafts.value.some((flow) => flow.id === taskSourceFlowId.value)) {
      taskSourceFlowId.value = null
      taskSourceFlowTitle.value = ''
      taskSourceFlowVariableValues.value = {}
      detached = true
    }
    if (taskSourceRunId.value && !tasks.value.some((task) => task.id === taskSourceRunId.value)) {
      taskSourceRunId.value = null
      taskSourceRunSummary.value = ''
      detached = true
    }
    if (
      taskInputVariantOfTaskId.value &&
      !tasks.value.some((task) => task.id === taskInputVariantOfTaskId.value)
    ) {
      taskInputVariantOfTaskId.value = null
      taskInputVariantSourceTitle.value = ''
      detached = true
    }
    if (detached) {
      persistAiCommandDraft(captureAiCommandDraft())
      ElMessage.warning('AI Command 草稿的原始来源已不存在，现有输入已保留为独立任务')
    }
  }

  function saveTaskSourceFlowRunDraft() {
    const flowId = taskSourceFlowId.value
    if (!flowId) {
      return
    }
    saveFlowRunDraft(flowId, taskInput.value, taskSourceFlowVariableValues.value)
  }

  async function saveLatestTaskAsPrompt() {
    if (!latestResult.value || !latestTaskInput.value.trim()) {
      ElMessage.warning('请先完成一次 AI Command 执行')
      return null
    }

    if (latestTaskPrompt.value) {
      return latestTaskPrompt.value
    }

    const payload: SavePromptPayload = {
      title: buildTaskPromptTitle(latestTaskInput.value),
      category: 'AI Command',
      description: buildTaskPromptDescription(latestResult.value.summary),
      content: latestTaskInput.value.trim(),
      tags: ['AI Command', 'Task', 'Reusable'],
      favorite: false,
      sourceTaskId: latestResult.value.taskId || null
    }

    taskAssetLoading.value = true
    try {
      const { data } = await createPrompt(payload)
      latestTaskPrompt.value = data
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Prompt 沉淀失败')
      return null
    } finally {
      taskAssetLoading.value = false
    }
  }

  async function createFlowFromLatestTask() {
    const prompt = await saveLatestTaskAsPrompt()
    if (!prompt) {
      return null
    }

    return createFlowFromPrompt(prompt)
  }

  async function saveHistoricalResultAsPrompt(sourceRun: TaskHistoryItem) {
    if (sourceRun.status === 'failed') {
      ElMessage.warning('失败运行不能沉淀为 Prompt')
      return null
    }

    const existingPrompt = taskPromptsByRunId.value[sourceRun.id]
    if (existingPrompt) {
      return existingPrompt
    }

    const payload: SavePromptPayload = {
      title: buildHistoricalResultPromptTitle(sourceRun),
      category: sourceRun.sourceFlowId ? 'Flow Result' : 'AI Result',
      description: buildHistoricalResultPromptDescription(sourceRun),
      content: buildHistoricalResultPromptContent(sourceRun),
      tags: ['Result', 'History', 'Reusable'],
      favorite: false,
      sourceTaskId: sourceRun.id
    }

    taskAssetLoading.value = true
    try {
      const { data } = await createPrompt(payload)
      taskPromptsByRunId.value = {
        ...taskPromptsByRunId.value,
        [sourceRun.id]: data
      }
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '历史结果沉淀失败')
      return null
    } finally {
      taskAssetLoading.value = false
    }
  }

  async function createFlowFromHistoricalResult(sourceRun: TaskHistoryItem) {
    const prompt = await saveHistoricalResultAsPrompt(sourceRun)
    if (!prompt) {
      return null
    }
    return createFlowFromPrompt(prompt)
  }

  async function loadFlowDrafts() {
    flowLoading.value = true
    try {
      const { data } = await listFlows()
      flowDrafts.value = data
      flowAssetsReady.value = true

      activeFlowId.value = resolveActiveFlowId(data.map((flow) => flow.id), readActiveFlowId())
      persistActiveFlowId(activeFlowId.value)
    } catch (error: any) {
      flowAssetsReady.value = false
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

    return persistNewFlowDraft(payload, 'Flow 草稿创建失败')
  }

  async function createFlowFromTemplate(
    title: string,
    description: string,
    promptNodes: Array<Pick<FlowNode, 'title' | 'description' | 'content'>>
  ) {
    const cleanTitle = title.trim()
    const cleanDescription = description.trim()

    if (!cleanTitle || !cleanDescription) {
      ElMessage.warning('请补全 Flow 模板标题和目标')
      return null
    }

    const payload: SaveFlowPayload = {
      title: cleanTitle,
      description: cleanDescription,
      nodes: createTemplatedFlowNodes(cleanDescription, promptNodes)
    }

    return persistNewFlowDraft(payload, 'Flow 模板创建失败')
  }

  async function createFlowFromPrompt(prompt: PromptAsset) {
    const cleanTitle = prompt.title.trim()
    const cleanDescription = prompt.description.trim()
    const cleanContent = prompt.content.trim()

    if (!cleanTitle || !cleanDescription || !cleanContent) {
      ElMessage.warning('Prompt 信息不完整，暂时无法创建 Flow')
      return null
    }

    const payload: SaveFlowPayload = {
      title: buildPromptFlowTitle(cleanTitle),
      description: cleanDescription,
      nodes: createPromptBasedFlowNodes(prompt)
    }

    return persistNewFlowDraft(payload, '从 Prompt 创建 Flow 失败')
  }

  async function createFlowFromRunSnapshot(snapshot: FlowRunSnapshot) {
    const description = snapshot.description.trim()
    if (!description || !snapshot.nodes.length) {
      ElMessage.warning('这个运行快照不完整，暂时无法创建 Flow')
      return null
    }

    const sourceFlow = flowDrafts.value.find((flow) => flow.id === snapshot.flowId)
    const payload: SaveFlowPayload = {
      title: buildFlowSnapshotTitle(snapshot.title),
      description,
      nodes: snapshot.nodes.map((node) => ({
        ...node,
        id: createId()
      })),
      sourceFlowId: sourceFlow?.id || null
    }

    return persistNewFlowDraft(payload, '从运行快照创建 Flow 失败', (flow) => {
      pendingFlowRunSeed.value = {
        flowId: flow.id,
        runtimeContext: snapshot.runtimeContext.trim(),
        variableValues: { ...snapshot.variableValues }
      }
    })
  }

  async function createFlowFromRevision(version: FlowVersion) {
    const sourceFlow = flowDrafts.value.find((flow) => flow.id === version.flowId)
    if (!sourceFlow || !version.nodes.length) {
      ElMessage.warning('这个 Flow 修订已不可用，暂时无法创建变体')
      return null
    }

    const payload: SaveFlowPayload = {
      title: buildFlowRevisionTitle(version.title),
      description: version.description,
      nodes: version.nodes.map((node) => ({
        ...node,
        id: createId()
      })),
      sourceFlowId: sourceFlow.id,
      sourceFlowVersionId: version.id
    }

    return persistNewFlowDraft(payload, '从 Flow 修订创建变体失败')
  }

  async function createFlowFromRecoveredEditor(
    snapshot: Pick<SaveFlowPayload, 'title' | 'description' | 'nodes'>,
    sourceFlowId?: string | null
  ) {
    const title = snapshot.title.trim()
    const description = snapshot.description.trim()
    if (!title || !description || !snapshot.nodes.length) {
      ElMessage.warning('本地 Flow 草稿不完整，暂时无法创建恢复副本')
      return null
    }

    const sourceStillAvailable = Boolean(
      sourceFlowId && flowDrafts.value.some((flow) => flow.id === sourceFlowId)
    )
    const payload: SaveFlowPayload = {
      title: buildFlowRecoveryTitle(title),
      description,
      nodes: snapshot.nodes.map((node) => ({
        ...node,
        id: createId()
      })),
      sourceFlowId: sourceStillAvailable ? sourceFlowId : null
    }
    return persistNewFlowDraft(payload, 'Flow 恢复副本创建失败')
  }

  function consumeFlowRunSeed(flowId: string) {
    if (pendingFlowRunSeed.value?.flowId !== flowId) {
      return null
    }

    const seed = pendingFlowRunSeed.value
    pendingFlowRunSeed.value = null
    return seed
  }

  function getFlowRunDraft(flowId: string) {
    const draft = flowRunDrafts.value[flowId]
    return draft
      ? {
          runtimeContext: draft.runtimeContext,
          variableValues: { ...draft.variableValues },
          updatedAt: draft.updatedAt
        }
      : null
  }

  function saveFlowRunDraft(flowId: string, runtimeContext: string, variableValues: Record<string, string>) {
    const nextDrafts = upsertFlowRunDraft(flowRunDrafts.value, flowId, runtimeContext, variableValues)
    if (nextDrafts === flowRunDrafts.value) {
      return
    }
    flowRunDrafts.value = nextDrafts
    persistFlowRunDrafts(flowRunDrafts.value)
  }

  function clearFlowRunDraft(flowId: string) {
    const nextDrafts = removeFlowRunDraft(flowRunDrafts.value, flowId)
    if (nextDrafts === flowRunDrafts.value) {
      return
    }
    flowRunDrafts.value = nextDrafts
    persistFlowRunDrafts(nextDrafts)
  }

  function prepareFlowRunFromSnapshot(snapshot: FlowRunSnapshot) {
    const sourceFlow = flowDrafts.value.find((flow) => flow.id === snapshot.flowId)
    if (!sourceFlow) {
      return false
    }

    activeFlowId.value = sourceFlow.id
    persistActiveFlowId(sourceFlow.id)
    pendingFlowRunSeed.value = {
      flowId: sourceFlow.id,
      runtimeContext: snapshot.runtimeContext?.trim() || '',
      variableValues: { ...(snapshot.variableValues || {}) }
    }
    return true
  }

  async function persistNewFlowDraft(
    payload: SaveFlowPayload,
    errorMessage: string,
    beforeActivate?: (flow: FlowDraft) => void
  ) {
    flowLoading.value = true
    try {
      const { data } = await createFlow(payload)
      beforeActivate?.(data)
      flowDrafts.value = [data, ...flowDrafts.value]
      activeFlowId.value = data.id
      persistActiveFlowId(data.id)
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || errorMessage)
      return null
    } finally {
      flowLoading.value = false
    }
  }

  function selectFlowDraft(id: string) {
    activeFlowId.value = id
    persistActiveFlowId(id)
  }

  function replaceFlowDraft(flow: FlowDraft) {
    const exists = flowDrafts.value.some((item) => item.id === flow.id)
    flowDrafts.value = exists
      ? flowDrafts.value.map((item) => (item.id === flow.id ? flow : item))
      : [flow, ...flowDrafts.value]
    if (flowConflictId.value === flow.id) {
      flowConflictId.value = ''
    }
  }

  function dismissFlowConflict() {
    flowConflictId.value = ''
  }

  async function duplicateActiveFlowDraft() {
    if (!activeFlow.value) {
      return null
    }

    const payload: SaveFlowPayload = {
      title: buildFlowCopyTitle(activeFlow.value.title),
      description: activeFlow.value.description,
      nodes: activeFlow.value.nodes.map((node) => ({
        ...node,
        id: createId()
      })),
      sourceFlowId: activeFlow.value.id
    }

    return persistNewFlowDraft(payload, 'Flow 变体创建失败')
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

  async function addContextToActiveFlow(content = '') {
    if (!activeFlow.value) {
      return null
    }
    if (content.trim() && !canPersistFlowContext(content)) {
      return null
    }

    const contextNode = createFlowContextNode(createId(), content)

    const updatedFlow = await updateActiveFlow((flow) => {
      const firstNonInputIndex = flow.nodes.findIndex((node) => node.type !== 'input')
      const insertIndex = firstNonInputIndex >= 0 ? firstNonInputIndex : flow.nodes.length
      flow.nodes.splice(insertIndex, 0, contextNode)
    })
    return updatedFlow ? contextNode : null
  }

  async function removeFlowNode(nodeId: string) {
    await updateActiveFlow((flow) => {
      const targetNode = flow.nodes.find((node) => node.id === nodeId)
      const primaryInputNode = flow.nodes.find((node) => node.type === 'input')
      const canRemove =
        targetNode &&
        (targetNode.type === 'prompt' ||
          (targetNode.type === 'input' && targetNode.id !== primaryInputNode?.id))

      if (!canRemove) {
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

    return updateActiveFlow((flow) => applyFlowNodePatch(flow, nodeId, cleanTitle, cleanDescription, patch.content))
  }

  async function syncFlowPromptNode(nodeId: string, prompt: PromptAsset) {
    return updateActiveFlow((flow) => {
      const targetNode = flow.nodes.find((node) => node.id === nodeId && node.type === 'prompt')
      if (!targetNode) {
        return
      }

      targetNode.title = prompt.title
      targetNode.description = prompt.description
      targetNode.content = prompt.content
      targetNode.promptId = prompt.id
      targetNode.promptTitle = prompt.title
    })
  }

  async function renameFlowVariable(currentName: string, nextName: string) {
    const sourceName = currentName.trim()
    const targetName = nextName.trim()
    if (!activeFlow.value || !isValidPromptVariableName(sourceName) || !isValidPromptVariableName(targetName)) {
      ElMessage.warning('变量名仅支持中文、字母、数字、下划线和连字符')
      return null
    }

    if (sourceName === targetName) {
      return activeFlow.value
    }

    const targetAlreadyExists = activeFlow.value.nodes.some((node) =>
      extractPromptVariables(node.content || '').includes(targetName)
    )
    if (targetAlreadyExists) {
      ElMessage.warning(`Flow 中已存在变量 {${targetName}}`)
      return null
    }

    return updateActiveFlow((flow) => {
      flow.nodes.forEach((node) => {
        if (node.content) {
          node.content = renamePromptVariable(node.content, sourceName, targetName)
        }
      })
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

  async function moveFlowContextNode(nodeId: string, direction: 'up' | 'down') {
    return updateActiveFlow((flow) => {
      const inputNodes = flow.nodes.filter((node) => node.type === 'input')
      const [primaryInputNode, ...contextNodes] = inputNodes
      const currentIndex = contextNodes.findIndex((node) => node.id === nodeId)
      const nextIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1

      if (!primaryInputNode || currentIndex < 0 || nextIndex < 0 || nextIndex >= contextNodes.length) {
        return
      }

      const currentNode = contextNodes[currentIndex]
      contextNodes[currentIndex] = contextNodes[nextIndex]
      contextNodes[nextIndex] = currentNode
      flow.nodes = [
        primaryInputNode,
        ...contextNodes,
        ...flow.nodes.filter((node) => node.type === 'prompt'),
        ...flow.nodes.filter((node) => node.type === 'ai-task'),
        ...flow.nodes.filter((node) => node.type === 'output')
      ]
    })
  }

  async function duplicateFlowPromptNode(nodeId: string) {
    if (!activeFlow.value) {
      return null
    }

    const sourceNode = activeFlow.value.nodes.find((node) => node.id === nodeId && node.type === 'prompt')
    if (!sourceNode) {
      return null
    }

    const duplicatedNode: FlowNode = {
      ...sourceNode,
      id: createId(),
      title: `${sourceNode.title} Copy`
    }

    const updatedFlow = await updateActiveFlow((flow) => {
      const sourceIndex = flow.nodes.findIndex((node) => node.id === nodeId)
      if (sourceIndex < 0) {
        return
      }
      flow.nodes.splice(sourceIndex + 1, 0, duplicatedNode)
    })

    return updatedFlow ? duplicatedNode : null
  }

  async function updateFlowMeta(title: string, description: string) {
    const cleanTitle = title.trim()
    const cleanDescription = description.trim()

    if (!cleanTitle || !cleanDescription) {
      ElMessage.warning('请补全 Flow 标题和目标')
      return null
    }

    return updateActiveFlow((flow) => applyFlowMetaPatch(flow, cleanTitle, cleanDescription))
  }

  async function updateFlowMetaAndNode(
    title: string,
    description: string,
    nodeId: string,
    nodePatch: Pick<FlowNode, 'title' | 'description'> & { content?: string }
  ) {
    const cleanTitle = title.trim()
    const cleanDescription = description.trim()
    const cleanNodeTitle = nodePatch.title.trim()
    const cleanNodeDescription = nodePatch.description.trim()

    if (!cleanTitle || !cleanDescription) {
      ElMessage.warning('请补全 Flow 标题和目标')
      return null
    }
    if (!cleanNodeTitle || !cleanNodeDescription) {
      ElMessage.warning('请补全节点标题和说明')
      return null
    }
    if (!activeFlow.value?.nodes.some((node) => node.id === nodeId)) {
      ElMessage.error('当前节点已变化，请重新确认修改')
      return null
    }

    return updateActiveFlow((flow) => {
      applyFlowMetaPatch(flow, cleanTitle, cleanDescription)
      applyFlowNodePatch(flow, nodeId, cleanNodeTitle, cleanNodeDescription, nodePatch.content)
    })
  }

  async function deleteFlowDraft(id: string) {
    const flow = flowDrafts.value.find((item) => item.id === id)
    if (!flow) {
      return false
    }

    flowLoading.value = true
    try {
      await deleteFlow(id, flow.revision)
      clearFlowRunDraft(id)
      flowDrafts.value = flowDrafts.value.filter((flow) => flow.id !== id)
      if (activeFlowId.value === id) {
        activeFlowId.value = flowDrafts.value[0]?.id || ''
        persistActiveFlowId(activeFlowId.value)
      }
      if (flowConflictId.value === id) {
        flowConflictId.value = ''
      }
      return true
    } catch (error: any) {
      if (error.response?.status === 409) {
        await recoverFlowConflict(id, error)
      } else {
        ElMessage.error(error.response?.data?.message || 'Flow 草稿删除失败')
      }
      return false
    } finally {
      flowLoading.value = false
    }
  }

  async function restoreActiveFlowVersion(versionId: string) {
    const flow = activeFlow.value
    if (!flow) {
      return null
    }

    flowLoading.value = true
    try {
      const { data } = await restoreFlowVersion(flow.id, versionId, flow.revision)
      replaceFlowDraft(data)
      return data
    } catch (error: any) {
      if (error.response?.status === 409) {
        await recoverFlowConflict(flow.id, error)
      } else {
        ElMessage.error(error.response?.data?.message || 'Flow 修订恢复失败')
      }
      return null
    } finally {
      flowLoading.value = false
    }
  }

  function sendFlowToTask(runtimeContext = '', variableValues: Record<string, string> = {}) {
    if (!activeFlow.value) {
      return
    }

    prepareTask(
      runtimeContext.trim(),
      null,
      {
        id: activeFlow.value.id,
        title: activeFlow.value.title,
        variableValues
      }
    )
  }

  async function executeActiveFlow(runtimeContext = '', variableValues: Record<string, string> = {}) {
    if (!activeFlow.value) {
      return null
    }

    running.value = true
    try {
      const { data } = await runTask({
        input: runtimeContext.trim(),
        flowId: activeFlow.value.id,
        flowRunContext: runtimeContext.trim(),
        flowVariableValues: variableValues
      })
      latestResult.value = data
      ElMessage.success('Flow 执行完成')
      await loadTasks()
      return data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Flow 执行失败')
      await loadTasks()
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
      replaceFlowDraft(data)
      return data
    } catch (error: any) {
      if (error.response?.status === 409) {
        await recoverFlowConflict(flow.id, error)
      } else if (error.response?.status === 404) {
        await recoverMissingFlow(flow.id, error)
      } else {
        ElMessage.error(error.response?.data?.message || 'Flow 草稿保存失败')
      }
      return null
    } finally {
      flowLoading.value = false
    }
  }

  async function recoverFlowConflict(flowId: string, error: any) {
    flowConflictId.value = flowId
    await loadFlowDrafts()
    ElMessage.warning(error.response?.data?.message || 'Flow 已更新，请重新确认当前修改')
  }

  async function recoverMissingFlow(flowId: string, error: any) {
    if (flowConflictId.value === flowId) {
      flowConflictId.value = ''
    }
    await loadFlowDrafts()
    ElMessage.warning(error.response?.data?.message || '原 Flow 已删除，本地编辑仍可创建为恢复副本')
  }

  async function saveProvider(payload: SaveApiKeyPayload) {
    settingsLoading.value = true
    try {
      await saveApiKey(payload)
      providerConnectionChecks.value = {}
      ElMessage.success('API 密钥已保存')
      await loadApiKeys()
      return true
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥保存失败')
      return false
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
      return true
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Provider 激活失败')
      return false
    } finally {
      settingsLoading.value = false
    }
  }

  async function testProviderConnection(id: string) {
    providerTestLoadingId.value = id
    try {
      const { data } = await testApiKey(id)
      providerConnectionChecks.value = {
        ...providerConnectionChecks.value,
        [id]: data
      }
      ElMessage.success(`${data.provider} 连接正常`)
      return data
    } catch (error: any) {
      const nextChecks = { ...providerConnectionChecks.value }
      delete nextChecks[id]
      providerConnectionChecks.value = nextChecks
      ElMessage.error(error.response?.data?.message || 'Provider 连接验证失败')
      return null
    } finally {
      providerTestLoadingId.value = ''
    }
  }

  async function removeProvider(id: string) {
    settingsLoading.value = true
    try {
      await deleteApiKey(id)
      const nextChecks = { ...providerConnectionChecks.value }
      delete nextChecks[id]
      providerConnectionChecks.value = nextChecks
      ElMessage.success('API 密钥已删除')
      await loadApiKeys()
      return true
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥删除失败')
      return false
    } finally {
      settingsLoading.value = false
    }
  }

  async function bootstrap() {
    if (bootstrapped) {
      return
    }
    if (!bootstrapPromise) {
      bootstrapPromise = Promise.all([loadTasks(), loadApiKeys(), loadFlowDrafts()])
        .then(() => {
          reconcileAiCommandDraftSource()
          bootstrapped = true
        })
        .finally(() => {
          bootstrapPromise = null
        })
    }
    await bootstrapPromise
  }

  return {
    tasks,
    apiKeys,
    flowDrafts,
    activeFlowId,
    latestResult,
    latestTaskInput,
    latestTaskPrompt,
    taskPromptsByRunId,
    taskInput,
    taskSourcePromptId,
    taskSourcePromptTitle,
    taskSourceFlowId,
    taskSourceFlowTitle,
    taskSourceFlowVariableValues,
    taskSourceRunId,
    taskSourceRunSummary,
    taskInputVariantOfTaskId,
    taskInputVariantSourceTitle,
    taskDraftRecovered,
    running,
    historyLoading,
    settingsLoading,
    providerTestLoadingId,
    providerConnectionChecks,
    flowLoading,
    flowAssetsReady,
    flowConflictId,
    taskAssetLoading,
    activeProvider,
    activeFlow,
    workspaceName,
    profileName,
    profileInitial,
    workspacePreferencesPersisted,
    canPromoteLatestTask,
    taskSourceFlowVariables,
    missingTaskSourceFlowVariables,
    canExecuteTask,
    bootstrap,
    loadTasks,
    loadApiKeys,
    loadFlowDrafts,
    updateWorkspacePreferences,
    createFlowDraft,
    createFlowFromTemplate,
    createFlowFromPrompt,
    createFlowFromRunSnapshot,
    createFlowFromRevision,
    createFlowFromRecoveredEditor,
    consumeFlowRunSeed,
    getFlowRunDraft,
    saveFlowRunDraft,
    clearFlowRunDraft,
    prepareFlowRunFromSnapshot,
    selectFlowDraft,
    replaceFlowDraft,
    dismissFlowConflict,
    duplicateActiveFlowDraft,
    addPromptToActiveFlow,
    addContextToActiveFlow,
    removeFlowNode,
    updateFlowNode,
    syncFlowPromptNode,
    renameFlowVariable,
    moveFlowPromptNode,
    moveFlowContextNode,
    duplicateFlowPromptNode,
    updateFlowMeta,
    updateFlowMetaAndNode,
    deleteFlowDraft,
    restoreActiveFlowVersion,
    sendFlowToTask,
    executeActiveFlow,
    executeTask,
    rerunHistoricalTask,
    saveLatestTaskAsPrompt,
    createFlowFromLatestTask,
    saveHistoricalResultAsPrompt,
    createFlowFromHistoricalResult,
    prepareTask,
    prepareTaskContinuation,
    prepareTaskInputVariant,
    prepareLatestResultContinuation,
    clearTaskSource,
    acknowledgeTaskDraftRecovery,
    saveTaskSourceFlowRunDraft,
    saveProvider,
    activateProvider,
    testProviderConnection,
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
      description: '调用当前激活的模型执行结构化任务',
      content: DEFAULT_AI_TASK_EXECUTION_GUIDANCE
    },
    {
      id: createId(),
      type: 'output',
      title: 'Structured Result',
      description: '沉淀 Summary、Key Points、Result 和下一步行动',
      content: DEFAULT_OUTPUT_DELIVERY_FOCUS
    }
  ]
}

function createTemplatedFlowNodes(
  description: string,
  promptNodes: Array<Pick<FlowNode, 'title' | 'description' | 'content'>>
): FlowNode[] {
  return [
    {
      id: createId(),
      type: 'input',
      title: 'Intent',
      description: '用户想完成的 AI 工作',
      content: description
    },
    ...promptNodes.map((node) => ({
      id: createId(),
      type: 'prompt' as const,
      title: node.title,
      description: node.description,
      content: node.content || '',
      promptId: null,
      promptTitle: null
    })),
    {
      id: createId(),
      type: 'ai-task',
      title: 'AI Execution',
      description: '调用当前激活的模型执行结构化任务',
      content: DEFAULT_AI_TASK_EXECUTION_GUIDANCE
    },
    {
      id: createId(),
      type: 'output',
      title: 'Structured Result',
      description: '沉淀 Summary、Key Points、Result 和下一步行动',
      content: DEFAULT_OUTPUT_DELIVERY_FOCUS
    }
  ]
}

function createPromptBasedFlowNodes(prompt: PromptAsset): FlowNode[] {
  return [
    {
      id: createId(),
      type: 'input',
      title: 'Intent',
      description: '这条可复用 Prompt 想完成的 AI 工作',
      content: prompt.description
    },
    {
      id: createId(),
      type: 'prompt',
      title: prompt.title,
      description: prompt.description,
      content: prompt.content,
      promptId: prompt.id,
      promptTitle: prompt.title
    },
    {
      id: createId(),
      type: 'ai-task',
      title: 'AI Execution',
      description: '调用当前激活的模型执行结构化任务',
      content: DEFAULT_AI_TASK_EXECUTION_GUIDANCE
    },
    {
      id: createId(),
      type: 'output',
      title: 'Structured Result',
      description: '沉淀 Summary、Key Points、Result 和下一步行动',
      content: DEFAULT_OUTPUT_DELIVERY_FOCUS
    }
  ]
}

function buildFlowTitle(description: string) {
  const firstLine = description.split('\n').find(Boolean) || description
  const title = firstLine.replace(/[。.,，]/g, '').slice(0, 24)
  return title || 'Untitled Flow'
}

function buildTaskPromptTitle(input: string) {
  const firstLine = input.split('\n').find(Boolean) || input
  const title = firstLine.replace(/[#*`_。.,，]/g, '').trim().slice(0, 44)
  return title ? `${title} Prompt` : 'AI Command Prompt'
}

function buildTaskPromptDescription(summary: string) {
  const cleanSummary = summary.replace(/\s+/g, ' ').trim().slice(0, 96)
  return cleanSummary
    ? `从一次 AI Command 执行沉淀，用于复用「${cleanSummary}」对应的工作方式。`
    : '从一次 AI Command 执行沉淀出的可复用工作方式。'
}

function buildHistoricalResultPromptTitle(task: TaskHistoryItem) {
  const source = task.sourceFlowTitle || task.sourcePromptTitle || task.summary || task.input
  const cleanSource = source.replace(/[#*`_。.,，]/g, '').replace(/\s+/g, ' ').trim()
  const suffix = ' 结果复用'
  return `${cleanSource.slice(0, 120 - suffix.length).trim() || 'AI'}${suffix}`
}

function buildHistoricalResultPromptDescription(task: TaskHistoryItem) {
  const summary = task.summary.replace(/\s+/g, ' ').trim().slice(0, 180)
  return summary
    ? `从一条已验证的历史运行沉淀，用于基于“${summary}”的结果结构继续生成同类工作。`
    : '从一条已验证的历史运行沉淀出的可复用结果模式。'
}

function buildHistoricalResultPromptContent(task: TaskHistoryItem) {
  const sourceLabel = task.sourceFlowTitle
    ? `Flow: ${task.sourceFlowTitle}`
    : task.sourcePromptTitle
      ? `Prompt: ${task.sourcePromptTitle}`
      : 'Source: AI Command'

  return [
    '请参考下面这次已验证的 AI 结果模式，针对新的输入生成同类高质量交付。',
    '',
    sourceLabel,
    '',
    '新的输入：',
    '{input}',
    '',
    '原始执行输入：',
    task.input.trim().slice(0, 1800),
    '',
    '参考 Summary：',
    task.summary.trim().slice(0, 1200),
    '',
    '参考 Result：',
    task.result.trim().slice(0, 7000),
    '',
    '请保持结果的结构、清晰度和行动性，但不要照抄参考内容。'
  ].join('\n')
}

function buildPromptFlowTitle(promptTitle: string) {
  return `${promptTitle.slice(0, 112).trim() || 'Untitled'} Flow`
}

function buildFlowSnapshotTitle(title: string) {
  const suffix = ' 续作'
  const cleanTitle = title.trim() || 'Untitled Flow'
  return `${cleanTitle.slice(0, 120 - suffix.length).trim()}${suffix}`
}

function buildFlowRevisionTitle(title: string) {
  const suffix = ' 变体'
  const cleanTitle = title.trim() || 'Untitled Flow'
  return `${cleanTitle.slice(0, 120 - suffix.length).trim()}${suffix}`
}

function buildFlowCopyTitle(title: string) {
  const suffix = ' Copy'
  const cleanTitle = title.trim() || 'Untitled Flow'
  return `${cleanTitle.slice(0, 120 - suffix.length).trim()}${suffix}`
}

function buildFlowRecoveryTitle(title: string) {
  const suffix = ' 恢复'
  const cleanTitle = title.trim() || 'Untitled Flow'
  return `${cleanTitle.slice(0, 120 - suffix.length).trim()}${suffix}`
}

function toSaveFlowPayload(flow: FlowDraft): SaveFlowPayload {
  return {
    title: flow.title,
    description: flow.description,
    nodes: flow.nodes,
    revision: flow.revision
  }
}

function applyFlowMetaPatch(flow: FlowDraft, title: string, description: string) {
  flow.title = title
  flow.description = description
  const intentNode = flow.nodes.find((node) => node.type === 'input')
  if (intentNode) {
    intentNode.content = description
    intentNode.description = '用户想完成的 AI 工作'
  }
}

function applyFlowNodePatch(
  flow: FlowDraft,
  nodeId: string,
  title: string,
  description: string,
  content?: string
) {
  const targetNode = flow.nodes.find((node) => node.id === nodeId)
  if (!targetNode) {
    return
  }

  targetNode.title = title
  targetNode.description = description
  if (typeof content === 'string') {
    targetNode.content = content.trim()
  }
}

function createId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
