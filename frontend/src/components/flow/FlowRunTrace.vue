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
                      ? artifactCloseLabel(node)
                      : artifactActionLabel(node)
                }}
              </button>
            </div>
          </div>
          <Transition name="artifact-reveal">
            <section
              v-if="node.outputArtifact && artifactIsOpen(node.outputArtifact.key)"
              class="flow-run-trace-artifact-detail"
              :data-artifact-key="node.outputArtifact.key"
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
              <FlowProviderAttempts
                v-if="artifactDetails[node.outputArtifact.key]?.providerAttempts?.length"
                :attempts="artifactDetails[node.outputArtifact.key]?.providerAttempts || []"
                :policy="artifactDetails[node.outputArtifact.key]?.providerAttemptPolicy"
              />
              <div
                v-else-if="artifactDetails[node.outputArtifact.key]?.providerCall"
                class="flow-run-trace-provider-call"
                :class="artifactDetails[node.outputArtifact.key]?.providerCall?.status"
              >
                <span></span>
                <div>
                  <small>真实 Provider 调用</small>
                  <strong>
                    {{ providerCallSource(artifactDetails[node.outputArtifact.key]) || 'Provider 来源未报告' }}
                  </strong>
                  <p>
                    {{ providerCallStatus(artifactDetails[node.outputArtifact.key]) }}
                    <template v-if="providerCallMetrics(artifactDetails[node.outputArtifact.key])">
                      · {{ providerCallMetrics(artifactDetails[node.outputArtifact.key]) }}
                    </template>
                  </p>
                  <p
                    v-if="artifactDetails[node.outputArtifact.key]?.providerCall?.errorMessage"
                    class="flow-run-trace-provider-error"
                  >
                    {{ artifactDetails[node.outputArtifact.key]?.providerCall?.errorMessage }}
                  </p>
                </div>
              </div>
              <p
                v-else-if="node.outputArtifact.state === 'failed'"
                class="flow-run-trace-provider-legacy"
              >
                此运行未保存节点级 Provider 来源，可查看节点错误与 Task 执行来源。
              </p>
              <section
                v-if="artifactDetails[node.outputArtifact.key]?.providerInputReferences?.length"
                class="flow-provider-input-references"
              >
                <div class="flow-provider-input-references-heading">
                  <span>Declared Inputs</span>
                  <small>
                    {{ artifactDetails[node.outputArtifact.key]?.providerInputReferences?.length }} 个已保存引用
                  </small>
                </div>
                <ol>
                  <li
                    v-for="reference in artifactDetails[node.outputArtifact.key]?.providerInputReferences"
                    :key="reference.artifactKey"
                  >
                    <span>{{ reference.inputOrder }}</span>
                    <div>
                      <strong>{{ flowArtifactTypeLabel(reference.artifactType) }}</strong>
                      <small>
                        {{ providerInputSourceLabel(reference.sourceNodeId) }}
                        · {{ flowArtifactInputResolutionLabel(reference.inputResolution) }}
                        <template v-if="reference.contentFingerprint">
                          · SHA {{ shortFingerprint(reference.contentFingerprint) }}
                        </template>
                      </small>
                    </div>
                    <button
                      v-if="reference.artifactStorage === 'node-artifact' && reference.artifactState === 'materialized'"
                      type="button"
                      title="定位到已声明输入产物"
                      :disabled="loadingArtifactKey === reference.artifactKey"
                      @click="revealProviderInputReference(reference.artifactKey)"
                    >
                      <View />
                    </button>
                  </li>
                </ol>
              </section>
              <div
                v-if="artifactDetails[node.outputArtifact.key]?.inputArtifactKey"
                class="flow-run-trace-artifact-lineage"
              >
                <span></span>
                <div>
                  <small>上游引用</small>
                  <strong>
                    {{ artifactInputTypeLabel(artifactDetails[node.outputArtifact.key]) }}
                  </strong>
                  <p>
                    {{ artifactInputStorageLabel(artifactDetails[node.outputArtifact.key]) }}
                    <template v-if="artifactDetails[node.outputArtifact.key]?.inputResolution">
                      · {{ artifactInputResolutionLabel(artifactDetails[node.outputArtifact.key]) }}
                    </template>
                    <template v-if="artifactDetails[node.outputArtifact.key]?.inputArtifactState">
                      · {{ artifactInputStateLabel(artifactDetails[node.outputArtifact.key]) }}
                    </template>
                    <template v-if="artifactDetails[node.outputArtifact.key]?.inputContentFingerprint">
                      · SHA {{ artifactInputFingerprintLabel(artifactDetails[node.outputArtifact.key]) }}
                    </template>
                  </p>
                </div>
                <button
                  v-if="canInspectUpstreamArtifact(artifactDetails[node.outputArtifact.key])"
                  type="button"
                  :disabled="loadingArtifactKey === artifactDetails[node.outputArtifact.key]?.inputArtifactKey"
                  @click="revealUpstreamArtifact(artifactDetails[node.outputArtifact.key])"
                >
                  <Back />
                  查看上游
                </button>
              </div>
              <details
                v-if="artifactDetails[node.outputArtifact.key]?.inputArtifactKey"
                class="flow-run-trace-lineage"
                @toggle="toggleLineage(node.outputArtifact.key, $event)"
              >
                <summary>
                  <span>
                    <strong>来源链</strong>
                    <small>按需查看完整上游路径 · 不包含产物正文</small>
                  </span>
                  <Right class="flow-run-trace-lineage-chevron" />
                </summary>
                <div v-if="lineageLoadingKey === node.outputArtifact.key" class="flow-run-trace-lineage-loading">
                  <Loading class="flow-run-trace-artifact-loading" />
                  正在读取来源链
                </div>
                <div v-else-if="lineageDetails[node.outputArtifact.key]" class="flow-run-trace-lineage-body">
                  <div class="flow-run-trace-lineage-status">
                    <span :class="{ complete: lineageDetails[node.outputArtifact.key]?.complete }"></span>
                    {{ lineageStatusLabel(lineageDetails[node.outputArtifact.key]) }}
                  </div>
                  <ol>
                    <li
                      v-for="entry in lineageDetails[node.outputArtifact.key]?.path"
                      :key="`${entry.artifactKey}-${entry.sequence || 'source'}`"
                      :class="{ source: !entry.persisted }"
                    >
                      <span class="flow-run-trace-lineage-step">{{ entry.persisted ? entry.sequence : '·' }}</span>
                      <div>
                        <strong>{{ flowArtifactTypeLabel(entry.artifactType) }}</strong>
                        <small>
                          {{ entry.persisted ? nodeTypeLabelForLineage(entry.nodeId) : 'Flow 快照目标' }}
                          · {{ flowArtifactStorageLabel(entry.storage) }}
                          <template v-if="entry.contentFingerprint">
                            · SHA {{ shortFingerprint(entry.contentFingerprint) }}
                          </template>
                          <template v-if="entry.providerCall">
                            · {{ flowProviderCallSource(entry.providerCall) || 'Provider 来源未报告' }}
                            <template v-if="flowProviderCallMetrics(entry.providerCall)">
                              · {{ flowProviderCallMetrics(entry.providerCall) }}
                            </template>
                          </template>
                        </small>
                      </div>
                      <button
                        v-if="entry.persisted"
                        type="button"
                        title="定位到运行轨迹中的产物"
                        @click="revealLineageEntry(entry)"
                      >
                        <View />
                      </button>
                    </li>
                  </ol>
                </div>
              </details>
              <pre v-if="artifactDetails[node.outputArtifact.key]?.payload">{{ artifactDetails[node.outputArtifact.key]?.payload }}</pre>
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
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Back, CopyDocument, Loading, Right, View } from '@element-plus/icons-vue'
import type {
  FlowArtifactInputResolution,
  FlowArtifactState,
  FlowArtifactStorage,
  FlowArtifactType,
  FlowExecutionMode,
  FlowNodeArtifactLineage,
  FlowNodeArtifactLineageEntry,
  FlowNodeArtifactDetail,
  FlowNodeRunTrace,
  FlowNodeRunTraceStatus,
  FlowNodeType,
  FlowRunTrace
} from '@/types'
import { getTaskArtifact, getTaskArtifactLineage } from '@/api/tasks'
import FlowExecutionPlan from '@/components/flow/FlowExecutionPlan.vue'
import FlowProviderAttempts from '@/components/flow/FlowProviderAttempts.vue'
import {
  flowArtifactInputResolutionLabel,
  flowArtifactStateLabel,
  flowArtifactStorageLabel,
  flowArtifactTypeLabel
} from '@/utils/flowExecutionPlan'
import { flowArtifactLineageStatusLabel } from '@/utils/flowArtifactLineage'
import {
  flowProviderCallMetrics,
  flowProviderCallSource,
  flowProviderCallStatusLabel
} from '@/utils/flowProviderCall'

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
const lineageDetails = ref<Record<string, FlowNodeArtifactLineage>>({})
const openArtifactKeys = ref<Record<string, boolean>>({})
const loadingArtifactKey = ref('')
const lineageLoadingKey = ref('')

function canInspectArtifact(node: FlowNodeRunTrace) {
  return Boolean(
    props.trace.runId
      && node.outputArtifact?.storage === 'node-artifact'
      && (
        node.outputArtifact.state === 'materialized'
        || (node.outputArtifact.type === 'provider-result' && node.outputArtifact.state === 'failed')
      )
  )
}

function artifactActionLabel(node: FlowNodeRunTrace) {
  return node.outputArtifact?.state === 'failed' ? '查看调用' : '查看产物'
}

function artifactCloseLabel(node: FlowNodeRunTrace) {
  return node.outputArtifact?.state === 'failed' ? '收起调用' : '收起产物'
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

  const detail = await loadArtifact(taskId, artifact.key)
  if (detail) {
    openArtifactKeys.value[artifact.key] = true
  }
}

async function loadArtifact(taskId: string, artifactKey: string) {
  if (artifactDetails.value[artifactKey]) {
    return artifactDetails.value[artifactKey]
  }
  loadingArtifactKey.value = artifactKey
  try {
    const { data } = await getTaskArtifact(taskId, artifactKey)
    artifactDetails.value[artifactKey] = data
    return data
  } catch {
    ElMessage.error('节点产物加载失败')
    return null
  } finally {
    loadingArtifactKey.value = ''
  }
}

function canInspectUpstreamArtifact(detail: FlowNodeArtifactDetail | undefined) {
  return Boolean(
    detail?.inputArtifactKey
      && detail.inputArtifactStorage === 'node-artifact'
      && detail.inputArtifactState === 'materialized'
  )
}

async function revealUpstreamArtifact(detail: FlowNodeArtifactDetail | undefined) {
  const taskId = props.trace.runId
  const artifactKey = detail?.inputArtifactKey
  if (!taskId || !artifactKey || !canInspectUpstreamArtifact(detail)) {
    return
  }
  const upstream = await loadArtifact(taskId, artifactKey)
  if (!upstream) {
    return
  }
  openArtifactKeys.value[artifactKey] = true
  await nextTick()
  const target = Array.from(document.querySelectorAll<HTMLElement>('[data-artifact-key]'))
    .find((element) => element.dataset.artifactKey === artifactKey)
  target?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

async function revealProviderInputReference(artifactKey: string) {
  const taskId = props.trace.runId
  if (!taskId) {
    return
  }
  const inputArtifact = await loadArtifact(taskId, artifactKey)
  if (!inputArtifact) {
    return
  }
  openArtifactKeys.value[artifactKey] = true
  await nextTick()
  const target = Array.from(document.querySelectorAll<HTMLElement>('[data-artifact-key]'))
    .find((element) => element.dataset.artifactKey === artifactKey)
  target?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

async function toggleLineage(artifactKey: string, event: Event) {
  const details = event.currentTarget as HTMLDetailsElement | null
  if (!details?.open || lineageDetails.value[artifactKey]) {
    return
  }
  const taskId = props.trace.runId
  if (!taskId) {
    return
  }
  lineageLoadingKey.value = artifactKey
  try {
    const { data } = await getTaskArtifactLineage(taskId, artifactKey)
    lineageDetails.value[artifactKey] = data
  } catch {
    ElMessage.error('来源链加载失败')
    details.open = false
  } finally {
    lineageLoadingKey.value = ''
  }
}

async function revealLineageEntry(entry: FlowNodeArtifactLineageEntry) {
  const taskId = props.trace.runId
  if (!taskId || !entry.persisted) {
    return
  }
  const detail = await loadArtifact(taskId, entry.artifactKey)
  if (!detail) {
    return
  }
  openArtifactKeys.value[entry.artifactKey] = true
  await nextTick()
  const target = Array.from(document.querySelectorAll<HTMLElement>('[data-artifact-key]'))
    .find((element) => element.dataset.artifactKey === entry.artifactKey)
  target?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

async function copyArtifact(artifactKey: string) {
  try {
    await navigator.clipboard.writeText(artifactDetails.value[artifactKey]?.payload || '')
    ElMessage.success('节点产物已复制')
  } catch {
    ElMessage.error('复制失败，请展开后手动复制')
  }
}

function artifactInputTypeLabel(detail: FlowNodeArtifactDetail | undefined) {
  return detail?.inputArtifactType
    ? flowArtifactTypeLabel(detail.inputArtifactType as FlowArtifactType)
    : '未知产物'
}

function artifactInputStorageLabel(detail: FlowNodeArtifactDetail | undefined) {
  return detail?.inputArtifactStorage
    ? flowArtifactStorageLabel(detail.inputArtifactStorage as FlowArtifactStorage)
    : '未知来源'
}

function artifactInputResolutionLabel(detail: FlowNodeArtifactDetail | undefined) {
  return detail?.inputResolution
    ? flowArtifactInputResolutionLabel(detail.inputResolution as FlowArtifactInputResolution)
    : '未知解析方式'
}

function artifactInputStateLabel(detail: FlowNodeArtifactDetail | undefined) {
  return detail?.inputArtifactState
    ? flowArtifactStateLabel(detail.inputArtifactState as FlowArtifactState)
    : '未知状态'
}

function artifactInputFingerprintLabel(detail: FlowNodeArtifactDetail | undefined) {
  return detail?.inputContentFingerprint ? shortFingerprint(detail.inputContentFingerprint) : ''
}

function providerCallSource(detail: FlowNodeArtifactDetail | undefined) {
  return flowProviderCallSource(detail?.providerCall)
}

function providerCallMetrics(detail: FlowNodeArtifactDetail | undefined) {
  return flowProviderCallMetrics(detail?.providerCall)
}

function providerCallStatus(detail: FlowNodeArtifactDetail | undefined) {
  return flowProviderCallStatusLabel(detail?.providerCall)
}

function lineageStatusLabel(lineage: FlowNodeArtifactLineage | undefined) {
  return flowArtifactLineageStatusLabel(lineage)
}

function nodeTypeLabelForLineage(nodeId: string | null | undefined) {
  if (!nodeId) {
    return '节点产物'
  }
  const node = props.trace.nodes.find((item) => item.nodeId === nodeId)
  return node ? nodeTypeLabel(node.nodeType) : '节点产物'
}

function providerInputSourceLabel(nodeId: string | null | undefined) {
  if (!nodeId) {
    return 'Flow 快照目标'
  }
  const node = props.trace.nodes.find((item) => item.nodeId === nodeId)
  return node ? `${nodeTypeLabel(node.nodeType)} · ${node.title}` : '节点产物'
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
  if (source === 'stored-input-recovery') return '失败输入恢复'
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
