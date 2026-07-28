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
              v-for="(variable, index) in workspace.taskSourceFlowVariables"
              :key="variable"
              class="flow-variable-field"
              :class="{ 'is-missing': !workspace.taskSourceFlowVariableValues[variable]?.trim() }"
            >
              <span>{{ '{' + variable + '}' }}</span>
              <textarea
                :id="`task-flow-variable-${index}`"
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
            <button v-if="workspace.taskSourceFlowId" type="button" class="ghost-button" @click="returnToFlow()">
              回到 Flow
            </button>
            <button
              v-else-if="workspace.taskSourceRunId || workspace.taskInputVariantOfTaskId"
              type="button"
              class="ghost-button"
              @click="returnToHistory"
            >
              查看 History
            </button>
            <button v-else type="button" class="ghost-button" @click="openPromptLibrary">
              查看 Prompt
            </button>
            <button type="button" class="ghost-button" @click="detachTaskSource">脱离来源</button>
          </div>
        </div>
        <FlowExecutionInputPreview
          v-if="workspace.taskSourceFlowId"
          :key="workspace.taskSourceFlowId"
          :flow-id="workspace.taskSourceFlowId"
          :runtime-context="workspace.taskInput"
          :variable-values="workspace.taskSourceFlowVariableValues"
          :source-version="taskSourceFlow?.updatedAt"
          node-action-label="在 Flow 中打开"
          variable-action-label="填写变量"
          @open-node="returnToFlow"
          @focus-variable="focusTaskFlowVariable"
        />
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
            :duration-ms="workspace.latestResult.durationMs"
          />
          <div v-if="workspace.canPromoteLatestTask" class="task-result-actions">
            <div>
              <span class="section-kicker">Reuse</span>
              <strong>让这次有效执行成为下一次创作的起点。</strong>
            </div>
            <div class="task-result-action-buttons">
              <button
                v-if="workspace.latestResult.taskId"
                type="button"
                class="ghost-button"
                @click="openLatestResultHistory"
              >
                在 History 打开
              </button>
              <button type="button" class="ghost-button" @click="continueLatestResult">
                继续此结果
              </button>
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
import { computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import FlowExecutionInputPreview from '@/components/flow/FlowExecutionInputPreview.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()
const providerReadyToRun = computed(() => Boolean(workspace.activeProvider))
const hasTaskSource = computed(() =>
  Boolean(
    workspace.taskSourceFlowTitle ||
      workspace.taskSourcePromptTitle ||
      workspace.taskSourceRunId ||
      workspace.taskInputVariantOfTaskId
  )
)
const taskSourceFlow = computed(() =>
  workspace.taskSourceFlowId
    ? workspace.flowDrafts.find((flow) => flow.id === workspace.taskSourceFlowId) || null
    : null
)
const sourceLabel = computed(() => {
  if (workspace.taskSourceFlowTitle) return 'Flow context'
  if (workspace.taskSourceRunId) return 'Historical result'
  if (workspace.taskInputVariantOfTaskId) return 'Historical input'
  return 'Prompt context'
})
const sourceTitle = computed(
  () =>
    workspace.taskSourceFlowTitle ||
    workspace.taskSourceRunSummary ||
    workspace.taskInputVariantSourceTitle ||
    workspace.taskSourcePromptTitle
)
const taskInputPlaceholder = computed(() => {
  if (workspace.taskSourceFlowId) {
    return '可选：补充本次运行说明、目标用户、输出格式或约束条件...'
  }
  if (workspace.taskSourceRunId) {
    return '描述下一步要如何修改、深化或转化这个结果...'
  }
  if (workspace.taskInputVariantOfTaskId) {
    return '调整这份固定执行输入，然后创建一个独立变体...'
  }
  return '描述你希望 AI 完成的任务...'
})
const sourceDescription = computed(() => {
  if (workspace.taskSourceFlowTitle) {
    return 'Flow 将按已保存的节点和 Prompt 执行。这里的调整会同步回 Flow 的 Run Brief 草稿。'
  }
  if (workspace.taskSourceRunId) {
    return '后端会读取已保存的完整结果，并将这里的新方向编译为下一次可追溯执行。'
  }
  if (workspace.taskInputVariantOfTaskId) {
    return '独立输入变体 · 保留来源运行 · 不继承原 Flow 快照'
  }
  return '这次执行会保留与原 Prompt 的关联，方便把有效工作方式沉淀为资产。'
})

watch(
  [() => workspace.taskInput, () => workspace.taskSourceFlowVariableValues],
  () => workspace.saveTaskSourceFlowRunDraft(),
  { deep: true }
)

function goToApiKeys() {
  router.push('/api-keys')
}

async function focusTaskFlowVariable(variable: string) {
  const variableIndex = workspace.taskSourceFlowVariables.indexOf(variable)
  if (variableIndex < 0) {
    ElMessage.warning('这个变量已不存在，请刷新预览')
    return
  }

  await nextTick()
  const input = document.getElementById(`task-flow-variable-${variableIndex}`) as HTMLTextAreaElement | null
  input?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  input?.focus({ preventScroll: true })
}

function returnToFlow(nodeId?: string) {
  const flowId = workspace.taskSourceFlowId
  if (flowId) {
    workspace.saveTaskSourceFlowRunDraft()
    workspace.selectFlowDraft(flowId)
    router.push({ path: '/workflows', query: { flow: flowId, ...(nodeId ? { node: nodeId } : {}) } })
    return
  }
  router.push('/workflows')
}

function openPromptLibrary() {
  const promptId = workspace.taskSourcePromptId
  router.push(promptId ? { path: '/prompts', query: { prompt: promptId } } : '/prompts')
}

function returnToHistory() {
  const runId = workspace.taskSourceRunId || workspace.taskInputVariantOfTaskId
  router.push(runId ? { path: '/history', query: { run: runId } } : '/history')
}

function openLatestResultHistory() {
  const runId = workspace.latestResult?.taskId
  if (runId) {
    router.push({ path: '/history', query: { run: runId } })
  }
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

function continueLatestResult() {
  if (workspace.prepareLatestResultContinuation()) {
    ElMessage.success('已将当前结果作为下一轮来源')
  }
}

async function createFlowFromLatestTask() {
  const flow = await workspace.createFlowFromLatestTask()
  if (!flow) {
    return
  }

  ElMessage.success('已从 AI Command 创建 Flow')
  router.push({ path: '/workflows', query: { flow: flow.id } })
}
</script>
