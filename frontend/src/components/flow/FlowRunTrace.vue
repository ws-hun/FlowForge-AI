<template>
  <details class="flow-run-trace">
    <summary>
      <span>
        <strong>Server Run Trace</strong>
        <small>
          {{ executionModeLabel(trace.executionMode) }} · {{ trace.nodes.length }} nodes ·
          {{ trace.providerCallCount }} Provider call
        </small>
      </span>
      <em :class="trace.status">{{ trace.status === 'completed' ? 'Completed' : 'Failed' }}</em>
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
            <summary>Compiled content</summary>
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
  return mode === 'node-sequential' ? 'Node sequential' : 'Single-pass'
}

function statusLabel(status: FlowNodeRunTraceStatus) {
  const labels: Record<FlowNodeRunTraceStatus, string> = {
    prepared: 'Prepared',
    completed: 'Done',
    failed: 'Failed',
    skipped: 'Skipped'
  }
  return labels[status]
}
</script>
