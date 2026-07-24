<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">历史记录</p>
      <h1>时间线，而不是表格。</h1>
      <p>保留每一次 AI 工作流执行的上下文、摘要和结果。</p>
    </header>

    <div class="timeline">
      <article
        v-for="task in workspace.tasks"
        :id="`history-run-${task.id}`"
        :key="task.id"
        class="timeline-item"
        :class="{ focused: focusedRunId === task.id }"
      >
        <span class="timeline-dot" :class="{ failed: isFailed(task) }"></span>
        <div class="timeline-content soft-card" :class="{ failed: isFailed(task) }">
          <div class="row-between">
            <strong>{{ task.input }}</strong>
            <div class="history-run-meta">
              <span v-if="isFailed(task)" class="history-status failed">执行失败</span>
              <small>{{ new Date(task.createdAt).toLocaleString() }}</small>
            </div>
          </div>
          <button
            v-if="task.sourceFlowTitle || task.sourcePromptTitle"
            type="button"
            class="history-source-row"
            :disabled="!task.sourceFlowId && !task.sourcePromptId"
            @click="openTaskSource(task)"
          >
            <span class="badge">{{ task.sourceFlowTitle ? 'Flow' : 'Prompt' }}</span>
            <strong>{{ task.sourceFlowTitle || task.sourcePromptTitle }}</strong>
          </button>
          <div v-if="lineageSource(task)" class="history-lineage-note">
            <span>{{ lineageLabel(task) }}</span>
            <strong>
              {{
                formatExecutionSource(
                  lineageSource(task)?.provider,
                  lineageSource(task)?.model,
                  lineageSource(task)?.totalTokens,
                  lineageSource(task)?.durationMs
                ) ||
                '来源运行'
              }}
            </strong>
            <button type="button" class="text-button" @click="openHistoryRun(lineageSource(task)?.id)">
              查看来源
            </button>
          </div>
          <p class="muted" :class="{ 'error-copy': isFailed(task) }">
            {{ isFailed(task) ? task.errorMessage || task.result : task.summary }}
          </p>
          <div class="history-reuse-row">
            <span class="run-provenance">
              {{
                formatExecutionSource(task.provider, task.model, task.totalTokens, task.durationMs) ||
                '已保存服务端执行输入'
              }}
            </span>
            <div class="history-reuse-actions">
              <button
                v-if="!isFailed(task)"
                type="button"
                class="secondary-button"
                @click="continueFromRun(task)"
              >
                用结果继续
              </button>
              <button
                v-if="canCompareWithSource(task)"
                type="button"
                class="ghost-button"
                @click="compareWithSource(task)"
              >
                对比来源
              </button>
              <button
                type="button"
                class="ghost-button"
                :disabled="workspace.running"
                @click="rerunHistoryTask(task.id)"
              >
                {{ workspace.running ? '执行中...' : '使用当前 Provider 重跑' }}
              </button>
            </div>
          </div>
          <el-collapse v-model="expandedRunIds" @change="onRunExpansionChange(task.id, $event)">
            <el-collapse-item :title="isFailed(task) ? '查看失败详情' : '查看结果'" :name="task.id">
              <div v-if="isFailed(task)" class="failed-run-detail">
                <span class="section-kicker">Execution Error</span>
                <strong>{{ task.errorMessage || task.result }}</strong>
                <p>执行输入、来源和 Flow 快照已保留，可以使用当前 Provider 重新运行。</p>
              </div>
              <AiResultDocument
                v-else
                :summary="task.summary"
                :result="task.result"
                :provider="task.provider"
                :model="task.model"
                :input-tokens="task.inputTokens"
                :output-tokens="task.outputTokens"
                :total-tokens="task.totalTokens"
                :duration-ms="task.durationMs"
                compact
                :show-raw="false"
              />
              <FlowRunSnapshot
                v-if="task.flowRunSnapshot"
                :snapshot="task.flowRunSnapshot"
                can-create-flow
                :can-reuse-run-settings="flowStillAvailable(task.flowRunSnapshot)"
                :can-open-source-flow="flowSourceStillAvailable(task.flowRunSnapshot)"
                :creating="workspace.flowLoading"
                @create-flow="createFlowFromSnapshot"
                @reuse-run-settings="reuseFlowRunSettings"
                @open-source-flow="openFlowSnapshotSource"
              />
            </el-collapse-item>
          </el-collapse>
        </div>
      </article>
      <div v-if="!workspace.tasks.length" class="empty-state">暂无历史记录</div>
    </div>

    <RunComparisonDialog
      :open="comparisonOpen"
      :source-run="comparisonSource"
      :target-run="comparisonTarget"
      :mode="comparisonMode"
      @close="closeComparison"
      @continue="continueFromRun"
    />
  </section>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import RunComparisonDialog from '@/components/ai/RunComparisonDialog.vue'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatExecutionSource } from '@/utils/aiProvider'
import type { FlowRunSnapshot as FlowRunSnapshotType, TaskHistoryItem } from '@/types'

const router = useRouter()
const route = useRoute()
const workspace = useWorkspaceStore()
const expandedRunIds = ref<string[]>([])
const focusedRunId = ref('')
const historyRouteReady = ref(false)
const comparisonOpen = ref(false)
const comparisonSource = ref<TaskHistoryItem | null>(null)
const comparisonTarget = ref<TaskHistoryItem | null>(null)
const comparisonMode = ref<'rerun' | 'continuation'>('rerun')

onMounted(async () => {
  await workspace.bootstrap()
  historyRouteReady.value = true
  await openRunFromRoute()
})

watch(
  () => route.query.run,
  () => {
    if (historyRouteReady.value) {
      void openRunFromRoute()
    }
  }
)

async function rerunHistoryTask(taskId: string) {
  if (!workspace.activeProvider) {
    ElMessage.warning('请先配置并激活 AI Provider')
    router.push('/api-keys')
    return
  }

  const result = await workspace.rerunHistoricalTask(taskId)
  const sourceRun = workspace.tasks.find((task) => task.id === taskId) || null
  const targetRun = result?.taskId ? workspace.tasks.find((task) => task.id === result.taskId) || null : null
  if (sourceRun && targetRun) {
    openComparison(sourceRun, targetRun, 'rerun')
  } else if (result) {
    router.push('/tasks')
  }
}

function lineageSource(task: TaskHistoryItem) {
  const sourceTaskId = task.rerunOfTaskId || task.continuedFromTaskId
  if (!sourceTaskId) {
    return null
  }
  return workspace.tasks.find((item) => item.id === sourceTaskId) || null
}

function lineageLabel(task: TaskHistoryItem) {
  return task.rerunOfTaskId ? '重跑自' : '继续自'
}

function isFailed(task: TaskHistoryItem) {
  return task.status === 'failed'
}

function canCompareWithSource(task: TaskHistoryItem) {
  const sourceRun = lineageSource(task)
  return Boolean(sourceRun && !isFailed(sourceRun) && !isFailed(task))
}

function lineageMode(task: TaskHistoryItem): 'rerun' | 'continuation' {
  return task.rerunOfTaskId ? 'rerun' : 'continuation'
}

function openComparison(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem,
  mode: 'rerun' | 'continuation'
) {
  comparisonSource.value = sourceRun
  comparisonTarget.value = targetRun
  comparisonMode.value = mode
  comparisonOpen.value = true
}

function compareWithSource(targetRun: TaskHistoryItem) {
  const sourceRun = lineageSource(targetRun)
  if (sourceRun) {
    openComparison(sourceRun, targetRun, lineageMode(targetRun))
  }
}

async function openRunFromRoute() {
  const runId = typeof route.query.run === 'string' ? route.query.run : ''
  if (!runId) {
    expandedRunIds.value = []
    focusedRunId.value = ''
    return
  }

  if (!workspace.tasks.some((task) => task.id === runId)) {
    ElMessage.warning('指定的运行记录已不存在或无法访问')
    await syncRunRoute(null, 'replace')
    return
  }

  expandedRunIds.value = [runId]
  focusedRunId.value = runId
  await nextTick()
  document.getElementById(`history-run-${runId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function openHistoryRun(runId?: string) {
  if (!runId) {
    return
  }
  void syncRunRoute(runId)
}

function onRunExpansionChange(taskId: string, value: string | string[]) {
  const expanded = Array.isArray(value) ? value.includes(taskId) : value === taskId
  expandedRunIds.value = expanded ? [taskId] : []
  focusedRunId.value = expanded ? taskId : ''
  void syncRunRoute(expanded ? taskId : null)
}

function syncRunRoute(runId: string | null, mode: 'push' | 'replace' = 'push') {
  const currentRunId = typeof route.query.run === 'string' ? route.query.run : ''
  if ((runId || '') === currentRunId) {
    return Promise.resolve()
  }

  const query = { ...route.query }
  if (runId) {
    query.run = runId
  } else {
    delete query.run
  }
  return router[mode]({ query })
}

function openTaskSource(task: TaskHistoryItem) {
  if (task.sourceFlowId) {
    router.push({ path: '/workflows', query: { flow: task.sourceFlowId } })
    return
  }
  if (task.sourcePromptId) {
    router.push({ path: '/prompts', query: { prompt: task.sourcePromptId } })
  }
}

function closeComparison() {
  comparisonOpen.value = false
  comparisonSource.value = null
  comparisonTarget.value = null
  comparisonMode.value = 'rerun'
}

function continueFromRun(run: TaskHistoryItem) {
  closeComparison()
  workspace.prepareTaskContinuation(run)
  router.push('/tasks')
}

function flowStillAvailable(snapshot: FlowRunSnapshotType) {
  return workspace.flowDrafts.some((flow) => flow.id === snapshot.flowId)
}

function flowSourceStillAvailable(snapshot: FlowRunSnapshotType) {
  return Boolean(
    snapshot.sourceFlowId && workspace.flowDrafts.some((flow) => flow.id === snapshot.sourceFlowId)
  )
}

function openFlowSnapshotSource(snapshot: FlowRunSnapshotType) {
  const sourceFlow = workspace.flowDrafts.find((flow) => flow.id === snapshot.sourceFlowId)
  if (!sourceFlow) {
    ElMessage.warning('来源 Flow 已删除，运行快照中的来源信息仍然保留')
    return
  }

  workspace.selectFlowDraft(sourceFlow.id)
  ElMessage.success(`已打开来源 Flow「${sourceFlow.title}」`)
  router.push({ path: '/workflows', query: { flow: sourceFlow.id } })
}

function reuseFlowRunSettings(snapshot: FlowRunSnapshotType) {
  if (!workspace.prepareFlowRunFromSnapshot(snapshot)) {
    ElMessage.warning('原 Flow 已不存在，请从快照创建新的 Flow')
    return
  }

  ElMessage.success('已将本次运行配置带回原 Flow')
  router.push({ path: '/workflows', query: { flow: snapshot.flowId } })
}

async function createFlowFromSnapshot(snapshot: FlowRunSnapshotType) {
  const flow = await workspace.createFlowFromRunSnapshot(snapshot)
  if (!flow) {
    return
  }

  ElMessage.success('已创建新的 Flow，并带入本次运行上下文')
  router.push({ path: '/workflows', query: { flow: flow.id } })
}
</script>
