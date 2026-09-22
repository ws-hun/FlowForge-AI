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
        <span class="section-kicker">运行对比</span>
        <strong>运行结果对比</strong>
      </div>
    </template>

    <div v-if="sourceRun && targetRun" class="run-comparison-body">
      <section class="run-input-comparison" :class="inputComparison.relation">
        <span></span>
        <div>
          <strong>{{ inputComparisonTitle }}</strong>
          <p>{{ inputComparisonDescription }}</p>
        </div>
      </section>

      <section class="run-input-comparison provider-input-comparison" :class="providerInputComparison.relation">
        <span></span>
        <div>
          <strong>{{ providerInputComparisonTitle }}</strong>
          <p>{{ providerInputComparisonDescription }}</p>
        </div>
      </section>

      <section class="run-input-comparison flow-origin-comparison" :class="flowOriginComparison.relation">
        <span></span>
        <div>
          <strong>{{ flowOriginComparisonTitle }}</strong>
          <p>{{ flowOriginComparisonDescription }}</p>
        </div>
      </section>

      <RunExecutionEvidenceComparison :source-run="sourceRun" :target-run="targetRun" />

      <div class="run-comparison-grid">
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
          <ExecutionInputArchive :input="sourceRun.input" title="来源执行输入" compact />
          <FlowRunTrace v-if="sourceRun.flowRunTrace" :trace="sourceRun.flowRunTrace" />
          <div v-if="sourceRun.status === 'failed'" class="failed-run-detail run-comparison-failure">
            <span class="section-kicker">执行错误</span>
            <strong>{{ sourceRun.errorMessage || sourceRun.result }}</strong>
            <p>这次失败记录及其节点状态保持不变，可与后续恢复结果直接核对。</p>
          </div>
          <AiResultDocument
            v-else
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
          <div class="run-comparison-pane-actions">
            <button
              v-if="flowIdFor(sourceRun)"
              type="button"
              class="ghost-button"
              @click="emit('open-flow', sourceRun)"
            >
              打开执行 Flow
            </button>
            <button
              v-if="originFor(sourceRun)"
              type="button"
              class="ghost-button"
              @click="emit('open-origin', sourceRun)"
            >
              {{ originLabel(sourceRun) }}
            </button>
            <button v-if="sourceRun.status !== 'failed'" type="button" class="secondary-button" @click="emit('continue', sourceRun)">
              用此结果继续
            </button>
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
          <ExecutionInputArchive :input="targetRun.input" title="本次执行输入" compact />
          <FlowRunTrace v-if="targetRun.flowRunTrace" :trace="targetRun.flowRunTrace" />
          <div v-if="targetRun.status === 'failed'" class="failed-run-detail run-comparison-failure">
            <span class="section-kicker">执行错误</span>
            <strong>{{ targetRun.errorMessage || targetRun.result }}</strong>
            <p>{{ targetFailureDescription }}</p>
          </div>
          <AiResultDocument
            v-else
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
          <div class="run-comparison-pane-actions">
            <button
              v-if="flowIdFor(targetRun)"
              type="button"
              class="ghost-button"
              @click="emit('open-flow', targetRun)"
            >
              打开执行 Flow
            </button>
            <button
              v-if="originFor(targetRun)"
              type="button"
              class="ghost-button"
              @click="emit('open-origin', targetRun)"
            >
              {{ originLabel(targetRun) }}
            </button>
            <button v-if="targetRun.status !== 'failed'" type="button" class="secondary-button" @click="emit('continue', targetRun)">
              用此结果继续
            </button>
          </div>
        </section>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import ExecutionInputArchive from '@/components/ai/ExecutionInputArchive.vue'
import RunExecutionEvidenceComparison from '@/components/ai/RunExecutionEvidenceComparison.vue'
import FlowRunTrace from '@/components/flow/FlowRunTrace.vue'
import { formatExecutionSource } from '@/utils/aiProvider'
import {
  compareRunExecutionInputs,
  compareRunFlowOrigins,
  compareRunProviderInputDeclarations,
  type RunFlowOriginKind
} from '@/utils/runComparison'
import type { TaskHistoryItem } from '@/types'

const props = withDefaults(defineProps<{
  open: boolean
  sourceRun: TaskHistoryItem | null
  targetRun: TaskHistoryItem | null
  mode?: 'rerun' | 'recovery' | 'continuation' | 'input-variant'
}>(), {
  mode: 'rerun'
})

const emit = defineEmits<{
  close: []
  continue: [run: TaskHistoryItem]
  'open-flow': [run: TaskHistoryItem]
  'open-origin': [run: TaskHistoryItem]
}>()

const targetLabel = computed(() => {
  if (props.mode === 'recovery') return '恢复运行'
  if (props.mode === 'continuation') return '继续结果'
  if (props.mode === 'input-variant') return '输入变体'
  return '本次重跑'
})

const targetFailureDescription = computed(() =>
  props.mode === 'recovery'
    ? '恢复运行仍未完成，固定输入、Provider 来源和节点失败位置已经保留。'
    : '重跑仍未完成，固定输入、Provider 来源和节点失败位置已经保留。'
)

const inputComparison = computed(() =>
  props.sourceRun && props.targetRun
    ? compareRunExecutionInputs(props.sourceRun, props.targetRun)
    : { relation: 'different' as const, verification: 'stored-text' as const }
)
const inputComparisonTitle = computed(() =>
  inputComparison.value.relation === 'same' ? '两次运行使用相同输入' : '两次运行输入已经变化'
)
const inputComparisonDescription = computed(() => {
  if (inputComparison.value.verification === 'fingerprint') {
    return inputComparison.value.relation === 'same'
      ? 'Provider 输入指纹一致，结果差异来自 Provider、模型或生成过程。'
      : 'Provider 输入指纹不同，对比结果时需要同时考虑输入变化。'
  }
  return inputComparison.value.relation === 'same'
    ? '历史记录缺少完整指纹，已按保存的执行输入文本确认一致。'
    : '历史记录缺少完整指纹，保存的执行输入文本存在差异。'
})

const providerInputComparison = computed(() =>
  props.sourceRun && props.targetRun
    ? compareRunProviderInputDeclarations(props.sourceRun, props.targetRun)
    : {
        relation: 'unavailable' as const,
        verification: 'saved-execution-plan' as const,
        sourceInputCount: null,
        targetInputCount: null
      }
)
const providerInputComparisonTitle = computed(() => {
  if (providerInputComparison.value.relation === 'same') return 'Provider 输入结构一致'
  if (providerInputComparison.value.relation === 'different') return 'Provider 输入结构已经变化'
  return 'Provider 输入结构无法核验'
})
const providerInputComparisonDescription = computed(() => {
  const comparison = providerInputComparison.value
  if (comparison.relation === 'unavailable') {
    return '至少一次运行没有保存 v5 输入声明，本次对比不根据当前 Flow 反向推断。'
  }
  const counts = `${comparison.sourceInputCount} 项 → ${comparison.targetInputCount} 项`
  return comparison.relation === 'same'
    ? `已保存的有序 Artifact 声明一致（${counts}）。`
    : `已保存的有序 Artifact key、类型或来源发生变化（${counts}）。`
})

const flowOriginComparison = computed(() =>
  props.sourceRun && props.targetRun
    ? compareRunFlowOrigins(props.sourceRun, props.targetRun)
    : {
        relation: 'unavailable' as const,
        flowRelation: 'unavailable' as const,
        originRelation: 'unavailable' as const,
        verification: 'saved-flow-snapshot' as const,
        source: null,
        target: null,
        differences: []
      }
)
const flowOriginComparisonTitle = computed(() => {
  const comparison = flowOriginComparison.value
  if (comparison.flowRelation === 'unavailable') return 'Flow 创作来源无法核验'
  if (comparison.flowRelation === 'different') {
    if (comparison.originRelation === 'same') return 'Flow 资产不同，但创作来源一致'
    if (comparison.originRelation === 'different') return 'Flow 资产与创作来源都已变化'
    return 'Flow 资产不同，部分来源未记录'
  }
  if (comparison.originRelation === 'same') return '来自同一 Flow 与创作来源'
  if (comparison.originRelation === 'different') return '同一 Flow 的来源证据存在差异'
  return 'Flow 身份一致，创作来源未记录'
})
const flowOriginComparisonDescription = computed(() => {
  const comparison = flowOriginComparison.value
  if (!comparison.source || !comparison.target) {
    return '至少一次运行没有保存 Flow 快照，本次对比不会根据当前资产反向补写来源。'
  }
  const flowChange = `Flow「${comparison.source.flowTitle}」→「${comparison.target.flowTitle}」`
  if (comparison.originRelation === 'unavailable') {
    return comparison.flowRelation === 'same'
      ? `两侧快照都指向「${comparison.source.flowTitle}」，但至少一侧没有记录派生来源。`
      : `${flowChange}；至少一侧没有记录派生来源，未知部分保持未核验。`
  }
  const sourceOrigin = formatFlowOrigin(comparison.source.originKind, comparison.source.originTitle)
  const targetOrigin = formatFlowOrigin(comparison.target.originKind, comparison.target.originTitle)
  if (comparison.flowRelation === 'same' && comparison.originRelation === 'same') {
    return `两侧快照都指向「${comparison.source.flowTitle}」，并保留同一${sourceOrigin}。`
  }
  if (comparison.originRelation === 'same') {
    return `${flowChange}；两个资产都由同一${sourceOrigin}创建。`
  }
  return `${flowChange}；创作来源由${sourceOrigin}变为${targetOrigin}。`
})

function formatFlowOrigin(kind: RunFlowOriginKind | null, title: string | null) {
  if (!kind) return '未记录来源'
  const kindLabel = kind === 'result' ? 'Result' : kind === 'prompt' ? 'Prompt' : 'Flow'
  return title ? `${kindLabel}「${title}」` : kindLabel
}

function flowIdFor(run: TaskHistoryItem) {
  return run.flowRunSnapshot?.flowId || run.sourceFlowId || null
}

function originFor(run: TaskHistoryItem) {
  const snapshot = run.flowRunSnapshot
  if (snapshot?.sourceTaskId) {
    return { kind: 'result' as const, id: snapshot.sourceTaskId }
  }
  if (snapshot?.sourcePromptId) {
    return { kind: 'prompt' as const, id: snapshot.sourcePromptId }
  }
  if (snapshot?.sourceFlowId) {
    return { kind: 'flow' as const, id: snapshot.sourceFlowId }
  }
  if (run.sourcePromptId) {
    return { kind: 'prompt' as const, id: run.sourcePromptId }
  }
  if (run.sourceFlowId) {
    return { kind: 'flow' as const, id: run.sourceFlowId }
  }
  return null
}

function originLabel(run: TaskHistoryItem) {
  const origin = originFor(run)
  if (!origin) return '打开创作来源'
  return origin.kind === 'result'
    ? '打开来源 Result'
    : origin.kind === 'prompt'
      ? '打开来源 Prompt'
      : '打开来源 Flow'
}

function handleOpenChange(value: boolean) {
  if (!value) {
    emit('close')
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}
</script>
