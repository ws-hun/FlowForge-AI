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
            {{
              formatExecutionSource(sourceRun.provider, sourceRun.model, sourceRun.totalTokens, sourceRun.durationMs) ||
              '来源未记录'
            }}
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
          :duration-ms="sourceRun.durationMs"
          compact
          :show-raw="false"
        />
        <div v-if="sourceRun.status !== 'failed'" class="run-comparison-pane-actions">
          <button type="button" class="secondary-button" @click="emit('continue', sourceRun)">用此结果继续</button>
        </div>
      </section>

      <section class="run-comparison-pane current">
        <div class="run-comparison-pane-header">
          <div>
            <span class="badge">{{ targetLabel }}</span>
            <time>{{ formatDate(targetRun.createdAt) }}</time>
          </div>
          <span class="run-provenance">
            {{
              formatExecutionSource(targetRun.provider, targetRun.model, targetRun.totalTokens, targetRun.durationMs) ||
              '来源未记录'
            }}
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
          :duration-ms="targetRun.durationMs"
          compact
          :show-raw="false"
        />
        <div v-if="targetRun.status !== 'failed'" class="run-comparison-pane-actions">
          <button type="button" class="secondary-button" @click="emit('continue', targetRun)">用此结果继续</button>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { formatExecutionSource } from '@/utils/aiProvider'
import type { TaskHistoryItem } from '@/types'

const props = withDefaults(defineProps<{
  open: boolean
  sourceRun: TaskHistoryItem | null
  targetRun: TaskHistoryItem | null
  mode?: 'rerun' | 'continuation'
}>(), {
  mode: 'rerun'
})

const emit = defineEmits<{
  close: []
  continue: [run: TaskHistoryItem]
}>()

const targetLabel = computed(() => (props.mode === 'continuation' ? '继续结果' : '本次重跑'))

function handleOpenChange(value: boolean) {
  if (!value) {
    emit('close')
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}
</script>
