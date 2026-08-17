<template>
  <details class="flow-run-trace">
    <summary>
      <span>
        <strong>服务端运行轨迹</strong>
        <small>
          <template v-if="trace.runId">运行 <code :title="trace.runId">{{ shortRunId(trace.runId) }}</code> · </template>
          <template v-if="trace.inputSource">{{ inputSourceLabel(trace.inputSource) }} · </template>
          <template v-if="trace.replayedFromTaskId">
            来源 <code :title="trace.replayedFromTaskId">{{ shortRunId(trace.replayedFromTaskId) }}</code> ·
          </template>
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
      <FlowExecutionPlan
        v-if="trace.executionPlan"
        :plan="trace.executionPlan"
        eyebrow="Persisted Plan"
        title="本次运行保存的执行路径"
        :node-action-label="nodeActionLabel"
        :navigable-node-ids="navigableNodeIds"
        @open-node="emit('openNode', $event)"
      />
      <p v-else class="flow-run-trace-legacy-plan">旧运行未保存节点执行计划，以下仅展示当时保留的节点状态。</p>
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
          <div
            v-if="node.outputArtifact"
            class="flow-run-trace-artifact"
            :class="node.outputArtifact.state"
          >
            <span class="flow-run-trace-artifact-dot"></span>
            <div class="flow-run-trace-artifact-meta">
              <strong>{{ flowArtifactTypeLabel(node.outputArtifact.type) }}</strong>
              <small>
                {{ flowArtifactStorageLabel(node.outputArtifact.storage) }}
                <template v-if="node.outputArtifact.contentFingerprint">
                  · <code :title="node.outputArtifact.contentFingerprint">SHA {{ shortFingerprint(node.outputArtifact.contentFingerprint) }}</code>
                </template>
              </small>
            </div>
            <div class="flow-run-trace-artifact-actions">
              <em>{{ flowArtifactStateLabel(node.outputArtifact.state) }}</em>
              <button
                v-if="canInspectArtifact(node)"
                type="button"
                :disabled="loadingArtifactKey === node.outputArtifact.key"
                @click="toggleArtifact(node)"
              >
                <Loading
                  v-if="loadingArtifactKey === node.outputArtifact.key"
                  class="flow-run-trace-artifact-loading"
                />
                <View v-else />
                {{
                  loadingArtifactKey === node.outputArtifact.key
                    ? '加载中'
                    : artifactIsOpen(node.outputArtifact.key)
                      ? '收起产物'
                      : '查看产物'
                }}
              </button>
            </div>
          </div>
          <Transition name="artifact-reveal">
            <section
              v-if="node.outputArtifact && artifactIsOpen(node.outputArtifact.key)"
              class="flow-run-trace-artifact-detail"
            >
              <header>
                <span>{{ artifactDetails[node.outputArtifact.key]?.mediaType || 'text/plain' }}</span>
                <button
                  type="button"
                  title="复制节点产物"
                  aria-label="复制节点产物"
                  @click="copyArtifact(node.outputArtifact.key)"
                >
                  <CopyDocument />
                </button>
              </header>
              <pre>{{ artifactDetails[node.outputArtifact.key]?.payload }}</pre>
            </section>
          </Transition>
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Loading, Right, View } from '@element-plus/icons-vue'
import type {
  FlowExecutionMode,
  FlowNodeArtifactDetail,
  FlowNodeRunTrace,
  FlowNodeRunTraceStatus,
  FlowNodeType,
  FlowRunTrace
} from '@/types'
import { getTaskArtifact } from '@/api/tasks'
import FlowExecutionPlan from '@/components/flow/FlowExecutionPlan.vue'
import {
  flowArtifactStateLabel,
  flowArtifactStorageLabel,
  flowArtifactTypeLabel
} from '@/utils/flowExecutionPlan'

const props = withDefaults(
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

const artifactDetails = ref<Record<string, FlowNodeArtifactDetail>>({})
const openArtifactKeys = ref<Record<string, boolean>>({})
const loadingArtifactKey = ref('')

function canInspectArtifact(node: FlowNodeRunTrace) {
  return Boolean(
    props.trace.runId
      && node.outputArtifact?.storage === 'node-artifact'
      && node.outputArtifact.state === 'materialized'
  )
}

function artifactIsOpen(artifactKey: string) {
  return Boolean(openArtifactKeys.value[artifactKey])
}

async function toggleArtifact(node: FlowNodeRunTrace) {
  const artifact = node.outputArtifact
  const taskId = props.trace.runId
  if (!artifact || !taskId || !canInspectArtifact(node)) {
    return
  }
  if (artifactDetails.value[artifact.key]) {
    openArtifactKeys.value[artifact.key] = !artifactIsOpen(artifact.key)
    return
  }

  loadingArtifactKey.value = artifact.key
  try {
    const { data } = await getTaskArtifact(taskId, artifact.key)
    artifactDetails.value[artifact.key] = data
    openArtifactKeys.value[artifact.key] = true
  } catch {
    ElMessage.error('节点产物加载失败')
  } finally {
    loadingArtifactKey.value = ''
  }
}

async function copyArtifact(artifactKey: string) {
  try {
    await navigator.clipboard.writeText(artifactDetails.value[artifactKey]?.payload || '')
    ElMessage.success('节点产物已复制')
  } catch {
    ElMessage.error('复制失败，请展开后手动复制')
  }
}

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

function shortRunId(runId: string) {
  return runId.slice(0, 8)
}

function inputSourceLabel(source: FlowRunTrace['inputSource']) {
  return source === 'stored-input-replay' ? '历史输入重放' : '当前 Flow 编译'
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
