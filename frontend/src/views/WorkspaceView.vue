<template>
  <section class="workspace-home">
    <div class="home-hero">
      <p class="page-kicker">FlowForge</p>
      <h1 class="page-title">把想法变成 AI 工作流。</h1>
      <p class="page-subtitle">从一句自然语言开始，整理任务意图，进入命令工作区，并逐步形成可复用的 Flow。</p>
    </div>

    <div class="create-composer surface">
      <div class="composer-heading">
        <span>New Flow</span>
        <strong>描述你想创建的 AI 工作</strong>
      </div>
      <textarea
        ref="inputRef"
        v-model="workspace.taskInput"
        class="workspace-input"
        placeholder="例如：帮我把一个产品想法拆解成 PRD、接口草案和执行任务..."
        @input="workspace.clearTaskSource"
      ></textarea>
      <div class="composer-footer">
        <span>{{ workspace.taskInput.trim() ? '已准备进入 AI Command Workspace' : '先写下一个想法，或从模板开始' }}</span>
        <button class="primary-button" :disabled="!workspace.taskInput.trim()" @click="startBuilding">开始构建</button>
      </div>
    </div>

    <section v-if="continueFlow || latestSuccessfulTask" class="workspace-continuation">
      <div class="section-heading">
        <h2>继续工作</h2>
        <span>回到最近的创作上下文</span>
      </div>
      <div class="workspace-continuation-grid">
        <button
          v-if="continueFlow"
          type="button"
          class="soft-card workspace-continuation-card"
          @click="openFlow(continueFlow.id)"
        >
          <div class="row-between">
            <span class="badge">Flow</span>
            <time>{{ formatDate(continueFlow.updatedAt) }}</time>
          </div>
          <strong>{{ continueFlow.title }}</strong>
          <p>{{ continueFlow.description }}</p>
          <span class="workspace-card-action">继续编辑 <el-icon><ArrowRight /></el-icon></span>
        </button>

        <button
          v-if="latestSuccessfulTask"
          type="button"
          class="soft-card workspace-continuation-card"
          @click="continueResult"
        >
          <div class="row-between">
            <span class="badge">Result</span>
            <time>{{ formatDate(latestSuccessfulTask.createdAt) }}</time>
          </div>
          <strong>{{ latestSuccessfulTask.summary }}</strong>
          <p>{{ latestSuccessfulTask.result }}</p>
          <span class="workspace-card-action">继续创作 <el-icon><ArrowRight /></el-icon></span>
        </button>
      </div>
    </section>

    <section v-if="recentFlows.length || recentPrompts.length" class="workspace-recent-assets">
      <div class="section-heading">
        <h2>最近资产</h2>
        <span>Flow 与 Prompt</span>
      </div>
      <div class="workspace-asset-columns">
        <div v-if="recentFlows.length" class="workspace-asset-group">
          <div class="workspace-asset-group-heading">
            <strong>Recent Flows</strong>
            <button type="button" class="ghost-button" @click="router.push('/workflows')">全部</button>
          </div>
          <button
            v-for="flow in recentFlows"
            :key="flow.id"
            type="button"
            class="workspace-asset-row"
            @click="openFlow(flow.id)"
          >
            <span>
              <strong>{{ flow.title }}</strong>
              <small>{{ flow.nodes.length }} 个节点 · {{ formatDate(flow.updatedAt) }}</small>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>

        <div v-if="recentPrompts.length" class="workspace-asset-group">
          <div class="workspace-asset-group-heading">
            <strong>Prompt Library</strong>
            <button type="button" class="ghost-button" @click="router.push('/prompts')">全部</button>
          </div>
          <button
            v-for="prompt in recentPrompts"
            :key="prompt.id"
            type="button"
            class="workspace-asset-row"
            @click="openPrompt(prompt.id)"
          >
            <span>
              <strong>{{ prompt.title }}</strong>
              <small>{{ prompt.category }} · {{ formatDate(prompt.updatedAt) }}</small>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>
    </section>

    <section class="template-section">
      <div class="section-heading">
        <h2>从模板开始</h2>
        <span>选择一个创作起点</span>
      </div>
      <div class="template-grid">
        <button
          v-for="template in templates"
          :key="template.title"
          type="button"
          class="soft-card template-card"
          @click="useTemplate(template.prompt)"
        >
          <span class="badge">{{ template.category }}</span>
          <strong>{{ template.title }}</strong>
          <p>{{ template.description }}</p>
        </button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { PromptAsset } from '@/types'

const router = useRouter()
const workspace = useWorkspaceStore()
const inputRef = ref<HTMLTextAreaElement | null>(null)
const prompts = ref<PromptAsset[]>([])

const continueFlow = computed(() => workspace.activeFlow || workspace.flowDrafts[0] || null)
const latestSuccessfulTask = computed(() =>
  workspace.tasks.find((task) => task.status !== 'failed') || null
)
const recentFlows = computed(() =>
  workspace.flowDrafts.filter((flow) => flow.id !== continueFlow.value?.id).slice(0, 3)
)
const recentPrompts = computed(() => prompts.value.slice(0, 3))

const templates = [
  {
    title: '需求拆解 Flow',
    category: '产品',
    description: '把模糊想法整理为目标、用户场景、功能范围和下一步任务。',
    prompt: '请将这个产品想法拆解为目标用户、核心问题、MVP 功能、优先级和下一步执行任务：'
  },
  {
    title: 'PRD 总结 Flow',
    category: '文档',
    description: '从长文本中提炼背景、目标、范围、风险和验收标准。',
    prompt: '请将下面的 PRD 或需求描述整理为背景、目标、功能范围、风险和验收标准：'
  },
  {
    title: '代码审查 Flow',
    category: '工程',
    description: '聚焦质量、风险、边界情况和测试缺口。',
    prompt: '请审查下面的代码或技术方案，指出主要风险、可维护性问题、边界情况和测试建议：'
  },
  {
    title: '市场分析 Flow',
    category: '研究',
    description: '从一个方向出发，形成竞品、机会、用户和定位分析。',
    prompt: '请围绕下面的市场方向，分析目标用户、竞品格局、机会点、差异化定位和验证计划：'
  }
]

onMounted(loadRecentPrompts)

async function loadRecentPrompts() {
  try {
    const { data } = await listPrompts()
    prompts.value = data
  } catch {
    prompts.value = []
  }
}

function startBuilding() {
  if (!workspace.taskInput.trim()) {
    return
  }
  router.push('/tasks')
}

async function useTemplate(prompt: string) {
  workspace.prepareTask(prompt)
  await nextTick()
  inputRef.value?.focus()
}

function openFlow(flowId: string) {
  workspace.selectFlowDraft(flowId)
  router.push('/workflows')
}

function continueResult() {
  if (!latestSuccessfulTask.value) {
    return
  }
  workspace.prepareTaskContinuation(latestSuccessfulTask.value)
  router.push('/tasks')
}

function openPrompt(promptId: string) {
  router.push({ path: '/prompts', query: { prompt: promptId } })
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  }).format(new Date(value))
}
</script>
