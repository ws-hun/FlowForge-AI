<template>
  <el-dialog
    :model-value="open"
    width="min(1180px, calc(100vw - 32px))"
    class="run-comparison-dialog"
    append-to-body
    @update:model-value="handleOpenChange"
  >
    <template #header>
      <div class="run-comparison-heading">
        <span class="section-kicker">Run Comparison</span>
        <strong>运行结果对比</strong>
      </div>
    </template>

    <div v-if="sourceRun && targetRun" class="run-comparison-grid">
      <section class="run-comparison-pane">
        <div class="run-comparison-pane-header">
          <div>
            <span class="badge">来源运行</span>
            <time>{{ formatDate(sourceRun.createdAt) }}</time>
          </div>
          <span class="run-provenance">
            {{ formatExecutionSource(sourceRun.provider, sourceRun.model, sourceRun.totalTokens) || '来源未记录' }}
          </span>
        </div>
        <AiResultDocument
          :summary="sourceRun.summary"
          :result="sourceRun.result"
          :provider="sourceRun.provider"
          :model="sourceRun.model"
          :input-tokens="sourceRun.inputTokens"
          :output-tokens="sourceRun.outputTokens"
          :total-tokens="sourceRun.totalTokens"
          compact
          :show-raw="false"
        />
      </section>

      <section class="run-comparison-pane current">
        <div class="run-comparison-pane-header">
          <div>
            <span class="badge">本次重跑</span>
            <time>{{ formatDate(targetRun.createdAt) }}</time>
          </div>
          <span class="run-provenance">
            {{ formatExecutionSource(targetRun.provider, targetRun.model, targetRun.totalTokens) || '来源未记录' }}
          </span>
        </div>
        <AiResultDocument
          :summary="targetRun.summary"
          :result="targetRun.result"
          :provider="targetRun.provider"
          :model="targetRun.model"
          :input-tokens="targetRun.inputTokens"
          :output-tokens="targetRun.outputTokens"
          :total-tokens="targetRun.totalTokens"
          compact
          :show-raw="false"
        />
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { formatExecutionSource } from '@/utils/aiProvider'
import type { TaskHistoryItem } from '@/types'

defineProps<{
  open: boolean
  sourceRun: TaskHistoryItem | null
  targetRun: TaskHistoryItem | null
}>()

const emit = defineEmits<{
  close: []
}>()

function handleOpenChange(value: boolean) {
  if (!value) {
    emit('close')
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}
</script>
