<template>
  <details class="flow-run-snapshot">
    <summary>
      <span>
        <strong>已固定运行快照</strong>
        <small>该结果保留了执行当时的 Flow 与输入上下文</small>
      </span>
      <em>{{ formatDate(snapshot.flowUpdatedAt) }}</em>
    </summary>

    <div class="flow-run-snapshot-body">
      <div class="flow-run-snapshot-heading">
        <span>Flow snapshot</span>
        <strong>{{ snapshot.title }}</strong>
        <p>{{ snapshot.description }}</p>
      </div>

      <div v-if="snapshot.sourceFlowId" class="flow-run-snapshot-origin">
        <div>
          <span>Derived from</span>
          <strong>
            {{ snapshot.sourceFlowTitle || '来源 Flow' }}
            <small v-if="snapshot.sourceFlowVersionNumber">v{{ snapshot.sourceFlowVersionNumber }}</small>
          </strong>
          <p>本次执行的 Flow 是从该来源继续创建的独立资产。</p>
        </div>
        <button
          v-if="canOpenSourceFlow"
          type="button"
          class="snapshot-source-action"
          @click.stop="emit('open-source-flow', snapshot)"
        >
          <el-icon><Back /></el-icon>
          打开来源 Flow
        </button>
      </div>

      <div class="flow-run-snapshot-nodes" aria-label="Flow 节点顺序">
        <span v-for="(node, index) in snapshot.nodes" :key="node.id">
          <i>{{ index + 1 }}</i>
          {{ node.title }}
        </span>
      </div>

      <div v-if="snapshot.runtimeContext" class="flow-run-snapshot-block">
        <span>本次运行说明</span>
        <p>{{ snapshot.runtimeContext }}</p>
      </div>

      <div v-if="variableEntries.length" class="flow-run-snapshot-block">
        <span>Flow 变量</span>
        <div class="flow-run-snapshot-variables">
          <div v-for="[name, value] in variableEntries" :key="name">
            <code>{{ '{' + name + '}' }}</code>
            <p>{{ value || '未填写' }}</p>
          </div>
        </div>
      </div>

      <section
        v-if="snapshotDiff"
        class="flow-run-snapshot-comparison"
        :class="{ unchanged: !snapshotDiff.hasChanges }"
      >
        <div class="flow-run-snapshot-comparison-heading">
          <span>与当前 Flow 对比</span>
          <strong>
            {{ snapshotDiff.hasChanges ? `当前 Flow 已有 ${snapshotDiff.changeCount} 处变化` : '与当前 Flow 一致' }}
          </strong>
        </div>
        <ul v-if="snapshotDiff.hasChanges">
          <li v-for="item in visibleComparisonItems" :key="item.key">
            <span>{{ item.label }}</span>
            <p>{{ item.detail }}</p>
          </li>
        </ul>
        <small v-if="remainingComparisonCount">另有 {{ remainingComparisonCount }} 处变化</small>
      </section>

      <div v-if="showActions" class="snapshot-actions">
        <button
          v-if="canReuseRunSettings && hasReusableSettings"
          type="button"
          class="snapshot-reuse-action"
          @click.stop="emit('reuse-run-settings', snapshot)"
        >
          <el-icon><RefreshRight /></el-icon>
          复用本次运行配置
        </button>
        <button
          v-if="canCreateFlow"
          type="button"
          class="snapshot-create-action"
          :disabled="creating"
          @click.stop="emit('create-flow', snapshot)"
        >
          <el-icon><Plus /></el-icon>
          {{ creating ? '正在创建 Flow...' : '以此为起点创建 Flow' }}
        </button>
      </div>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Back, Plus, RefreshRight } from '@element-plus/icons-vue'
import { compareFlowRunSnapshot } from '@/utils/flowRunSnapshots'
import type { FlowDraft, FlowRunSnapshot } from '@/types'

const props = withDefaults(
  defineProps<{
    snapshot: FlowRunSnapshot
    currentFlow?: FlowDraft | null
    canCreateFlow?: boolean
    canReuseRunSettings?: boolean
    canOpenSourceFlow?: boolean
    creating?: boolean
  }>(),
  {
    canCreateFlow: false,
    currentFlow: null,
    canReuseRunSettings: false,
    canOpenSourceFlow: false,
    creating: false
  }
)

const emit = defineEmits<{
  'create-flow': [snapshot: FlowRunSnapshot]
  'reuse-run-settings': [snapshot: FlowRunSnapshot]
  'open-source-flow': [snapshot: FlowRunSnapshot]
}>()

const variableEntries = computed(() => Object.entries(props.snapshot.variableValues || {}))
const snapshotDiff = computed(() => {
  if (!props.currentFlow || props.currentFlow.id !== props.snapshot.flowId) {
    return null
  }
  return compareFlowRunSnapshot(props.currentFlow, props.snapshot)
})
const comparisonItems = computed(() => {
  const diff = snapshotDiff.value
  if (!diff) {
    return []
  }
  return [
    ...(diff.titleChanged
      ? [{ key: 'title', label: '标题', detail: `当前为“${props.currentFlow?.title || ''}”` }]
      : []),
    ...(diff.descriptionChanged
      ? [{ key: 'description', label: '目标', detail: '当前 Flow 的创作目标已修改' }]
      : []),
    ...diff.nodeChanges.map((change) => ({
      key: `${change.kind}-${change.id}`,
      label: nodeChangeLabel(change.kind),
      detail: change.title
    }))
  ]
})
const visibleComparisonItems = computed(() => comparisonItems.value.slice(0, 5))
const remainingComparisonCount = computed(() => Math.max(0, comparisonItems.value.length - 5))
const hasReusableSettings = computed(() =>
  Boolean(
    props.snapshot.runtimeContext?.trim() ||
      variableEntries.value.some(([, value]) => value?.trim())
  )
)
const showActions = computed(() => props.canCreateFlow || (props.canReuseRunSettings && hasReusableSettings.value))

function formatDate(value: string) {
  if (!value) {
    return '执行时版本'
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function nodeChangeLabel(kind: 'added' | 'removed' | 'updated' | 'reordered') {
  return {
    added: '新增节点',
    removed: '已移除',
    updated: '节点已修改',
    reordered: '顺序已调整'
  }[kind]
}
</script>
