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
        <div v-if="hasTaskSource" class="task-source-context">
          <div class="task-source-context-copy">
            <span>{{ sourceLabel }}</span>
            <strong>{{ sourceTitle }}</strong>
            <p>{{ sourceDescription }}</p>
          </div>
          <div class="task-source-context-actions">
            <button v-if="workspace.taskSourceFlowId" type="button" class="ghost-button" @click="returnToFlow">
              回到 Flow
            </button>
            <button v-else type="button" class="ghost-button" @click="openPromptLibrary">
              查看 Prompt
            </button>
            <button type="button" class="ghost-button" @click="detachTaskSource">脱离来源</button>
          </div>
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
const hasTaskSource = computed(() => Boolean(workspace.taskSourceFlowTitle || workspace.taskSourcePromptTitle))
const sourceLabel = computed(() => (workspace.taskSourceFlowTitle ? 'Flow context' : 'Prompt context'))
const sourceTitle = computed(() => workspace.taskSourceFlowTitle || workspace.taskSourcePromptTitle)
const sourceDescription = computed(() => {
  return workspace.taskSourceFlowTitle
    ? '这次执行会保留与原 Flow 的关联，结果可回到工作流中继续迭代。'
    : '这次执行会保留与原 Prompt 的关联，方便把有效工作方式沉淀为资产。'
})

function goToApiKeys() {
  router.push('/api-keys')
}

function returnToFlow() {
  if (workspace.taskSourceFlowId) {
    workspace.selectFlowDraft(workspace.taskSourceFlowId)
  }
  router.push('/workflows')
}

function openPromptLibrary() {
  router.push('/prompts')
}

function detachTaskSource() {
  workspace.clearTaskSource()
}
</script>
