<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">AI Command Workspace</p>
      <h1>写下任务，然后执行。</h1>
      <p>这不是聊天窗口，而是一个面向 AI 工作流的命令空间。</p>
    </header>

    <div class="two-column command-layout">
      <section class="command-input">
        <div v-if="workspace.taskSourceFlowId" class="flow-run-brief-heading">
          <span class="section-kicker">Run Brief</span>
          <p>为这次 Flow 运行补充目标、约束或输出偏好。</p>
        </div>
        <div
          v-if="workspace.taskSourceFlowId && workspace.taskSourceFlowVariables.length"
          class="flow-variable-inputs task-flow-variable-inputs"
          :class="{ 'has-missing': workspace.missingTaskSourceFlowVariables.length }"
        >
          <div class="flow-variable-heading">
            <div>
              <span class="section-kicker">Flow 变量</span>
              <p>确认这次运行需要注入的具体值。</p>
            </div>
            <span>
              {{ workspace.missingTaskSourceFlowVariables.length ? `${workspace.missingTaskSourceFlowVariables.length} 项待填写` : '已就绪' }}
            </span>
          </div>
          <div class="flow-variable-grid">
            <label
              v-for="variable in workspace.taskSourceFlowVariables"
              :key="variable"
              class="flow-variable-field"
              :class="{ 'is-missing': !workspace.taskSourceFlowVariableValues[variable]?.trim() }"
            >
              <span>{{ '{' + variable + '}' }}</span>
              <textarea
                v-model="workspace.taskSourceFlowVariableValues[variable]"
                class="quiet-textarea"
                :placeholder="`填写 ${variable}`"
              ></textarea>
            </label>
          </div>
          <div v-if="workspace.missingTaskSourceFlowVariables.length" class="flow-variable-readiness">
            <span class="flow-run-dot warning"></span>
            <p>补齐变量后即可执行这个 Flow。</p>
          </div>
        </div>
        <textarea
          v-model="workspace.taskInput"
          class="workspace-input"
          :placeholder="taskInputPlaceholder"
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
            <button v-else-if="workspace.taskSourceRunId" type="button" class="ghost-button" @click="returnToHistory">
              查看 History
            </button>
            <button v-else type="button" class="ghost-button" @click="openPromptLibrary">
              查看 Prompt
            </button>
            <button type="button" class="ghost-button" @click="detachTaskSource">脱离来源</button>
          </div>
        </div>
        <div v-if="!providerReadyToRun" class="command-readiness-note">
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
            :disabled="workspace.running || !workspace.canExecuteTask || !providerReadyToRun"
            @click="workspace.executeTask"
          >
            {{ workspace.running ? '执行中...' : '执行任务' }}
          </button>
        </div>
      </section>

      <section class="result-document">
        <template v-if="workspace.latestResult">
          <AiResultDocument
            :summary="workspace.latestResult.summary"
            :result="workspace.latestResult.result"
            :raw="workspace.latestResult.raw"
            :provider="workspace.latestResult.provider"
            :model="workspace.latestResult.model"
            :input-tokens="workspace.latestResult.inputTokens"
            :output-tokens="workspace.latestResult.outputTokens"
            :total-tokens="workspace.latestResult.totalTokens"
          />
          <div v-if="workspace.canPromoteLatestTask" class="task-result-actions">
            <div>
              <span class="section-kicker">Reuse</span>
              <strong>让这次有效执行成为下一次创作的起点。</strong>
            </div>
            <div class="task-result-action-buttons">
              <button
                type="button"
                class="ghost-button"
                :disabled="workspace.taskAssetLoading || Boolean(workspace.latestTaskPrompt)"
                @click="saveLatestTaskAsPrompt"
              >
                {{ workspace.latestTaskPrompt ? '已沉淀为 Prompt' : workspace.taskAssetLoading ? '沉淀中...' : '沉淀为 Prompt' }}
              </button>
              <button
                type="button"
                class="secondary-button"
                :disabled="workspace.taskAssetLoading || workspace.flowLoading"
                @click="createFlowFromLatestTask"
              >
                {{ workspace.flowLoading ? '创建中...' : '创建 Flow' }}
              </button>
            </div>
          </div>
        </template>
        <div v-else class="empty-state">结果会以文档形式显示在这里。</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()
const providerReadyToRun = computed(() => Boolean(workspace.activeProvider))
const hasTaskSource = computed(() =>
  Boolean(workspace.taskSourceFlowTitle || workspace.taskSourcePromptTitle || workspace.taskSourceRunId)
)
const sourceLabel = computed(() => {
  if (workspace.taskSourceFlowTitle) return 'Flow context'
  if (workspace.taskSourceRunId) return 'Historical result'
  return 'Prompt context'
})
const sourceTitle = computed(
  () => workspace.taskSourceFlowTitle || workspace.taskSourceRunSummary || workspace.taskSourcePromptTitle
)
const taskInputPlaceholder = computed(() => {
  if (workspace.taskSourceFlowId) {
    return '可选：补充本次运行说明、目标用户、输出格式或约束条件...'
  }
  if (workspace.taskSourceRunId) {
    return '描述下一步要如何修改、深化或转化这个结果...'
  }
  return '描述你希望 AI 完成的任务...'
})
const sourceDescription = computed(() => {
  if (workspace.taskSourceFlowTitle) {
    return 'Flow 将按已保存的节点和 Prompt 执行。这里的内容会作为本次运行简报固定保存。'
  }
  if (workspace.taskSourceRunId) {
    return '后端会读取已保存的完整结果，并将这里的新方向编译为下一次可追溯执行。'
  }
  return '这次执行会保留与原 Prompt 的关联，方便把有效工作方式沉淀为资产。'
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

function returnToHistory() {
  router.push('/history')
}

function detachTaskSource() {
  workspace.clearTaskSource()
}

async function saveLatestTaskAsPrompt() {
  const prompt = await workspace.saveLatestTaskAsPrompt()
  if (prompt) {
    ElMessage.success('这次任务已沉淀为 Prompt')
  }
}

async function createFlowFromLatestTask() {
  const flow = await workspace.createFlowFromLatestTask()
  if (!flow) {
    return
  }

  ElMessage.success('已从 AI Command 创建 Flow')
  router.push('/workflows')
}
</script>
