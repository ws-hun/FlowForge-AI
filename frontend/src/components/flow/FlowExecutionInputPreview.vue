<template>
  <details class="flow-input-preview" @toggle="onToggle">
    <summary>查看服务端执行输入</summary>
    <div v-if="loading" class="flow-input-preview-status">
      正在按保存的 Flow 编译本次输入...
    </div>
    <div v-else-if="preview" class="flow-input-preview-content">
      <div class="flow-input-preview-meta">
        <span>{{ stale ? '执行上下文已更新' : '与本次执行保持一致' }}</span>
        <button v-if="stale" type="button" class="text-button" @click="loadPreview">
          刷新输入
        </button>
      </div>
      <pre>{{ preview.executionInput }}</pre>
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
import { nextTick, ref, watch } from 'vue'
import { previewFlowExecution } from '@/api/flows'
import type { FlowExecutionPreviewResponse } from '@/types'

const props = withDefaults(
  defineProps<{
    flowId: string
    runtimeContext?: string
    variableValues?: Record<string, string>
    sourceVersion?: string
    dirty?: boolean
    beforeLoad?: () => boolean | Promise<boolean>
  }>(),
  {
    runtimeContext: '',
    variableValues: () => ({}),
    sourceVersion: '',
    dirty: false,
    beforeLoad: undefined
  }
)

const preview = ref<FlowExecutionPreviewResponse | null>(null)
const loading = ref(false)
const stale = ref(false)
const error = ref('')
const requestVersion = ref(0)

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
</script>
