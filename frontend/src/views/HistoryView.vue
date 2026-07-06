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
              <pre class="code-block">{{ task.result }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </article>
      <div v-if="!workspace.tasks.length" class="empty-state">暂无历史记录</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()
</script>
