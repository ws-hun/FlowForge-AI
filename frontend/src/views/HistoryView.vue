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
          <p class="muted">{{ task.summary }}</p>
          <el-collapse>
            <el-collapse-item title="查看结果" :name="task.id">
              <AiResultDocument :summary="task.summary" :result="task.result" compact :show-raw="false" />
              <FlowRunSnapshot
                v-if="task.flowRunSnapshot"
                :snapshot="task.flowRunSnapshot"
                can-create-flow
                @create-flow="createFlowFromSnapshot"
              />
            </el-collapse-item>
          </el-collapse>
        </div>
      </article>
      <div v-if="!workspace.tasks.length" class="empty-state">暂无历史记录</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FlowRunSnapshot as FlowRunSnapshotType } from '@/types'

const router = useRouter()
const workspace = useWorkspaceStore()

async function createFlowFromSnapshot(snapshot: FlowRunSnapshotType) {
  const flow = await workspace.createFlowFromRunSnapshot(snapshot)
  if (!flow) {
    return
  }

  ElMessage.success('已创建新的 Flow，并带入本次运行上下文')
  router.push('/workflows')
}
</script>
