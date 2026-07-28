<template>
  <details class="flow-input-preview" @toggle="onToggle">
    <summary>查看服务端执行输入</summary>
    <div v-if="loading" class="flow-input-preview-status">
      正在按保存的 Flow 编译本次输入...
    </div>
    <div v-else-if="preview" class="flow-input-preview-content">
      <div class="flow-input-preview-meta">
        <span class="flow-input-preview-state" :class="{ 'is-ready': preview.executable && !stale, 'is-warning': !preview.executable || stale }">
          <span class="flow-input-preview-state-dot"></span>
          {{ stale ? '执行上下文已更新' : readinessLabel }}
        </span>
        <div class="flow-input-preview-actions">
          <button v-if="stale" type="button" class="text-button" @click="loadPreview">
            <RefreshRight class="flow-input-preview-action-icon" />
            刷新
          </button>
          <button type="button" class="text-button" :disabled="stale" @click="copyExecutionInput">
            <CopyDocument class="flow-input-preview-action-icon" />
            复制完整输入
          </button>
        </div>
      </div>

      <div v-if="!preview.executable && !stale" class="flow-input-preview-readiness">
        <strong>执行前还有内容需要补全</strong>
        <p v-if="preview.missingVariables.length">
          变量：{{ preview.missingVariables.join('、') }}
        </p>
        <p v-if="preview.incompleteNodes.length">
          节点：{{ preview.incompleteNodes.join('、') }}
        </p>
      </div>

      <div class="flow-input-preview-viewbar">
        <div class="flow-input-preview-tabs" role="tablist" aria-label="执行输入预览方式">
          <button
            type="button"
            role="tab"
            :aria-selected="activeView === 'outline'"
            :class="{ 'is-active': activeView === 'outline' }"
            @click="activeView = 'outline'"
          >
            执行结构
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="activeView === 'raw'"
            :class="{ 'is-active': activeView === 'raw' }"
            @click="activeView = 'raw'"
          >
            Raw 输入
          </button>
        </div>
        <span>{{ preview.sections.length }} 个执行段 · 1 次 Provider 调用</span>
      </div>

      <div v-if="activeView === 'outline'" class="flow-input-preview-sections" :class="{ 'is-stale': stale }">
        <article v-for="section in preview.sections" :key="`${section.kind}-${section.nodeId || section.title}`" class="flow-input-preview-section">
          <header>
            <span>{{ sectionKindLabel(section.kind) }}</span>
            <strong>{{ section.title }}</strong>
            <button
              v-if="section.nodeId && nodeActionLabel"
              type="button"
              class="flow-input-preview-node-link"
              :disabled="stale"
              :aria-label="`${nodeActionLabel}：${section.title}`"
              @click="openSectionNode(section.nodeId)"
            >
              {{ nodeActionLabel }}
              <Right class="flow-input-preview-node-link-icon" />
            </button>
          </header>
          <p>{{ section.content }}</p>
        </article>
      </div>
      <pre v-else :class="{ 'is-stale': stale }">{{ preview.executionInput }}</pre>
    </div>
    <div v-else class="flow-input-preview-status">
      <span>{{ error || (stale ? '执行上下文已更新，请刷新执行输入。' : '展开后将从服务端生成执行输入。') }}</span>
      <button v-if="error || stale" type="button" class="text-button" @click="loadPreview">
        {{ error ? '重试' : '刷新输入' }}
      </button>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, RefreshRight, Right } from '@element-plus/icons-vue'
import { previewFlowExecution } from '@/api/flows'
import type { FlowExecutionPreviewResponse, FlowExecutionSectionKind } from '@/types'

const props = withDefaults(
  defineProps<{
    flowId: string
    runtimeContext?: string
    variableValues?: Record<string, string>
    sourceVersion?: string
    dirty?: boolean
    beforeLoad?: () => boolean | Promise<boolean>
    nodeActionLabel?: string
  }>(),
  {
    runtimeContext: '',
    variableValues: () => ({}),
    sourceVersion: '',
    dirty: false,
    beforeLoad: undefined,
    nodeActionLabel: ''
  }
)

const emit = defineEmits<{
  openNode: [nodeId: string]
}>()

const preview = ref<FlowExecutionPreviewResponse | null>(null)
const loading = ref(false)
const stale = ref(false)
const error = ref('')
const requestVersion = ref(0)
const activeView = ref<'outline' | 'raw'>('outline')

const readinessLabel = computed(() => preview.value?.executable ? '本次执行输入已就绪' : '本次执行仍需补全')

const sectionKindLabels: Record<FlowExecutionSectionKind, string> = {
  objective: 'Flow 目标',
  'input-context': '输入上下文',
  'runtime-context': 'Run Brief',
  prompt: 'Prompt',
  'execution-guidance': '执行指令',
  'delivery-focus': '交付重点',
  'response-contract': '输出协议'
}

watch(() => props.flowId, resetPreview)

watch(
  [() => props.runtimeContext, () => props.variableValues, () => props.sourceVersion, () => props.dirty],
  invalidatePreview,
  { deep: true }
)

function invalidatePreview() {
  const hadPreviewOrRequest = Boolean(preview.value) || loading.value
  requestVersion.value += 1
  loading.value = false
  if (hadPreviewOrRequest) {
    stale.value = true
  }
  error.value = ''
}

function resetPreview() {
  requestVersion.value += 1
  preview.value = null
  loading.value = false
  stale.value = false
  error.value = ''
  activeView.value = 'outline'
}

function onToggle(event: Event) {
  const details = event.currentTarget as HTMLDetailsElement
  if (details.open && (!preview.value || stale.value)) {
    void loadPreview()
  }
}

async function loadPreview() {
  if (!props.flowId || loading.value) {
    return
  }

  if (props.beforeLoad && !(await props.beforeLoad())) {
    return
  }
  await nextTick()

  const flowId = props.flowId
  const version = requestVersion.value
  loading.value = true
  error.value = ''
  try {
    const { data } = await previewFlowExecution(flowId, {
      runtimeContext: props.runtimeContext,
      variableValues: { ...props.variableValues }
    })
    if (props.flowId === flowId && requestVersion.value === version) {
      preview.value = data
      stale.value = false
    }
  } catch (requestError: any) {
    if (props.flowId === flowId && requestVersion.value === version) {
      error.value = requestError.response?.data?.message || '执行输入生成失败'
    }
  } finally {
    if (props.flowId === flowId && requestVersion.value === version) {
      loading.value = false
    }
  }
}

function sectionKindLabel(kind: FlowExecutionSectionKind) {
  return sectionKindLabels[kind]
}

function openSectionNode(nodeId: string | null | undefined) {
  if (nodeId) {
    emit('openNode', nodeId)
  }
}

async function copyExecutionInput() {
  if (!preview.value || stale.value) {
    return
  }

  try {
    await navigator.clipboard.writeText(preview.value.executionInput)
    ElMessage.success('完整执行输入已复制')
  } catch {
    ElMessage.error('复制失败，请在 Raw 输入中手动复制')
  }
}
</script>
