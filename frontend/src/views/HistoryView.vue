<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">历史记录</p>
      <h1>时间线，而不是表格。</h1>
      <p>保留每一次 AI 工作流执行的上下文、摘要和结果。</p>
    </header>

    <div class="history-explorer">
      <label class="history-search-field">
        <Search class="history-search-icon" />
        <input v-model="historyQuery" type="search" placeholder="搜索运行、来源或结果..." />
      </label>
      <div class="history-scope-tabs" role="tablist" aria-label="历史运行范围">
        <button
          v-for="scope in historyScopes"
          :key="scope.value"
          type="button"
          role="tab"
          :aria-selected="historyScope === scope.value"
          :class="{ active: historyScope === scope.value }"
          @click="historyScope = scope.value"
        >
          {{ scope.label }}
        </button>
      </div>
      <span class="history-result-count">{{ filteredTasks.length }} / {{ workspace.tasks.length }}</span>
    </div>

    <div class="timeline">
      <article
        v-for="task in filteredTasks"
        :id="`history-run-${task.id}`"
        :key="task.id"
        class="timeline-item"
        :class="{ focused: focusedRunId === task.id }"
      >
        <span class="timeline-dot" :class="{ failed: isFailed(task) }"></span>
        <div class="timeline-content soft-card" :class="{ failed: isFailed(task) }">
          <div class="row-between">
            <div class="history-run-title">
              <span>{{ historyRunKind(task) }}</span>
              <strong>{{ historyRunTitle(task) }}</strong>
            </div>
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
                v-if="taskFlowStillAvailable(task)"
                type="button"
                class="ghost-button"
                @click="inspectTaskInFlow(task)"
              >
                在 Flow 中检查
              </button>
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
              <ExecutionInputArchive
                :input="task.input"
                :title="task.flowRunSnapshot ? '固定 Flow 执行输入' : '固定执行输入'"
                can-create-variant
                @create-variant="createInputVariant(task)"
              />
              <FlowRunTrace
                v-if="task.flowRunTrace"
                :trace="task.flowRunTrace"
                node-action-label="在 Flow 中打开"
                :navigable-node-ids="traceNavigableNodeIds(task)"
                @open-node="openFlowTraceNode(task, $event)"
              />
              <section v-if="!isFailed(task)" class="history-result-reuse">
                <div>
                  <span class="section-kicker">Reuse Result</span>
                  <strong>把这次有效结果沉淀为下一次创作的起点。</strong>
                </div>
                <div class="history-result-reuse-actions">
                  <button
                    type="button"
                    class="ghost-button"
                    :disabled="workspace.taskAssetLoading"
                    @click="saveRunAsPrompt(task)"
                  >
                    {{ workspace.taskPromptsByRunId[task.id] ? '打开 Prompt' : workspace.taskAssetLoading ? '保存中...' : '保存为 Prompt' }}
                  </button>
                  <button
                    type="button"
                    class="secondary-button"
                    :disabled="workspace.taskAssetLoading || workspace.flowLoading"
                    @click="createFlowFromRun(task)"
                  >
                    {{ workspace.flowLoading ? '创建中...' : '从 Result 创建 Flow' }}
                  </button>
                </div>
              </section>
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
      <div v-else-if="!filteredTasks.length" class="empty-state history-filter-empty">
        <div>
          <strong>没有匹配的运行</strong>
          <p>当前历史仍然保留，清除搜索或切换范围即可重新查看。</p>
          <button type="button" class="ghost-button" @click="resetHistoryExplorer">清除筛选</button>
        </div>
      </div>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import ExecutionInputArchive from '@/components/ai/ExecutionInputArchive.vue'
import RunComparisonDialog from '@/components/ai/RunComparisonDialog.vue'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import FlowRunTrace from '@/components/flow/FlowRunTrace.vue'
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
const comparisonMode = ref<'rerun' | 'continuation' | 'input-variant'>('rerun')
type HistoryScope = 'all' | 'flow' | 'prompt' | 'failed'
const historyQuery = ref('')
const historyScope = ref<HistoryScope>('all')
const historyScopes: Array<{ label: string; value: HistoryScope }> = [
  { label: '全部', value: 'all' },
  { label: 'Flow', value: 'flow' },
  { label: 'Prompt', value: 'prompt' },
  { label: '失败', value: 'failed' }
]
const filteredTasks = computed(() => {
  const query = historyQuery.value.trim().toLowerCase()
  return workspace.tasks.filter((task) => {
    if (historyScope.value === 'flow' && !task.sourceFlowId) return false
    if (historyScope.value === 'prompt' && !task.sourcePromptId) return false
    if (historyScope.value === 'failed' && !isFailed(task)) return false
    if (!query) return true

    return [
      task.input,
      task.summary,
      task.result,
      task.errorMessage,
      task.sourceFlowTitle,
      task.sourcePromptTitle,
      task.provider,
      task.model,
      historyRunKind(task)
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(query)
  })
})

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
  const sourceTaskId = task.rerunOfTaskId || task.continuedFromTaskId || task.inputVariantOfTaskId
  if (!sourceTaskId) {
    return null
  }
  return workspace.tasks.find((item) => item.id === sourceTaskId) || null
}

function lineageLabel(task: TaskHistoryItem) {
  if (task.rerunOfTaskId) return '重跑自'
  if (task.continuedFromTaskId) return '继续自'
  return '输入变体自'
}

function isFailed(task: TaskHistoryItem) {
  return task.status === 'failed'
}

function historyRunKind(task: TaskHistoryItem) {
  if (task.continuedFromTaskId) return 'Continuation'
  if (task.rerunOfTaskId) return 'Rerun'
  if (task.inputVariantOfTaskId) return 'Input Variant'
  if (task.sourceFlowId) return 'Flow Run'
  if (task.sourcePromptId) return 'Prompt Run'
  return 'AI Task'
}

function historyRunTitle(task: TaskHistoryItem) {
  if (task.sourceFlowTitle) return task.sourceFlowTitle
  if (task.sourcePromptTitle) return task.sourcePromptTitle

  const sourceRun = lineageSource(task)
  if (sourceRun?.summary) {
    return sourceRun.summary
  }

  return compactExecutionInput(task.input)
}

function compactExecutionInput(input: string) {
  const firstLine = input
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean) || '未命名 AI 任务'
  return firstLine.length > 120 ? `${firstLine.slice(0, 120)}…` : firstLine
}

function canCompareWithSource(task: TaskHistoryItem) {
  const sourceRun = lineageSource(task)
  if (!sourceRun) {
    return false
  }
  if (task.rerunOfTaskId) {
    return true
  }
  return !isFailed(sourceRun) && !isFailed(task)
}

function lineageMode(task: TaskHistoryItem): 'rerun' | 'continuation' | 'input-variant' {
  if (task.rerunOfTaskId) return 'rerun'
  if (task.continuedFromTaskId) return 'continuation'
  return 'input-variant'
}

function openComparison(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem,
  mode: 'rerun' | 'continuation' | 'input-variant'
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

  const targetRun = workspace.tasks.find((task) => task.id === runId)
  if (!targetRun) {
    ElMessage.warning('指定的运行记录已不存在或无法访问')
    await syncRunRoute(null, 'replace')
    return
  }

  if (!filteredTasks.value.some((task) => task.id === runId)) {
    resetHistoryExplorer()
  }

  expandedRunIds.value = [runId]
  focusedRunId.value = runId
  await nextTick()
  document.getElementById(`history-run-${runId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function resetHistoryExplorer() {
  historyQuery.value = ''
  historyScope.value = 'all'
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

function taskFlowStillAvailable(task: TaskHistoryItem) {
  return Boolean(task.sourceFlowId && workspace.flowDrafts.some((flow) => flow.id === task.sourceFlowId))
}

function inspectTaskInFlow(task: TaskHistoryItem) {
  const flow = workspace.flowDrafts.find((item) => item.id === task.sourceFlowId)
  if (!flow) {
    ElMessage.warning('这个运行对应的 Flow 已不存在，历史快照仍可在当前页面查看')
    return
  }

  const historicalNodeIds = task.flowRunTrace?.nodes.map((node) => node.nodeId)
    || task.flowRunSnapshot?.nodes.map((node) => node.id)
    || []
  const nodeId = historicalNodeIds.find((id) => flow.nodes.some((node) => node.id === id))
  workspace.selectFlowDraft(flow.id)
  router.push({
    path: '/workflows',
    query: { flow: flow.id, run: task.id, ...(nodeId ? { node: nodeId } : {}) }
  })
}

async function saveRunAsPrompt(task: TaskHistoryItem) {
  const existingPrompt = workspace.taskPromptsByRunId[task.id]
  if (existingPrompt) {
    router.push({ path: '/prompts', query: { prompt: existingPrompt.id } })
    return
  }

  const prompt = await workspace.saveHistoricalResultAsPrompt(task)
  if (prompt) {
    ElMessage.success('历史结果已沉淀为 Prompt')
  }
}

async function createFlowFromRun(task: TaskHistoryItem) {
  const flow = await workspace.createFlowFromHistoricalResult(task)
  if (!flow) {
    return
  }
  ElMessage.success('已从历史 Result 创建 Flow')
  router.push({ path: '/workflows', query: { flow: flow.id } })
}

function createInputVariant(task: TaskHistoryItem) {
  workspace.prepareTaskInputVariant(task)
  router.push('/tasks')
}

function traceNavigableNodeIds(task: TaskHistoryItem) {
  const trace = task.flowRunTrace
  const flow = trace ? workspace.flowDrafts.find((item) => item.id === trace.flowId) : null
  return flow
    ? trace?.nodes.filter((node) => flow.nodes.some((item) => item.id === node.nodeId)).map((node) => node.nodeId) || []
    : []
}

function openFlowTraceNode(task: TaskHistoryItem, nodeId: string) {
  const trace = task.flowRunTrace
  const flow = trace ? workspace.flowDrafts.find((item) => item.id === trace.flowId) : null
  if (!flow || !flow.nodes.some((node) => node.id === nodeId)) {
    ElMessage.warning('这个历史节点已不在当前 Flow 中')
    return
  }
  workspace.selectFlowDraft(flow.id)
  router.push({ path: '/workflows', query: { flow: flow.id, node: nodeId, run: task.id } })
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
