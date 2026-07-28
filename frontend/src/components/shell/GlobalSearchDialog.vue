<template>
  <el-dialog
    :model-value="open"
    width="min(680px, calc(100vw - 28px))"
    class="global-search-dialog"
    modal-class="global-search-overlay"
    append-to-body
    :show-close="false"
    @update:model-value="handleOpenChange"
  >
    <div class="global-search-input-row">
      <Search class="global-search-input-icon" />
      <input
        ref="searchInput"
        v-model="query"
        type="search"
        placeholder="搜索 Flow、Prompt、Result..."
        @keydown.down.prevent="moveSelection(1)"
        @keydown.up.prevent="moveSelection(-1)"
        @keydown.enter.prevent="openSelectedResult"
      />
      <button type="button" class="global-search-close" title="关闭" aria-label="关闭搜索" @click="emit('close')">
        <Close />
      </button>
    </div>

    <div class="global-search-meta">
      <span>{{ query.trim() ? '搜索结果' : '继续创作' }}</span>
      <small>{{ visibleResults.length }} 项</small>
    </div>

    <div v-if="visibleResults.length" class="global-search-results" role="listbox" aria-label="全局搜索结果">
      <button
        v-for="(result, index) in visibleResults"
        :key="result.id"
        type="button"
        role="option"
        :aria-selected="activeIndex === index"
        class="global-search-result"
        :class="{ active: activeIndex === index }"
        @mouseenter="activeIndex = index"
        @click="openResult(result)"
      >
        <span class="global-search-result-icon" :class="result.kind">
          <component :is="result.icon" />
        </span>
        <span class="global-search-result-copy">
          <strong>{{ result.title }}</strong>
          <small>{{ result.description }}</small>
        </span>
        <em>{{ result.label }}</em>
      </button>
    </div>

    <div v-else class="global-search-empty">
      <strong>没有匹配内容</strong>
      <p>换一个关键词，或直接进入工作空间开始新的 AI 任务。</p>
      <button type="button" class="secondary-button" @click="openResult(quickActions[0]!)">打开 AI Command</button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { useRouter } from 'vue-router'
import { Clock, Close, Connection, Document, Plus, Search } from '@element-plus/icons-vue'
import { listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { PromptAsset, TaskHistoryItem } from '@/types'

type SearchResultKind = 'action' | 'flow' | 'prompt' | 'run'
type SearchResult = {
  id: string
  kind: SearchResultKind
  label: string
  title: string
  description: string
  searchText: string
  to: RouteLocationRaw
  icon: Component
}

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const workspace = useWorkspaceStore()
const query = ref('')
const activeIndex = ref(0)
const searchInput = ref<HTMLInputElement | null>(null)
const prompts = ref<PromptAsset[]>([])
const promptsLoaded = ref(false)

const quickActions: SearchResult[] = [
  createResult('action-task', 'action', 'Create', 'AI Command', '执行一个新的结构化 AI 任务', '/tasks', Plus),
  createResult('action-flow', 'action', 'Create', 'Flow Space', '创建或继续编排可执行 Flow', '/workflows', Connection),
  createResult('action-prompt', 'action', 'Reuse', 'Prompt Library', '查找并复用已沉淀的工作方式', '/prompts', Document),
  createResult('action-history', 'action', 'Explore', 'History', '回到一次可追溯的 AI 执行', '/history', Clock)
]

const flowResults = computed<SearchResult[]>(() =>
  workspace.flowDrafts.map((flow) =>
    createResult(
      `flow-${flow.id}`,
      'flow',
      'Flow',
      flow.title,
      flow.description,
      { path: '/workflows', query: { flow: flow.id } },
      Connection
    )
  )
)

const promptResults = computed<SearchResult[]>(() =>
  prompts.value.map((prompt) =>
    createResult(
      `prompt-${prompt.id}`,
      'prompt',
      'Prompt',
      prompt.title,
      prompt.description || prompt.category,
      { path: '/prompts', query: { prompt: prompt.id } },
      Document,
      [prompt.category, prompt.content, ...prompt.tags]
    )
  )
)

const runResults = computed<SearchResult[]>(() =>
  workspace.tasks.map((task) =>
    createResult(
      `run-${task.id}`,
      'run',
      task.status === 'failed' ? 'Failed Run' : 'Result',
      runTitle(task),
      task.status === 'failed' ? task.errorMessage || task.result : task.summary,
      { path: '/history', query: { run: task.id } },
      Clock,
      [task.input, task.result, task.sourceFlowTitle, task.sourcePromptTitle, task.provider, task.model]
    )
  )
)

const allResults = computed(() => [
  ...quickActions,
  ...flowResults.value,
  ...promptResults.value,
  ...runResults.value
])

const visibleResults = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) {
    return [
      ...quickActions,
      ...flowResults.value.slice(0, 2),
      ...promptResults.value.slice(0, 2),
      ...runResults.value.slice(0, 3)
    ].slice(0, 11)
  }

  return allResults.value
    .filter((result) => result.searchText.includes(keyword))
    .sort((left, right) => searchScore(right, keyword) - searchScore(left, keyword))
    .slice(0, 12)
})

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    query.value = ''
    activeIndex.value = 0
    await nextTick()
    searchInput.value?.focus()
    await workspace.bootstrap()
    if (!promptsLoaded.value) {
      try {
        const { data } = await listPrompts()
        prompts.value = data
        promptsLoaded.value = true
      } catch {
        prompts.value = []
      }
    }
  }
)

watch(visibleResults, () => {
  activeIndex.value = 0
})

function createResult(
  id: string,
  kind: SearchResultKind,
  label: string,
  title: string,
  description: string,
  to: RouteLocationRaw,
  icon: Component,
  extraSearchValues: Array<string | null | undefined> = []
): SearchResult {
  return {
    id,
    kind,
    label,
    title,
    description,
    to,
    icon,
    searchText: [title, description, label, ...extraSearchValues].filter(Boolean).join(' ').toLowerCase()
  }
}

function runTitle(task: TaskHistoryItem) {
  return task.sourceFlowTitle || task.sourcePromptTitle || task.summary || compactInput(task.input)
}

function compactInput(input: string) {
  const firstLine = input.split(/\r?\n/).map((line) => line.trim()).find(Boolean) || '未命名 AI 任务'
  return firstLine.length > 72 ? `${firstLine.slice(0, 72)}…` : firstLine
}

function searchScore(result: SearchResult, keyword: string) {
  const title = result.title.toLowerCase()
  if (title === keyword) return 4
  if (title.startsWith(keyword)) return 3
  if (title.includes(keyword)) return 2
  return 1
}

function moveSelection(offset: number) {
  const count = visibleResults.value.length
  if (!count) return
  activeIndex.value = (activeIndex.value + offset + count) % count
}

function openSelectedResult() {
  const result = visibleResults.value[activeIndex.value]
  if (result) {
    openResult(result)
  }
}

function openResult(result: SearchResult) {
  emit('close')
  void router.push(result.to)
}

function handleOpenChange(value: boolean) {
  if (!value) {
    emit('close')
  }
}
</script>
