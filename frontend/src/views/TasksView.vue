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
        <div v-if="workspace.taskSourcePromptTitle || workspace.taskSourceFlowTitle" class="source-note">
          <span>{{ workspace.taskSourceFlowTitle ? '来自 Flow' : '来自 Prompt' }}</span>
          <strong>{{ workspace.taskSourceFlowTitle || workspace.taskSourcePromptTitle }}</strong>
        </div>
        <div v-if="!taskReadyToRun" class="command-readiness-note">
          <span class="flow-run-dot warning"></span>
          <div>
            <strong>需要配置 AI Provider</strong>
            <p>任务执行依赖一个已激活的 Provider。配置后即可运行当前命令。</p>
          </div>
          <button type="button" class="secondary-button" @click="goToApiKeys">配置 Provider</button>
        </div>
        <div class="composer-footer">
          <span>{{ workspace.activeProvider?.provider || '请先配置 Provider' }}</span>
          <button
            class="primary-button"
            :disabled="workspace.running || !workspace.taskInput.trim() || !taskReadyToRun"
            @click="workspace.executeTask"
          >
            {{ workspace.running ? '执行中...' : '执行任务' }}
          </button>
        </div>
      </section>

      <section class="result-document">
        <AiResultDocument
          v-if="workspace.latestResult"
          :summary="workspace.latestResult.summary"
          :result="workspace.latestResult.result"
          :raw="workspace.latestResult.raw"
        />
        <div v-else class="empty-state">结果会以文档形式显示在这里。</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()
const taskReadyToRun = computed(() => Boolean(workspace.activeProvider))

function goToApiKeys() {
  router.push('/api-keys')
}
</script>
