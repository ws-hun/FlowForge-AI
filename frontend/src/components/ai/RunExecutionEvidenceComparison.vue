<template>
  <section class="run-input-comparison execution-evidence-comparison" :class="comparison.relation">
    <span></span>
    <div class="execution-evidence-body">
      <div>
        <strong>{{ title }}</strong>
        <p>{{ description }}</p>
      </div>
      <div class="execution-evidence-grid">
        <div class="execution-evidence-pane">
          <span>来源运行</span>
          <dl>
            <div><dt>Provider</dt><dd>{{ evidenceValue(comparison.source.provider) }}</dd></div>
            <div><dt>模型</dt><dd>{{ evidenceValue(comparison.source.model) }}</dd></div>
            <div><dt>运行状态</dt><dd>{{ runStatusLabel(comparison.source.status) }}</dd></div>
            <div><dt>调用 / Attempt</dt><dd>{{ callEvidence(comparison.source.providerCallCount, comparison.source.attemptCount) }}</dd></div>
          </dl>
        </div>
        <div class="execution-evidence-pane current">
          <span>目标运行</span>
          <dl>
            <div><dt>Provider</dt><dd>{{ evidenceValue(comparison.target.provider) }}</dd></div>
            <div><dt>模型</dt><dd>{{ evidenceValue(comparison.target.model) }}</dd></div>
            <div><dt>运行状态</dt><dd>{{ runStatusLabel(comparison.target.status) }}</dd></div>
            <div><dt>调用 / Attempt</dt><dd>{{ callEvidence(comparison.target.providerCallCount, comparison.target.attemptCount) }}</dd></div>
          </dl>
        </div>
      </div>
      <ul v-if="comparison.differences.length || comparison.unknown.length" class="execution-evidence-differences">
        <li v-for="difference in comparison.differences" :key="difference">
          {{ differenceLabel(difference) }}
        </li>
        <li
          v-for="difference in comparison.unknown"
          :key="`unknown-${difference}`"
          class="unknown"
        >
          {{ differenceLabel(difference) }}未核验
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatProviderName } from '@/utils/aiProvider'
import {
  compareRunProviderExecution,
  type RunProviderExecutionDifference
} from '@/utils/runComparison'
import type { TaskHistoryItem } from '@/types'

const props = defineProps<{
  sourceRun: TaskHistoryItem
  targetRun: TaskHistoryItem
}>()

const comparison = computed(() => compareRunProviderExecution(props.sourceRun, props.targetRun))

const title = computed(() => {
  if (comparison.value.relation === 'same') return '两次运行执行环境一致'
  if (comparison.value.relation === 'different') return '两次运行执行契约存在差异'
  return '执行证据无法完整核验'
})

const description = computed(() => {
  if (comparison.value.unknown.length) {
    return comparison.value.relation === 'different'
      ? '已发现可比较字段的差异，但部分历史证据缺失，未对未知字段作结论。'
      : '部分历史证据缺失，只有双方都保存的字段参与核验。'
  }
  if (comparison.value.verification === 'flow-runtime-contract') {
    return comparison.value.relation === 'same'
      ? '两次运行均保存了 v5 执行契约，可确认 Provider、模型和单次调用边界一致。'
      : '差异来自运行时保存的 Provider、模型、状态或调用边界；不会从当前设置反向推断。'
  }
  if (comparison.value.verification === 'task-metadata') {
    return '至少一侧是旧运行，仅按历史 Task 中保存的执行元数据比较；Attempt 数量保持未知。'
  }
  return '至少一侧缺少可用的 Provider 执行元数据，本次对比不会补造运行证据。'
})

function evidenceValue(value: string | null) {
  return value ? formatProviderName(value) : '未记录'
}

function runStatusLabel(status: TaskHistoryItem['status']) {
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  return '未记录'
}

function callEvidence(callCount: number | null, attemptCount: number | null) {
  const calls = callCount === null ? '未核验' : `${callCount} 次`
  const attempts = attemptCount === null ? '未核验' : `${attemptCount} 次`
  return `${calls} / ${attempts}`
}

function differenceLabel(difference: RunProviderExecutionDifference) {
  const labels: Record<RunProviderExecutionDifference, string> = {
    provider: 'Provider 不同',
    model: '模型不同',
    status: '运行状态不同',
    'provider-call-count': 'Provider 调用次数不同',
    'attempt-count': 'Attempt 次数不同'
  }
  return labels[difference]
}
</script>
