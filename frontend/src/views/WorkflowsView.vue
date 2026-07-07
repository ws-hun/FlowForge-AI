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

        <div v-if="workspace.activeFlow" class="flow-map">
          <template v-for="(node, index) in workspace.activeFlow.nodes" :key="node.id">
            <button
              type="button"
              class="flow-card-node"
              :class="[node.type, { active: selectedNode?.id === node.id }]"
              @click="selectedNodeId = node.id"
            >
              <span>{{ nodeLabel(node.type) }}</span>
              <strong>{{ node.title }}</strong>
              <p>{{ node.description }}</p>
            </button>
            <div v-if="index < workspace.activeFlow.nodes.length - 1" class="flow-connector"></div>
          </template>
        </div>

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

          <pre v-if="selectedNode.content" class="detail-code flow-node-content">{{ selectedNode.content }}</pre>

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
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { listFlowRuns } from '@/api/flows'
import { listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FlowNode, FlowNodeType, PromptAsset, TaskHistoryItem } from '@/types'

const router = useRouter()
const workspace = useWorkspaceStore()

const flowIntent = ref('')
const flowTitle = ref('')
const flowDescription = ref('')
const prompts = ref<PromptAsset[]>([])
const flowRuns = ref<TaskHistoryItem[]>([])
const flowRunsLoading = ref(false)
const flowExecutionVisible = ref(false)
const selectedNodeId = ref('')

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

watch(
  () => workspace.activeFlow?.id,
  () => {
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
    flowTitle.value = workspace.activeFlow?.title || ''
    flowDescription.value = workspace.activeFlow?.description || ''
    flowRuns.value = []
    flowExecutionVisible.value = false
    if (workspace.activeFlow?.id) {
      loadFlowRuns(workspace.activeFlow.id)
    }
  },
  { immediate: true }
)

onMounted(async () => {
  workspace.loadFlowDrafts()
  await loadPromptAssets()
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
}

async function saveFlowMeta() {
  const updatedFlow = await workspace.updateFlowMeta(flowTitle.value, flowDescription.value)
  if (!updatedFlow) {
    return
  }
  flowTitle.value = updatedFlow.title
  flowDescription.value = updatedFlow.description
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
  workspace.sendFlowToTask()
  router.push('/tasks')
}

async function executeFlowNow() {
  const flowId = workspace.activeFlow?.id
  const result = await workspace.executeActiveFlow()
  if (result && flowId) {
    flowExecutionVisible.value = true
    await loadFlowRuns(flowId)
  }
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
</script>
