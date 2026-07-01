<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">AI Command Workspace</p>
      <h1>写下任务，然后执行。</h1>
      <p>这不是聊天窗口，而是一个面向 AI 工作流的命令空间。</p>
    </header>

    <div class="two-column command-layout">
      <section class="command-input">
        <textarea
          v-model="workspace.taskInput"
          class="workspace-input"
          placeholder="描述你希望 AI 完成的任务..."
        ></textarea>
        <div class="composer-footer">
          <span>{{ workspace.activeProvider?.provider || '请先配置 Provider' }}</span>
          <button class="primary-button" :disabled="workspace.running" @click="workspace.executeTask">
            {{ workspace.running ? '执行中...' : '执行任务' }}
          </button>
        </div>
      </section>

      <section class="result-document">
        <div v-if="workspace.latestResult" class="result-doc surface">
          <p class="page-kicker">摘要</p>
          <h2>{{ workspace.latestResult.summary }}</h2>

          <div class="doc-section">
            <h3>关键要点</h3>
            <ul>
              <li v-for="point in keyPoints" :key="point">{{ point }}</li>
            </ul>
          </div>

          <div class="doc-section">
            <h3>结果</h3>
            <div class="document-body">{{ workspace.latestResult.result }}</div>
          </div>

          <el-collapse>
            <el-collapse-item title="原始 JSON" name="raw">
              <pre class="code-block">{{ formattedRaw }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div v-else class="empty-state">结果会以文档形式显示在这里。</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()

const formattedRaw = computed(() => {
  if (!workspace.latestResult?.raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(workspace.latestResult.raw), null, 2)
  } catch {
    return workspace.latestResult.raw
  }
})

const keyPoints = computed(() => {
  const text = workspace.latestResult?.result || workspace.latestResult?.summary || ''
  return text
    .split(/\n|。|；|;|\./)
    .map((item) => item.replace(/^[-*•\d.、\s]+/, '').trim())
    .filter(Boolean)
    .slice(0, 5)
})
</script>
