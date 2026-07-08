<template>
  <section class="flow-workspace">
    <header class="quiet-header">
      <p class="page-kicker">Flow Space</p>
      <h1>把 Prompt 连接成可执行 Flow。</h1>
      <p>从一个目标开始，组织输入、Prompt、AI 执行和结构化输出，逐步形成可复用的工作流资产。</p>
    </header>

    <div class="flow-builder-layout">
      <aside class="surface flow-draft-panel">
        <div class="panel-heading">
          <span class="section-kicker">New Flow</span>
          <h2>描述你想搭建的工作流</h2>
        </div>

        <textarea
          v-model="flowIntent"
          class="quiet-textarea flow-intent-input"
          placeholder="例如：把一个产品想法依次拆解成 PRD、接口草案和任务清单..."
        ></textarea>

        <button
          type="button"
          :class="workspace.activeFlow ? 'secondary-button' : 'primary-button'"
          :disabled="!flowIntent.trim() || workspace.flowLoading"
          @click="createFlow"
        >
          {{ workspace.flowLoading ? '保存中...' : '创建 Flow 草稿' }}
        </button>

        <div class="draft-list">
          <div class="section-heading compact">
            <h3>继续创作</h3>
            <span>{{ workspace.flowDrafts.length ? `${workspace.flowDrafts.length} 个草稿` : '暂无草稿' }}</span>
          </div>

          <button
            v-for="flow in workspace.flowDrafts"
            :key="flow.id"
            type="button"
            class="draft-item"
            :class="{ active: flow.id === workspace.activeFlowId }"
            @click="selectFlow(flow.id)"
          >
            <strong>{{ flow.title }}</strong>
            <span>{{ formatDate(flow.updatedAt) }}</span>
          </button>

          <p v-if="!workspace.flowDrafts.length" class="quiet-note">
            第一个 Flow 会从输入目标、AI 执行和结构化输出三步开始。
          </p>
        </div>
      </aside>

      <section class="surface flow-canvas-panel">
        <div v-if="workspace.activeFlow" class="flow-canvas-header">
          <div>
            <span class="badge">Draft Flow</span>
            <h2>{{ workspace.activeFlow.title }}</h2>
            <p>{{ workspace.activeFlow.description }}</p>
          </div>
          <div class="flow-run-actions">
            <button type="button" class="ghost-button" :disabled="workspace.running" @click="sendFlowToTaskWorkspace">
              带入 Task
            </button>
            <button type="button" class="primary-button" :disabled="workspace.running" @click="executeFlowNow">
              {{ workspace.running ? '执行中...' : '执行 Flow' }}
            </button>
          </div>
        </div>

        <div v-if="workspace.activeFlow && flowRunPhase !== 'idle'" class="flow-run-signal" :class="flowRunPhase">
          <span class="flow-run-dot"></span>
          <div>
            <strong>{{ flowRunTitle }}</strong>
            <p>{{ flowRunDescription }}</p>
          </div>
        </div>

        <div v-if="workspace.activeFlow" class="flow-map">
          <template v-for="(node, index) in workspace.activeFlow.nodes" :key="node.id">
            <button
              type="button"
              class="flow-card-node"
              :class="[node.type, `is-${nodeStatus(node.id)}`, { active: selectedNode?.id === node.id }]"
              @click="selectedNodeId = node.id"
            >
              <div class="flow-node-meta">
                <span class="flow-node-type">{{ nodeLabel(node.type) }}</span>
                <span class="flow-node-state">{{ nodeStateLabel(nodeStatus(node.id)) }}</span>
              </div>
              <strong>{{ node.title }}</strong>
              <p>{{ node.description }}</p>
            </button>
            <div
              v-if="index < workspace.activeFlow.nodes.length - 1"
              class="flow-connector"
              :class="{ active: connectorCompleted(index), running: connectorRunning(index) }"
            ></div>
          </template>
        </div>

        <section v-if="workspace.activeFlow" class="flow-run-brief">
          <div class="section-heading compact">
            <div>
              <h3>Run Brief</h3>
              <span>执行前确认 AI 将接收的工作上下文</span>
            </div>
            <span>{{ workspace.activeProvider?.model || 'No provider' }}</span>
          </div>

          <textarea
            v-model="flowRunContext"
            class="quiet-textarea flow-context-input"
            placeholder="为本次运行补充上下文，例如目标用户、输出格式、约束条件或业务背景..."
          ></textarea>

          <details class="flow-input-preview">
            <summary>查看本次 AI 输入</summary>
            <pre>{{ flowRunInputPreview }}</pre>
          </details>
        </section>

        <AiResultDocument
          v-if="workspace.activeFlow && flowExecutionVisible && workspace.latestResult"
          class="flow-execution-result"
          :summary="workspace.latestResult.summary"
          :result="workspace.latestResult.result"
          :raw="workspace.latestResult.raw"
          compact
          :show-raw="false"
        />

        <div v-if="!workspace.activeFlow" class="flow-empty-state">
          <span class="badge">Canvas</span>
          <strong>先创建一个 Flow 草稿</strong>
          <p>FlowForge 会生成一个安静的工作流骨架，你可以继续加入 Prompt 节点。</p>
        </div>
      </section>

      <aside class="surface flow-inspector">
        <div v-if="workspace.activeFlow" class="flow-asset-editor">
          <div class="panel-heading">
            <span class="section-kicker">Flow Asset</span>
            <h2>调整 Flow 目标</h2>
          </div>
          <input v-model="flowTitle" class="quiet-input" placeholder="Flow 标题" />
          <textarea v-model="flowDescription" class="quiet-textarea" placeholder="Flow 目标"></textarea>
          <div class="flow-editor-actions">
            <button
              type="button"
              class="secondary-button"
              :disabled="!flowMetaChanged || workspace.flowLoading"
              @click="saveFlowMeta"
            >
              保存
            </button>
            <button type="button" class="danger-button" :disabled="workspace.flowLoading" @click="confirmDeleteFlow">
              删除
            </button>
          </div>
        </div>

        <template v-if="workspace.activeFlow && selectedNode">
          <div class="panel-heading">
            <span class="section-kicker">Inspector</span>
            <h2>{{ selectedNode.title }}</h2>
            <p>{{ selectedNode.description }}</p>
          </div>

          <div class="node-status-card" :class="selectedNodeState">
            <span>{{ nodeStateLabel(selectedNodeState) }}</span>
            <strong>{{ nodeStateTitle(selectedNodeState) }}</strong>
            <p>{{ nodeStateDescription(selectedNode) }}</p>
          </div>

          <div v-if="selectedNode.type === 'prompt'" class="flow-node-order-actions">
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedNodeUp || workspace.flowLoading"
              @click="moveSelectedPromptNode('up')"
            >
              上移
            </button>
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedNodeDown || workspace.flowLoading"
              @click="moveSelectedPromptNode('down')"
            >
              下移
            </button>
          </div>

          <div class="flow-node-editor">
            <label>
              <span>Node title</span>
              <input v-model="nodeTitle" class="quiet-input" placeholder="节点标题" />
            </label>
            <label>
              <span>Description</span>
              <textarea v-model="nodeDescription" class="quiet-textarea" placeholder="节点说明"></textarea>
            </label>
            <label v-if="nodeCanEditContent">
              <span>{{ selectedNode.type === 'prompt' ? 'Prompt content' : 'Input content' }}</span>
              <textarea
                v-model="nodeContent"
                class="quiet-textarea flow-node-content-editor"
                placeholder="定义这个节点在 Flow 中提供的上下文..."
              ></textarea>
            </label>
            <button
              type="button"
              class="secondary-button"
              :disabled="!nodeEditorChanged || workspace.flowLoading"
              @click="saveSelectedNode"
            >
              保存节点
            </button>
          </div>

          <button
            v-if="selectedNode.type === 'prompt'"
            type="button"
            class="ghost-button"
            @click="removeSelectedNode"
          >
            移除此 Prompt 节点
          </button>
        </template>

        <div v-else class="panel-heading">
          <span class="section-kicker">Inspector</span>
          <h2>选择一个节点</h2>
          <p>查看节点上下文，或从 Prompt Library 添加可复用工作方式。</p>
        </div>

        <div class="prompt-node-picker">
          <div class="section-heading compact">
            <h3>加入 Prompt</h3>
            <span>{{ prompts.length ? `${prompts.length} 个可用` : '暂无 Prompt' }}</span>
          </div>

          <button
            v-for="prompt in prompts.slice(0, 5)"
            :key="prompt.id"
            type="button"
            class="prompt-node-option"
            :disabled="!workspace.activeFlow || workspace.flowLoading"
            @click="addPromptNode(prompt)"
          >
            <span class="badge">{{ prompt.category }}</span>
            <strong>{{ prompt.title }}</strong>
            <small>{{ prompt.description }}</small>
          </button>

          <p v-if="!prompts.length" class="quiet-note">
            先在 Prompt Library 中沉淀可复用 Prompt，再把它们作为 Flow 节点接入。
          </p>
        </div>

        <div v-if="workspace.activeFlow" class="flow-run-section">
          <div class="section-heading compact">
            <h3>最近执行</h3>
            <span>{{ flowRuns.length ? `${flowRuns.length} 条记录` : '暂无记录' }}</span>
          </div>
          <div v-if="flowRunsLoading" class="run-timeline">
            <article v-for="item in 2" :key="item" class="run-item skeleton-run"></article>
          </div>
          <div v-else-if="flowRuns.length" class="run-timeline">
            <article v-for="run in flowRuns" :key="run.id" class="run-item">
              <time>{{ formatDate(run.createdAt) }}</time>
              <strong>{{ run.summary }}</strong>
              <p>{{ run.result }}</p>
            </article>
          </div>
          <p v-else class="quiet-note">
            从这个 Flow 发送到 Task 并执行后，记录会回到这里。
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { listFlowRuns } from '@/api/flows'
import { listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FlowNode, FlowNodeType, PromptAsset, TaskHistoryItem } from '@/types'

type FlowNodeRunState = 'idle' | 'queued' | 'running' | 'completed' | 'error'
type FlowRunPhase = 'idle' | 'running' | 'completed' | 'error'

const router = useRouter()
const workspace = useWorkspaceStore()

const flowIntent = ref('')
const flowTitle = ref('')
const flowDescription = ref('')
const flowRunContext = ref('')
const nodeTitle = ref('')
const nodeDescription = ref('')
const nodeContent = ref('')
const prompts = ref<PromptAsset[]>([])
const flowRuns = ref<TaskHistoryItem[]>([])
const flowRunsLoading = ref(false)
const flowExecutionVisible = ref(false)
const flowRunPhase = ref<FlowRunPhase>('idle')
const flowRunStartedAt = ref('')
const flowRunCompletedAt = ref('')
const nodeRunStates = ref<Record<string, FlowNodeRunState>>({})
const selectedNodeId = ref('')
let flowProgressTimers: number[] = []

const selectedNode = computed<FlowNode | null>(() => {
  if (!workspace.activeFlow) {
    return null
  }
  return workspace.activeFlow.nodes.find((node) => node.id === selectedNodeId.value) || workspace.activeFlow.nodes[0] || null
})

const flowMetaChanged = computed(() => {
  if (!workspace.activeFlow) {
    return false
  }
  return flowTitle.value.trim() !== workspace.activeFlow.title || flowDescription.value.trim() !== workspace.activeFlow.description
})

const flowRunInputPreview = computed(() => workspace.composeActiveFlowInput(flowRunContext.value))

const selectedNodeState = computed<FlowNodeRunState>(() => {
  if (!selectedNode.value) {
    return 'idle'
  }
  return nodeStatus(selectedNode.value.id)
})

const nodeCanEditContent = computed(() => {
  return selectedNode.value?.type === 'input' || selectedNode.value?.type === 'prompt'
})

const nodeEditorChanged = computed(() => {
  if (!selectedNode.value) {
    return false
  }
  const contentChanged = nodeCanEditContent.value && nodeContent.value.trim() !== (selectedNode.value.content || '')
  return (
    nodeTitle.value.trim() !== selectedNode.value.title ||
    nodeDescription.value.trim() !== selectedNode.value.description ||
    contentChanged
  )
})

const selectedPromptIndex = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return -1
  }
  return promptNodes.value.findIndex((node) => node.id === selectedNode.value?.id)
})

const promptNodes = computed(() => workspace.activeFlow?.nodes.filter((node) => node.type === 'prompt') || [])
const canMoveSelectedNodeUp = computed(() => selectedPromptIndex.value > 0)
const canMoveSelectedNodeDown = computed(() => {
  return selectedPromptIndex.value >= 0 && selectedPromptIndex.value < promptNodes.value.length - 1
})

const flowRunTitle = computed(() => {
  const labels: Record<FlowRunPhase, string> = {
    idle: 'Flow Ready',
    running: 'Flow 正在执行',
    completed: 'Flow 执行完成',
    error: 'Flow 执行失败'
  }
  return labels[flowRunPhase.value]
})

const flowRunDescription = computed(() => {
  if (flowRunPhase.value === 'running') {
    const currentNode = workspace.activeFlow?.nodes.find((node) => nodeStatus(node.id) === 'running')
    const startedAt = flowRunStartedAt.value ? `，开始于 ${formatDate(flowRunStartedAt.value)}` : ''
    return currentNode ? `正在处理 ${currentNode.title}${startedAt}` : `正在连接 Prompt、AI 执行与结构化输出${startedAt}。`
  }

  if (flowRunPhase.value === 'completed') {
    return flowRunCompletedAt.value ? `完成于 ${formatDate(flowRunCompletedAt.value)}，结果已沉淀到当前画布。` : '结果已沉淀到当前画布。'
  }

  if (flowRunPhase.value === 'error') {
    return '执行没有完成，请检查当前 Provider 或稍后重试。'
  }

  return 'Flow 已准备好执行。'
})

watch(
  () => workspace.activeFlow?.id,
  () => {
    resetFlowRunState()
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
    flowTitle.value = workspace.activeFlow?.title || ''
    flowDescription.value = workspace.activeFlow?.description || ''
    flowRunContext.value = ''
    flowRuns.value = []
    flowExecutionVisible.value = false
    if (workspace.activeFlow?.id) {
      loadFlowRuns(workspace.activeFlow.id)
    }
  },
  { immediate: true }
)

watch(
  () => selectedNode.value?.id,
  () => syncSelectedNodeEditor(),
  { immediate: true }
)

onMounted(async () => {
  workspace.loadFlowDrafts()
  await loadPromptAssets()
})

onBeforeUnmount(() => {
  clearFlowProgressTimers()
})

async function loadPromptAssets() {
  try {
    const { data } = await listPrompts()
    prompts.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt Library 加载失败')
  }
}

async function loadFlowRuns(flowId: string) {
  flowRunsLoading.value = true
  try {
    const { data } = await listFlowRuns(flowId)
    if (workspace.activeFlow?.id === flowId) {
      flowRuns.value = data
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Flow 执行记录加载失败')
  } finally {
    flowRunsLoading.value = false
  }
}

async function createFlow() {
  const flow = await workspace.createFlowDraft(flowIntent.value)
  if (!flow) {
    return
  }
  flowIntent.value = ''
  selectedNodeId.value = flow.nodes[0]?.id || ''
  ElMessage.success('Flow 草稿已创建')
}

function selectFlow(id: string) {
  workspace.selectFlowDraft(id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
}

async function addPromptNode(prompt: PromptAsset) {
  const addedNode = await workspace.addPromptToActiveFlow(prompt)
  selectedNodeId.value = addedNode?.id || selectedNodeId.value
  resetFlowRunState()
  if (addedNode) {
    ElMessage.success('Prompt 已加入 Flow')
  }
}

async function removeSelectedNode() {
  if (!selectedNode.value) {
    return
  }
  await workspace.removeFlowNode(selectedNode.value.id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
  resetFlowRunState()
}

async function saveSelectedNode() {
  if (!selectedNode.value) {
    return
  }

  const updatedFlow = await workspace.updateFlowNode(selectedNode.value.id, {
    title: nodeTitle.value,
    description: nodeDescription.value,
    content: nodeCanEditContent.value ? nodeContent.value : selectedNode.value.content
  })

  if (!updatedFlow) {
    return
  }

  resetFlowRunState()
  syncSelectedNodeEditor()
  ElMessage.success('节点已保存')
}

async function moveSelectedPromptNode(direction: 'up' | 'down') {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return
  }

  const movedFlow = await workspace.moveFlowPromptNode(selectedNode.value.id, direction)
  if (!movedFlow) {
    return
  }

  selectedNodeId.value = selectedNode.value.id
  resetFlowRunState()
  ElMessage.success('Prompt 顺序已调整')
}

async function saveFlowMeta() {
  const updatedFlow = await workspace.updateFlowMeta(flowTitle.value, flowDescription.value)
  if (!updatedFlow) {
    return
  }
  flowTitle.value = updatedFlow.title
  flowDescription.value = updatedFlow.description
  resetFlowRunState()
  ElMessage.success('Flow 已保存')
}

async function confirmDeleteFlow() {
  if (!workspace.activeFlow) {
    return
  }

  try {
    await ElMessageBox.confirm(`删除「${workspace.activeFlow.title}」后无法继续编辑这个 Flow。`, '删除 Flow', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const deleted = await workspace.deleteFlowDraft(workspace.activeFlow.id)
    if (deleted) {
      flowTitle.value = workspace.activeFlow?.title || ''
      flowDescription.value = workspace.activeFlow?.description || ''
      selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
      ElMessage.success('Flow 已删除')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Flow 删除失败')
    }
  }
}

function sendFlowToTaskWorkspace() {
  workspace.sendFlowToTask(flowRunContext.value)
  router.push('/tasks')
}

async function executeFlowNow() {
  const flow = workspace.activeFlow
  const flowId = flow?.id
  if (!flow || !flowId) {
    return
  }

  startFlowRun(flow.nodes)
  const result = await workspace.executeActiveFlow(flowRunContext.value)
  if (result && flowId) {
    completeFlowRun()
    flowExecutionVisible.value = true
    await loadFlowRuns(flowId)
    return
  }

  failFlowRun()
}

function nodeLabel(type: FlowNodeType) {
  const labels: Record<FlowNodeType, string> = {
    input: 'Input',
    prompt: 'Prompt',
    'ai-task': 'AI Task',
    output: 'Output'
  }
  return labels[type]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function nodeStatus(nodeId: string): FlowNodeRunState {
  return nodeRunStates.value[nodeId] || 'idle'
}

function startFlowRun(nodes: FlowNode[]) {
  clearFlowProgressTimers()
  flowRunPhase.value = 'running'
  flowRunStartedAt.value = new Date().toISOString()
  flowRunCompletedAt.value = ''
  flowExecutionVisible.value = false

  nodeRunStates.value = nodes.reduce<Record<string, FlowNodeRunState>>((states, node, index) => {
    states[node.id] = index === 0 ? 'running' : 'queued'
    return states
  }, {})

  nodes.slice(1).forEach((node, index) => {
    const timer = window.setTimeout(() => {
      if (flowRunPhase.value !== 'running') {
        return
      }
      const previousNode = nodes[index]
      nodeRunStates.value = {
        ...nodeRunStates.value,
        [previousNode.id]: 'completed',
        [node.id]: 'running'
      }
    }, (index + 1) * 700)
    flowProgressTimers.push(timer)
  })
}

function completeFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  clearFlowProgressTimers()
  flowRunPhase.value = 'completed'
  flowRunCompletedAt.value = new Date().toISOString()
  nodeRunStates.value = workspace.activeFlow.nodes.reduce<Record<string, FlowNodeRunState>>((states, node) => {
    states[node.id] = 'completed'
    return states
  }, {})
}

function failFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  clearFlowProgressTimers()
  flowRunPhase.value = 'error'
  const runningNode = workspace.activeFlow.nodes.find((node) => nodeStatus(node.id) === 'running')
  nodeRunStates.value = {
    ...nodeRunStates.value,
    ...(runningNode ? { [runningNode.id]: 'error' as FlowNodeRunState } : {})
  }
}

function resetFlowRunState() {
  clearFlowProgressTimers()
  flowRunPhase.value = 'idle'
  flowRunStartedAt.value = ''
  flowRunCompletedAt.value = ''
  nodeRunStates.value = {}
}

function clearFlowProgressTimers() {
  flowProgressTimers.forEach((timer) => window.clearTimeout(timer))
  flowProgressTimers = []
}

function connectorCompleted(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(currentNode && nextNode && nodeStatus(currentNode.id) === 'completed' && nodeStatus(nextNode.id) === 'completed')
}

function connectorRunning(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(currentNode && nextNode && nodeStatus(currentNode.id) === 'completed' && nodeStatus(nextNode.id) === 'running')
}

function nodeStateLabel(state: FlowNodeRunState) {
  const labels: Record<FlowNodeRunState, string> = {
    idle: 'Ready',
    queued: 'Queued',
    running: 'Running',
    completed: 'Done',
    error: 'Error'
  }
  return labels[state]
}

function nodeStateTitle(state: FlowNodeRunState) {
  const labels: Record<FlowNodeRunState, string> = {
    idle: '等待执行',
    queued: '排队等待',
    running: '正在处理',
    completed: '已完成',
    error: '需要检查'
  }
  return labels[state]
}

function nodeStateDescription(node: FlowNode) {
  const descriptions: Record<FlowNodeType, string> = {
    input: '读取 Flow 目标，作为本次执行的上下文起点。',
    prompt: '将可复用 Prompt 合并到本次 AI 工作流中。',
    'ai-task': '调用当前激活的 AI Provider 完成结构化任务。',
    output: '把 Summary、Key Points 与 Result 沉淀为可回看的执行结果。'
  }
  return descriptions[node.type]
}

function syncSelectedNodeEditor() {
  nodeTitle.value = selectedNode.value?.title || ''
  nodeDescription.value = selectedNode.value?.description || ''
  nodeContent.value = selectedNode.value?.content || ''
}
</script>
