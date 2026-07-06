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
          <button type="button" class="primary-button" @click="runFlowPreview">发送到 Task</button>
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

        <div v-else class="flow-empty-state">
          <span class="badge">Canvas</span>
          <strong>先创建一个 Flow 草稿</strong>
          <p>FlowForge 会生成一个安静的工作流骨架，你可以继续加入 Prompt 节点。</p>
        </div>
      </section>

      <aside class="surface flow-inspector">
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
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FlowNode, FlowNodeType, PromptAsset } from '@/types'

const router = useRouter()
const workspace = useWorkspaceStore()

const flowIntent = ref('')
const prompts = ref<PromptAsset[]>([])
const selectedNodeId = ref('')

const selectedNode = computed<FlowNode | null>(() => {
  if (!workspace.activeFlow) {
    return null
  }
  return workspace.activeFlow.nodes.find((node) => node.id === selectedNodeId.value) || workspace.activeFlow.nodes[0] || null
})

watch(
  () => workspace.activeFlow?.id,
  () => {
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
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

function runFlowPreview() {
  workspace.sendFlowToTask()
  router.push('/tasks')
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
