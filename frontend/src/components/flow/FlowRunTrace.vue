<template>
  <details class="flow-run-trace">
    <summary>
      <span>
        <strong>服务端运行轨迹</strong>
        <small>
          {{ executionModeLabel(trace.executionMode) }} · {{ trace.nodes.length }} 个节点 ·
          {{ trace.providerCallCount }} 次 Provider 调用
          <template v-if="trace.compilerVersion"> · {{ trace.compilerVersion }}</template>
          <template v-if="trace.executionInputFingerprint">
            · <code :title="trace.executionInputFingerprint">输入 {{ shortFingerprint(trace.executionInputFingerprint) }}</code>
          </template>
        </small>
      </span>
      <em :class="trace.status">{{ trace.status === 'completed' ? '已完成' : '失败' }}</em>
    </summary>

    <div class="flow-run-trace-body">
      <article v-for="(node, index) in trace.nodes" :key="node.nodeId" class="flow-run-trace-node">
        <div class="flow-run-trace-rail" :class="node.status">
          <span>{{ index + 1 }}</span>
        </div>
        <div class="flow-run-trace-node-body">
          <header>
            <div>
              <span>{{ nodeTypeLabel(node.nodeType) }}</span>
              <strong>{{ node.title }}</strong>
            </div>
            <em :class="node.status">{{ statusLabel(node.status) }}</em>
          </header>

          <details v-if="node.compiledContent" class="flow-run-trace-content">
            <summary>编译后内容</summary>
            <pre>{{ node.compiledContent }}</pre>
          </details>
          <p v-if="node.outputSummary" class="flow-run-trace-output">{{ node.outputSummary }}</p>
          <p v-if="node.errorMessage" class="flow-run-trace-error">{{ node.errorMessage }}</p>
          <button
            v-if="nodeActionLabel && navigableNodeIds.includes(node.nodeId)"
            type="button"
            class="flow-run-trace-node-action"
            @click="emit('openNode', node.nodeId)"
          >
            {{ nodeActionLabel }}
            <Right class="flow-run-trace-node-action-icon" />
          </button>
        </div>
      </article>
    </div>
  </details>
</template>

<script setup lang="ts">
import { Right } from '@element-plus/icons-vue'
import type { FlowExecutionMode, FlowNodeRunTraceStatus, FlowNodeType, FlowRunTrace } from '@/types'

withDefaults(
  defineProps<{
    trace: FlowRunTrace
    nodeActionLabel?: string
    navigableNodeIds?: string[]
  }>(),
  {
    nodeActionLabel: '',
    navigableNodeIds: () => []
  }
)

const emit = defineEmits<{
  openNode: [nodeId: string]
}>()

function nodeTypeLabel(type: FlowNodeType) {
  const labels: Record<FlowNodeType, string> = {
    input: 'Input',
    prompt: 'Prompt',
    'ai-task': 'AI Task',
    output: 'Output'
  }
  return labels[type]
}

function executionModeLabel(mode: FlowExecutionMode | null | undefined) {
  return mode === 'node-sequential' ? '节点顺序执行' : '单次编译执行'
}

function shortFingerprint(fingerprint: string) {
  return fingerprint.slice(0, 10)
}

function statusLabel(status: FlowNodeRunTraceStatus) {
  const labels: Record<FlowNodeRunTraceStatus, string> = {
    prepared: '已准备',
    completed: '已完成',
    failed: '失败',
    skipped: '已跳过'
  }
  return labels[status]
}
</script>
