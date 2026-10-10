<template>
  <section class="run-input-comparison artifact-evidence-comparison" :class="comparison.relation">
    <span></span>
    <div class="artifact-evidence-body">
      <div class="artifact-evidence-heading">
        <div>
          <strong>{{ title }}</strong>
          <p>{{ description }}</p>
        </div>
        <div class="artifact-evidence-counts">
          <span>来源 {{ countLabel(comparison.sourceArtifactCount) }}</span>
          <span>目标 {{ countLabel(comparison.targetArtifactCount) }}</span>
        </div>
      </div>

      <ul v-if="comparison.changes.length" class="artifact-evidence-change-list">
        <li v-for="change in visibleChanges" :key="change.key">
          <div class="artifact-evidence-change-copy">
            <span>{{ changeLabel(change.kind) }}</span>
            <strong>{{ change.title }}</strong>
            <small>{{ detailLabel(change) }}</small>
          </div>
          <button
            v-if="canInspectNode(change)"
            type="button"
            class="artifact-evidence-change-action"
            @click="openNode(change)"
          >
            检查节点
          </button>
        </li>
      </ul>
      <small v-if="remainingCount" class="artifact-evidence-remaining">另有 {{ remainingCount }} 处产物变化</small>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  compareRunNodeArtifacts,
  type RunArtifactEvidenceChange,
  type RunArtifactEvidenceChangeKind
} from '@/utils/runComparison'
import { flowArtifactStorageLabel, flowArtifactTypeLabel } from '@/utils/flowExecutionPlan'
import type { TaskHistoryItem } from '@/types'

const props = defineProps<{
  sourceRun: TaskHistoryItem
  targetRun: TaskHistoryItem
  canOpenNode?: (run: TaskHistoryItem, nodeId: string) => boolean
}>()

const emit = defineEmits<{
  openNode: [payload: { run: TaskHistoryItem; nodeId: string; artifactKey?: string }]
}>()

const comparison = computed(() => compareRunNodeArtifacts(props.sourceRun, props.targetRun))
const visibleChanges = computed(() => comparison.value.changes.slice(0, 6))
const remainingCount = computed(() => Math.max(0, comparison.value.changes.length - visibleChanges.value.length))

const title = computed(() => {
  if (comparison.value.relation === 'same') return '节点产物证据一致'
  if (comparison.value.relation === 'different') return '节点产物证据发生变化'
  return '节点产物证据无法核验'
})

const description = computed(() => {
  const value = comparison.value
  if (value.relation === 'unavailable') {
    return '至少一次运行没有保存可比较的 Artifact 元数据，不根据当前 Flow 或数据库资产反推历史证据。'
  }
  if (value.relation === 'same') {
    return '两次运行保存了相同的节点 Artifact、状态与内容指纹。'
  }
  return `已保存的节点 Artifact 中发现 ${value.changes.length} 处变化；完整 payload 仍按需查看。`
})

function countLabel(count: number | null) {
  return count === null ? '未核验' : `${count} 项`
}

function changeLabel(kind: RunArtifactEvidenceChangeKind) {
  const labels: Record<RunArtifactEvidenceChangeKind, string> = {
    added: '新增产物',
    removed: '缺失产物',
    'contract-changed': '契约变化',
    'state-changed': '状态变化',
    'fingerprint-changed': '内容变化'
  }
  return labels[kind]
}

function detailLabel(change: RunArtifactEvidenceChange) {
  if (change.kind === 'added') {
    return `${artifactStateLabel(change.targetState)} · ${fingerprintLabel(change.targetFingerprint)}`
  }
  if (change.kind === 'removed') {
    return `${artifactStateLabel(change.sourceState)} · 目标运行未保存`
  }
  if (change.kind === 'contract-changed') {
    return `${artifactContractLabel(change.sourceType, change.sourceStorage)} → ${artifactContractLabel(change.targetType, change.targetStorage)}`
  }
  if (change.kind === 'state-changed') {
    return `${artifactStateLabel(change.sourceState)} → ${artifactStateLabel(change.targetState)}`
  }
  return `${fingerprintLabel(change.sourceFingerprint)} → ${fingerprintLabel(change.targetFingerprint)}`
}

function artifactContractLabel(
  type: RunArtifactEvidenceChange['sourceType'],
  storage: RunArtifactEvidenceChange['sourceStorage']
) {
  return `${type ? flowArtifactTypeLabel(type) : '未记录'} · ${storage ? flowArtifactStorageLabel(storage) : '未记录'}`
}

function artifactStateLabel(state: RunArtifactEvidenceChange['sourceState']) {
  if (state === 'materialized') return '已保存'
  if (state === 'failed') return '失败'
  if (state === 'skipped') return '已跳过'
  return '未保存'
}

function fingerprintLabel(fingerprint: string | null) {
  return fingerprint ? `SHA ${fingerprint.slice(0, 10)}` : '指纹未保存'
}

function canInspectNode(change: RunArtifactEvidenceChange) {
  return Boolean(
    props.canOpenNode?.(
      change.side === 'source' ? props.sourceRun : props.targetRun,
      change.nodeId
    ) ?? true
  )
}

function openNode(change: RunArtifactEvidenceChange) {
  emit('openNode', {
    run: change.side === 'source' ? props.sourceRun : props.targetRun,
    nodeId: change.nodeId,
    artifactKey: change.artifactKey
  })
}
</script>
