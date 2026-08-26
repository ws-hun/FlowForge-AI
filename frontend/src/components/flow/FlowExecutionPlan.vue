<template>
  <section class="flow-execution-plan" :class="{ 'is-stale': stale }">
    <header class="flow-execution-plan-heading">
      <div>
        <span>{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <small>{{ plan.version }} · {{ schedulingLabel }} · {{ providerStepCount }} 次 AI 调用</small>
    </header>

    <div v-if="failurePolicySummary" class="flow-execution-plan-policy">
      <span>Failure Policy</span>
      <p>{{ failurePolicySummary }}</p>
      <small>{{ plan.failurePolicy?.version }}</small>
    </div>

    <div v-if="plan.inputResolutionContract" class="flow-execution-plan-resolution">
      <span>Input Contract</span>
      <strong>{{ flowArtifactInputResolutionLabel(plan.inputResolutionContract.activeResolution) }}</strong>
      <p v-if="!plan.inputResolutionContract.persistedArtifactEnabled">
        当前仍由单次编译解析；持久化产物输入将在逐节点运行时启用。
      </p>
      <small>{{ plan.inputResolutionContract.version }}</small>
    </div>

    <div class="flow-execution-plan-path">
      <article
        v-for="step in plan.steps"
        :key="step.nodeId"
        class="flow-execution-plan-step"
        :class="{ 'is-provider': step.providerBoundary }"
      >
        <div class="flow-execution-plan-index">{{ step.sequence }}</div>
        <div class="flow-execution-plan-step-body">
          <span>{{ flowNodeTypeLabel(step.nodeType) }}</span>
          <strong>{{ step.title }}</strong>
          <p>{{ flowExecutionOperationLabel(step.operation) }}</p>
          <div v-if="step.inputArtifact && step.outputArtifact" class="flow-execution-artifact-path">
            <div>
              <span :title="step.inputArtifact.key">{{ flowArtifactTypeLabel(step.inputArtifact.type) }}</span>
              <Right />
              <span :title="step.outputArtifact.key">{{ flowArtifactTypeLabel(step.outputArtifact.type) }}</span>
            </div>
            <small>输出记录于 {{ flowArtifactStorageLabel(step.outputArtifact.storage) }}</small>
          </div>
          <small>
            {{ step.dependsOnNodeIds.length ? '承接前置编译内容' : '执行起点' }}
            <template v-if="step.inputResolution">
              · {{ flowArtifactInputResolutionLabel(step.inputResolution) }}
            </template>
            <template v-if="step.providerBoundary"> · 唯一 Provider 边界</template>
          </small>
          <button
            v-if="nodeActionLabel"
            type="button"
            :disabled="stale || !navigableNodeIds.includes(step.nodeId)"
            @click="emit('openNode', step.nodeId)"
          >
            {{ nodeActionLabel }}
            <Right />
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Right } from '@element-plus/icons-vue'
import type { FlowExecutionPlan } from '@/types'
import {
  flowArtifactInputResolutionLabel,
  flowArtifactStorageLabel,
  flowArtifactTypeLabel,
  flowExecutionFailurePolicySummary,
  flowExecutionOperationLabel,
  flowNodeTypeLabel
} from '@/utils/flowExecutionPlan'

const props = withDefaults(
  defineProps<{
    plan: FlowExecutionPlan
    eyebrow?: string
    title?: string
    stale?: boolean
    nodeActionLabel?: string
    navigableNodeIds?: string[]
  }>(),
  {
    eyebrow: 'Execution Path',
    title: '节点如何形成这次运行',
    stale: false,
    nodeActionLabel: '',
    navigableNodeIds: () => []
  }
)

const emit = defineEmits<{
  openNode: [nodeId: string]
}>()

const providerStepCount = computed(() => props.plan.steps.filter((step) => step.providerBoundary).length)
const schedulingLabel = computed(() => props.plan.scheduling === 'linear' ? '顺序计划' : props.plan.scheduling)
const failurePolicySummary = computed(() => props.plan.failurePolicy
  ? flowExecutionFailurePolicySummary(props.plan.failurePolicy)
  : '')
</script>
