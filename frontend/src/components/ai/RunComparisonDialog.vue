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

      <section class="run-input-comparison execution-evidence-comparison" :class="providerExecutionComparison.relation">
        <span></span>
        <div class="execution-evidence-body">
          <div>
            <strong>{{ providerExecutionComparisonTitle }}</strong>
            <p>{{ providerExecutionComparisonDescription }}</p>
          </div>
          <div class="execution-evidence-grid">
            <div class="execution-evidence-pane">
              <span>来源运行</span>
              <dl>
                <div><dt>Provider</dt><dd>{{ providerExecutionValue(providerExecutionComparison.source.provider) }}</dd></div>
                <div><dt>模型</dt><dd>{{ providerExecutionValue(providerExecutionComparison.source.model) }}</dd></div>
                <div><dt>运行状态</dt><dd>{{ runStatusLabel(providerExecutionComparison.source.status) }}</dd></div>
                <div><dt>调用 / Attempt</dt><dd>{{ callEvidence(providerExecutionComparison.source.providerCallCount, providerExecutionComparison.source.attemptCount) }}</dd></div>
              </dl>
            </div>
            <div class="execution-evidence-pane current">
              <span>目标运行</span>
              <dl>
                <div><dt>Provider</dt><dd>{{ providerExecutionValue(providerExecutionComparison.target.provider) }}</dd></div>
                <div><dt>模型</dt><dd>{{ providerExecutionValue(providerExecutionComparison.target.model) }}</dd></div>
                <div><dt>运行状态</dt><dd>{{ runStatusLabel(providerExecutionComparison.target.status) }}</dd></div>
                <div><dt>调用 / Attempt</dt><dd>{{ callEvidence(providerExecutionComparison.target.providerCallCount, providerExecutionComparison.target.attemptCount) }}</dd></div>
              </dl>
            </div>
          </div>
          <ul v-if="providerExecutionComparison.differences.length" class="execution-evidence-differences">
            <li v-for="difference in providerExecutionComparison.differences" :key="difference">
              {{ providerExecutionDifferenceLabel(difference) }}
            </li>
          </ul>
        </div>
      </section>

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
            <span class="section-kicker">Execution Error</span>
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
          <ExecutionInputArchive :input="targetRun.input" title="本次执行输入" compact />
          <FlowRunTrace v-if="targetRun.flowRunTrace" :trace="targetRun.flowRunTrace" />
          <div v-if="targetRun.status === 'failed'" class="failed-run-detail run-comparison-failure">
            <span class="section-kicker">Execution Error</span>
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
          <div v-if="targetRun.status !== 'failed'" class="run-comparison-pane-actions">
            <button type="button" class="secondary-button" @click="emit('continue', targetRun)">用此结果继续</button>
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
import FlowRunTrace from '@/components/flow/FlowRunTrace.vue'
import { formatExecutionSource, formatProviderName } from '@/utils/aiProvider'
import {
  compareRunExecutionInputs,
  compareRunProviderExecution,
  compareRunProviderInputDeclarations,
  type RunProviderExecutionDifference
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

const providerExecutionComparison = computed(() =>
  props.sourceRun && props.targetRun
    ? compareRunProviderExecution(props.sourceRun, props.targetRun)
    : {
        relation: 'unavailable' as const,
        verification: 'unavailable' as const,
        source: {
          provider: null,
          model: null,
          status: null,
          providerCallCount: null,
          attemptCount: null
        },
        target: {
          provider: null,
          model: null,
          status: null,
          providerCallCount: null,
          attemptCount: null
        },
        differences: [] as RunProviderExecutionDifference[]
      }
)
const providerExecutionComparisonTitle = computed(() => {
  if (providerExecutionComparison.value.relation === 'same') return '两次运行执行环境一致'
  if (providerExecutionComparison.value.relation === 'different') return '两次运行执行契约存在差异'
  return '执行证据无法完整核验'
})
const providerExecutionComparisonDescription = computed(() => {
  const comparison = providerExecutionComparison.value
  if (comparison.verification === 'flow-runtime-contract') {
    return comparison.relation === 'same'
      ? '两次运行均保存了 v5 执行契约，可确认 Provider、模型和单次调用边界一致。'
      : '差异来自运行时保存的 Provider、模型、状态或调用边界；不会从当前设置反向推断。'
  }
  if (comparison.verification === 'task-metadata') {
    return '至少一侧是旧运行，仅按历史 Task 中保存的执行元数据比较；Attempt 数量保持未知。'
  }
  return '至少一侧缺少可用的 Provider 执行元数据，本次对比不会补造运行证据。'
})

function providerExecutionValue(value: string | null) {
  return value ? formatProviderName(value) : '未记录'
}

function runStatusLabel(status: TaskHistoryItem['status']) {
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  return '未记录'
}

function callEvidence(callCount: number | null, attemptCount: number | null) {
  return `${callCount === null ? '未核验' : `${callCount} 次`} / ${attemptCount === null ? '未核验' : `${attemptCount} 次`}`
}

function providerExecutionDifferenceLabel(difference: RunProviderExecutionDifference) {
  const labels: Record<RunProviderExecutionDifference, string> = {
    provider: 'Provider 不同',
    model: '模型不同',
    status: '运行状态不同',
    'provider-call-count': 'Provider 调用次数不同',
    'attempt-count': 'Attempt 次数不同'
  }
  return labels[difference]
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
