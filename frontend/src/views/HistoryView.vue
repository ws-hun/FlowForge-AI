<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">历史记录</p>
      <h1>时间线，而不是表格。</h1>
      <p>保留每一次 AI 工作流执行的上下文、摘要和结果。</p>
    </header>

    <div class="timeline">
      <article v-for="task in workspace.tasks" :key="task.id" class="timeline-item">
        <span class="timeline-dot"></span>
        <div class="timeline-content soft-card">
          <div class="row-between">
            <strong>{{ task.input }}</strong>
            <small>{{ new Date(task.createdAt).toLocaleString() }}</small>
          </div>
          <div v-if="task.sourceFlowTitle || task.sourcePromptTitle" class="history-source-row">
            <span class="badge">{{ task.sourceFlowTitle ? 'Flow' : 'Prompt' }}</span>
            <strong>{{ task.sourceFlowTitle || task.sourcePromptTitle }}</strong>
          </div>
          <div v-if="rerunSource(task)" class="history-lineage-note">
            <span>重跑自</span>
            <strong>
              {{
                formatExecutionSource(rerunSource(task)?.provider, rerunSource(task)?.model, rerunSource(task)?.totalTokens) ||
                '来源运行'
              }}
            </strong>
          </div>
          <p class="muted">{{ task.summary }}</p>
          <div class="history-reuse-row">
            <span class="run-provenance">
              {{ formatExecutionSource(task.provider, task.model, task.totalTokens) || '已保存服务端执行输入' }}
            </span>
            <div class="history-reuse-actions">
              <button
                v-if="rerunSource(task)"
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
          <el-collapse>
            <el-collapse-item title="查看结果" :name="task.id">
              <AiResultDocument
                :summary="task.summary"
                :result="task.result"
                :provider="task.provider"
                :model="task.model"
                :input-tokens="task.inputTokens"
                :output-tokens="task.outputTokens"
                :total-tokens="task.totalTokens"
                compact
                :show-raw="false"
              />
              <FlowRunSnapshot
                v-if="task.flowRunSnapshot"
                :snapshot="task.flowRunSnapshot"
                can-create-flow
                :can-reuse-run-settings="flowStillAvailable(task.flowRunSnapshot)"
                :creating="workspace.flowLoading"
                @create-flow="createFlowFromSnapshot"
                @reuse-run-settings="reuseFlowRunSettings"
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
      @close="closeComparison"
    />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import RunComparisonDialog from '@/components/ai/RunComparisonDialog.vue'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatExecutionSource } from '@/utils/aiProvider'
import type { FlowRunSnapshot as FlowRunSnapshotType, TaskHistoryItem } from '@/types'

const router = useRouter()
const workspace = useWorkspaceStore()
const comparisonOpen = ref(false)
const comparisonSource = ref<TaskHistoryItem | null>(null)
const comparisonTarget = ref<TaskHistoryItem | null>(null)

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
    openComparison(sourceRun, targetRun)
  } else if (result) {
    router.push('/tasks')
  }
}

function rerunSource(task: TaskHistoryItem) {
  if (!task.rerunOfTaskId) {
    return null
  }
  return workspace.tasks.find((item) => item.id === task.rerunOfTaskId) || null
}

function openComparison(sourceRun: TaskHistoryItem, targetRun: TaskHistoryItem) {
  comparisonSource.value = sourceRun
  comparisonTarget.value = targetRun
  comparisonOpen.value = true
}

function compareWithSource(targetRun: TaskHistoryItem) {
  const sourceRun = rerunSource(targetRun)
  if (sourceRun) {
    openComparison(sourceRun, targetRun)
  }
}

function closeComparison() {
  comparisonOpen.value = false
  comparisonSource.value = null
  comparisonTarget.value = null
}

function flowStillAvailable(snapshot: FlowRunSnapshotType) {
  return workspace.flowDrafts.some((flow) => flow.id === snapshot.flowId)
}

function reuseFlowRunSettings(snapshot: FlowRunSnapshotType) {
  if (!workspace.prepareFlowRunFromSnapshot(snapshot)) {
    ElMessage.warning('原 Flow 已不存在，请从快照创建新的 Flow')
    return
  }

  ElMessage.success('已将本次运行配置带回原 Flow')
  router.push('/workflows')
}

async function createFlowFromSnapshot(snapshot: FlowRunSnapshotType) {
  const flow = await workspace.createFlowFromRunSnapshot(snapshot)
  if (!flow) {
    return
  }

  ElMessage.success('已创建新的 Flow，并带入本次运行上下文')
  router.push('/workflows')
}
</script>
