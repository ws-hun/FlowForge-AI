<template>
  <section class="run-input-comparison flow-snapshot-content-comparison" :class="comparison.relation">
    <span></span>
    <div class="flow-snapshot-comparison-body">
      <div>
        <strong>{{ title }}</strong>
        <p>{{ description }}</p>
      </div>
      <ul v-if="comparison.changes.length" class="flow-snapshot-change-list">
        <li v-for="change in visibleChanges" :key="change.key">
          <span>{{ changeLabel(change.kind) }}</span>
          <strong>{{ change.title }}</strong>
        </li>
      </ul>
      <small v-if="remainingCount">另有 {{ remainingCount }} 处变化</small>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  compareRunFlowSnapshots,
  type RunFlowSnapshotChangeKind
} from '@/utils/runComparison'
import type { TaskHistoryItem } from '@/types'

const props = defineProps<{
  sourceRun: TaskHistoryItem
  targetRun: TaskHistoryItem
}>()

const comparison = computed(() => compareRunFlowSnapshots(props.sourceRun, props.targetRun))
const visibleChanges = computed(() => comparison.value.changes.slice(0, 6))
const remainingCount = computed(() => Math.max(0, comparison.value.changes.length - visibleChanges.value.length))

const title = computed(() => {
  if (comparison.value.relation === 'same') return 'Flow 执行快照一致'
  if (comparison.value.relation === 'different') return 'Flow 执行上下文已经变化'
  return 'Flow 执行快照无法核验'
})

const description = computed(() => {
  const value = comparison.value
  if (value.relation === 'unavailable') {
    return '至少一次运行没有保存 Flow 快照，本次不会根据当前 Flow 反向推断。'
  }
  const nodeCounts = `${value.sourceNodeCount} 个节点 → ${value.targetNodeCount} 个节点`
  return value.relation === 'same'
    ? `两次运行保存了相同的目标、节点、运行说明与变量（${nodeCounts}）。`
    : `不可变快照中发现 ${value.changes.length} 处变化（${nodeCounts}）。`
})

function changeLabel(kind: RunFlowSnapshotChangeKind) {
  const labels: Record<RunFlowSnapshotChangeKind, string> = {
    title: '名称变化',
    description: '目标变化',
    'runtime-context': '说明变化',
    'node-added': '新增节点',
    'node-removed': '移除节点',
    'node-updated': '更新节点',
    'node-reordered': '调整顺序',
    'variable-added': '新增变量',
    'variable-removed': '移除变量',
    'variable-updated': '更新变量'
  }
  return labels[kind]
}
</script>
