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
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import type { FlowRunSnapshot } from '@/types'

const props = withDefaults(
  defineProps<{
    snapshot: FlowRunSnapshot
    canCreateFlow?: boolean
    canReuseRunSettings?: boolean
    creating?: boolean
  }>(),
  {
    canCreateFlow: false,
    canReuseRunSettings: false,
    creating: false
  }
)

const emit = defineEmits<{
  'create-flow': [snapshot: FlowRunSnapshot]
  'reuse-run-settings': [snapshot: FlowRunSnapshot]
}>()

const variableEntries = computed(() => Object.entries(props.snapshot.variableValues || {}))
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
</script>
