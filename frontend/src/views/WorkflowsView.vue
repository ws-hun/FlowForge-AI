<template>
  <section class="flow-workspace">
    <header class="quiet-header">
      <p class="page-kicker">Flow Space</p>
      <h1>把 Prompt 连接成可执行 Flow。</h1>
      <p>从一个目标开始，组织输入、Prompt、AI 执行和结构化输出，逐步形成可复用的工作流资产。</p>
    </header>

    <div class="flow-builder-layout">
      <aside class="surface flow-draft-panel">
        <div class="panel-heading">
          <span class="section-kicker">New Flow</span>
          <h2>描述你想搭建的工作流</h2>
        </div>

        <textarea
          v-model="flowIntent"
          class="quiet-textarea flow-intent-input"
          placeholder="例如：把一个产品想法依次拆解成 PRD、接口草案和任务清单..."
          @input="selectedFlowTemplate = ''"
        ></textarea>

        <button
          type="button"
          :class="workspace.activeFlow ? 'secondary-button' : 'primary-button'"
          :disabled="!flowIntent.trim() || workspace.flowLoading"
          @click="createFlow"
        >
          {{ workspace.flowLoading ? '保存中...' : '创建 Flow 草稿' }}
        </button>

        <section class="flow-template-pack">
          <div class="section-heading compact">
            <h3>Flow Templates</h3>
            <span>选择一个创作起点</span>
          </div>

          <button
            v-for="template in flowTemplates"
            :key="template.title"
            type="button"
            class="flow-template-option"
            :class="{ active: selectedFlowTemplate === template.title }"
            @click="useFlowTemplate(template.intent)"
          >
            <span>{{ template.category }}</span>
            <strong>{{ template.title }}</strong>
            <small>{{ template.description }}</small>
            <em>{{ template.nodes.length }} Prompt nodes</em>
          </button>

          <div v-if="selectedFlowTemplateDetail" class="flow-template-preview">
            <span>Template Flow</span>
            <strong>将生成 {{ selectedFlowTemplateDetail.nodes.length + 3 }} 个节点</strong>
            <ol>
              <li>Intent</li>
              <li v-for="node in selectedFlowTemplateDetail.nodes" :key="node.title">{{ node.title }}</li>
              <li>AI Execution</li>
              <li>Structured Result</li>
            </ol>
          </div>
        </section>

        <div class="draft-list">
          <div class="section-heading compact">
            <h3>继续创作</h3>
            <span>{{ workspace.flowDrafts.length ? `${workspace.flowDrafts.length} 个草稿` : '暂无草稿' }}</span>
          </div>

          <button
            v-for="flow in workspace.flowDrafts"
            :key="flow.id"
            type="button"
            class="draft-item"
            :class="{ active: flow.id === workspace.activeFlowId }"
            @click="selectFlow(flow.id)"
          >
            <strong>{{ flow.title }}</strong>
            <span>{{ formatDate(flow.updatedAt) }}</span>
          </button>

          <p v-if="!workspace.flowDrafts.length" class="quiet-note">
            第一个 Flow 会从输入目标、AI 执行和结构化输出三步开始。
          </p>
        </div>
      </aside>

      <section class="surface flow-canvas-panel">
        <div v-if="workspace.activeFlow" class="flow-canvas-header">
          <div>
            <span class="badge">Draft Flow</span>
            <h2>{{ workspace.activeFlow.title }}</h2>
            <p>{{ workspace.activeFlow.description }}</p>
          </div>
          <div class="flow-run-actions">
            <button type="button" class="ghost-button" :disabled="workspace.running" @click="sendFlowToTaskWorkspace">
              带入 Task
            </button>
            <button
              type="button"
              class="primary-button"
              :disabled="workspace.running || !flowReadyToRun"
              @click="executeFlowNow"
            >
              {{ workspace.running ? '执行中...' : '执行 Flow' }}
            </button>
          </div>
        </div>

        <div v-if="workspace.activeFlow && !flowReadyToRun" class="flow-readiness-note">
          <span class="flow-run-dot warning"></span>
          <div>
            <strong>需要配置 AI Provider</strong>
            <p>Flow 执行依赖一个已激活的 Provider。配置后即可运行当前工作流。</p>
          </div>
          <button type="button" class="secondary-button" @click="goToApiKeys">配置 Provider</button>
        </div>

        <div v-if="workspace.activeFlow && flowRunPhase !== 'idle'" class="flow-run-signal" :class="flowRunPhase">
          <span class="flow-run-dot"></span>
          <div>
            <strong>{{ flowRunTitle }}</strong>
            <p>{{ flowRunDescription }}</p>
          </div>
        </div>

        <div v-if="workspace.activeFlow" class="flow-map">
          <template v-for="(node, index) in workspace.activeFlow.nodes" :key="node.id">
            <button
              type="button"
              class="flow-card-node"
              :class="[node.type, `is-${nodeStatus(node.id)}`, { active: selectedNode?.id === node.id }]"
              @click="selectedNodeId = node.id"
            >
              <div class="flow-node-meta">
                <span class="flow-node-type">{{ nodeLabel(node.type) }}</span>
                <span class="flow-node-state">{{ nodeStateLabel(nodeStatus(node.id)) }}</span>
              </div>
              <strong>{{ node.title }}</strong>
              <p>{{ node.description }}</p>
            </button>
            <div
              v-if="index < workspace.activeFlow.nodes.length - 1"
              class="flow-connector"
              :class="{ active: connectorCompleted(index), running: connectorRunning(index) }"
            ></div>
          </template>
        </div>

        <section v-if="workspace.activeFlow" class="flow-run-brief">
          <div class="section-heading compact">
            <div>
              <h3>Run Brief</h3>
              <span>执行前确认 AI 将接收的工作上下文</span>
            </div>
            <span>{{ activeProviderLabel }}</span>
          </div>

          <div class="flow-brief-strip">
            <div v-for="item in flowBriefItems" :key="item.label" class="flow-brief-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <div v-if="flowPromptVariables.length" class="flow-variable-inputs">
            <div class="flow-variable-heading">
              <div>
                <span class="section-kicker">Prompt 变量</span>
                <p>为本次运行填写 Prompt 需要的上下文。</p>
              </div>
              <span>{{ flowPromptVariables.length }} 个变量</span>
            </div>

            <div class="flow-variable-grid">
              <label v-for="variable in flowPromptVariables" :key="variable" class="flow-variable-field">
                <span>{{ '{' + variable + '}' }}</span>
                <textarea
                  v-model="flowVariableValues[variable]"
                  class="quiet-textarea"
                  :placeholder="`填写 ${variable}`"
                ></textarea>
              </label>
            </div>
          </div>

          <textarea
            v-model="flowRunContext"
            class="quiet-textarea flow-context-input"
            placeholder="为本次运行补充上下文，例如目标用户、输出格式、约束条件或业务背景..."
          ></textarea>

          <details class="flow-input-preview">
            <summary>查看本次 AI 输入</summary>
            <pre>{{ flowRunInputPreview }}</pre>
          </details>
        </section>

        <section v-if="workspace.activeFlow && flowExecutionVisible && activeFlowResult" class="flow-result-loop">
          <div class="flow-result-loop-actions">
            <div>
              <span class="section-kicker">Iteration</span>
              <strong>{{ flowResultHeading }}</strong>
            </div>
            <div class="flow-result-actions">
              <button type="button" class="secondary-button" @click="useLatestResultAsRunContext">
                带入下一轮
              </button>
              <button type="button" class="ghost-button" :disabled="savingResultPrompt" @click="saveLatestResultAsPrompt">
                {{ savingResultPrompt ? '保存中...' : '保存为 Prompt' }}
              </button>
              <button
                type="button"
                class="ghost-button"
                :disabled="savingResultPrompt || workspace.flowLoading"
                @click="saveLatestResultAndAddToFlow"
              >
                保存并加入 Flow
              </button>
            </div>
          </div>
          <FlowRunSnapshot v-if="activeFlowRunSnapshot" :snapshot="activeFlowRunSnapshot" />
          <AiResultDocument
            class="flow-execution-result"
            :summary="activeFlowResult.summary"
            :result="activeFlowResult.result"
            :raw="activeFlowResult.raw"
            compact
            :show-raw="false"
          />
        </section>

        <div v-if="!workspace.activeFlow" class="flow-empty-state">
          <span class="badge">Canvas</span>
          <strong>先创建一个 Flow 草稿</strong>
          <p>FlowForge 会生成一个安静的工作流骨架，你可以继续加入 Prompt 节点。</p>
        </div>
      </section>

      <aside class="surface flow-inspector">
        <div v-if="workspace.activeFlow" class="flow-asset-editor">
          <div class="panel-heading">
            <span class="section-kicker">Flow Asset</span>
            <h2>调整 Flow 目标</h2>
          </div>
          <input v-model="flowTitle" class="quiet-input" placeholder="Flow 标题" />
          <textarea v-model="flowDescription" class="quiet-textarea" placeholder="Flow 目标"></textarea>
          <div class="flow-editor-actions">
            <button
              type="button"
              class="secondary-button"
              :disabled="!flowMetaChanged || workspace.flowLoading"
              @click="saveFlowMeta"
            >
              保存
            </button>
            <button type="button" class="secondary-button" :disabled="workspace.flowLoading" @click="duplicateActiveFlow">
              创建变体
            </button>
            <button type="button" class="danger-button" :disabled="workspace.flowLoading" @click="confirmDeleteFlow">
              删除
            </button>
          </div>
        </div>

        <section v-if="workspace.activeFlow" class="flow-revision-section">
          <div class="section-heading compact">
            <div>
              <span class="section-kicker">Revisions</span>
              <h3>回到任意创作节点</h3>
            </div>
            <span>{{ flowVersions.length ? `${flowVersions.length} 个快照` : '编辑后保存' }}</span>
          </div>

          <div v-if="flowVersionsLoading" class="version-list">
            <article v-for="item in 2" :key="item" class="version-item skeleton-run"></article>
          </div>
          <div v-else-if="flowVersions.length" class="version-list">
            <button
              v-for="version in flowVersions"
              :key="version.id"
              type="button"
              class="version-item"
              :class="{ active: selectedFlowVersion?.id === version.id }"
              @click="selectedFlowVersion = version"
            >
              <span>v{{ version.versionNumber }}</span>
              <strong>{{ version.title }}</strong>
              <time>{{ formatDate(version.createdAt) }}</time>
            </button>
          </div>
          <div v-else class="quiet-empty">
            第一次调整节点或 Flow 目标后，当前状态会作为可恢复的修订保存在这里。
          </div>

          <div v-if="selectedFlowVersion" class="version-preview flow-version-preview">
            <div class="row-between">
              <span class="badge">v{{ selectedFlowVersion.versionNumber }}</span>
              <button
                type="button"
                class="ghost-button"
                :disabled="restoringFlowVersion || workspace.flowLoading"
                @click="restoreFlowVersionSnapshot(selectedFlowVersion)"
              >
                {{ restoringFlowVersion ? '恢复中...' : '恢复此修订' }}
              </button>
            </div>
            <strong>{{ selectedFlowVersion.title }}</strong>
            <p>{{ selectedFlowVersion.description }}</p>
            <div class="flow-version-node-sequence" aria-label="修订节点顺序">
              <span v-for="node in selectedFlowVersion.nodes" :key="node.id">{{ nodeLabel(node.type) }}</span>
            </div>

            <div v-if="selectedFlowVersionDiff" class="flow-version-diff">
              <div class="flow-version-diff-heading">
                <span>恢复影响</span>
                <strong>
                  {{ selectedFlowVersionDiff.hasChanges ? `将恢复 ${selectedFlowVersionDiff.changeCount} 处变化` : '与当前草稿一致' }}
                </strong>
              </div>
              <ul v-if="selectedFlowVersionDiff.hasChanges" class="flow-version-diff-list">
                <li v-if="selectedFlowVersionDiff.titleChanged">
                  <span class="flow-version-diff-kind">标题</span>
                  <p>会恢复为“{{ selectedFlowVersion.title }}”</p>
                </li>
                <li v-if="selectedFlowVersionDiff.descriptionChanged">
                  <span class="flow-version-diff-kind">目标</span>
                  <p>会恢复这个修订中的 Flow 目标</p>
                </li>
                <li v-for="change in selectedFlowVersionDiff.nodeChanges" :key="`${change.kind}-${change.id}`">
                  <span class="flow-version-diff-kind" :class="change.kind">{{ flowRevisionChangeLabel(change.kind) }}</span>
                  <p><strong>{{ change.title }}</strong>{{ change.detail }}</p>
                </li>
              </ul>
            </div>
          </div>
        </section>

        <template v-if="workspace.activeFlow && selectedNode">
          <div class="panel-heading">
            <span class="section-kicker">Inspector</span>
            <h2>{{ selectedNode.title }}</h2>
            <p>{{ selectedNode.description }}</p>
          </div>

          <div class="node-status-card" :class="selectedNodeState">
            <span>{{ nodeStateLabel(selectedNodeState) }}</span>
            <strong>{{ nodeStateTitle(selectedNodeState) }}</strong>
            <p>{{ nodeStateDescription(selectedNode) }}</p>
          </div>

          <div v-if="selectedNode.type === 'prompt'" class="flow-node-order-actions">
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedNodeUp || workspace.flowLoading"
              @click="moveSelectedPromptNode('up')"
            >
              上移
            </button>
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedNodeDown || workspace.flowLoading"
              @click="moveSelectedPromptNode('down')"
            >
              下移
            </button>
            <button
              type="button"
              class="ghost-button"
              :disabled="workspace.flowLoading"
              @click="duplicateSelectedPromptNode"
            >
              复制
            </button>
          </div>

          <div class="flow-node-editor">
            <label>
              <span>Node title</span>
              <input v-model="nodeTitle" class="quiet-input" placeholder="节点标题" />
            </label>
            <label>
              <span>Description</span>
              <textarea v-model="nodeDescription" class="quiet-textarea" placeholder="节点说明"></textarea>
            </label>
            <label v-if="nodeCanEditContent">
              <span>{{ selectedNode.type === 'prompt' ? 'Prompt content' : 'Input content' }}</span>
              <textarea
                v-model="nodeContent"
                class="quiet-textarea flow-node-content-editor"
                placeholder="定义这个节点在 Flow 中提供的上下文..."
              ></textarea>
            </label>
            <div class="flow-node-editor-actions">
              <button
                type="button"
                class="secondary-button"
                :disabled="!nodeEditorChanged || workspace.flowLoading"
                @click="saveSelectedNode"
              >
                保存节点
              </button>
              <button
                v-if="nodeCanSaveAsPrompt"
                type="button"
                class="ghost-button"
                :disabled="savingNodePrompt || workspace.flowLoading"
                @click="saveSelectedNodeAsPrompt"
              >
                {{ savingNodePrompt ? '保存中...' : '沉淀为 Prompt' }}
              </button>
              <button
                v-if="nodeCanSendToTask"
                type="button"
                class="ghost-button"
                :disabled="workspace.running"
                @click="sendSelectedNodeToTaskWorkspace"
              >
                带入 Task
              </button>
            </div>
          </div>

          <button
            v-if="selectedNode.type === 'prompt'"
            type="button"
            class="ghost-button"
            @click="removeSelectedNode"
          >
            移除此 Prompt 节点
          </button>
        </template>

        <div v-else class="panel-heading">
          <span class="section-kicker">Inspector</span>
          <h2>选择一个节点</h2>
          <p>查看节点上下文，或从 Prompt Library 添加可复用工作方式。</p>
        </div>

        <div class="prompt-node-picker">
          <div class="section-heading compact">
            <h3>加入 Prompt</h3>
            <span>{{ filteredPromptOptions.length ? `${filteredPromptOptions.length} 个匹配` : '暂无匹配' }}</span>
          </div>

          <input
            v-model="promptSearch"
            class="quiet-input prompt-node-search"
            placeholder="搜索 Prompt、分类或标签..."
          />

          <div v-if="promptFilterChips.length" class="prompt-node-filters">
            <button
              v-for="filter in promptFilterChips"
              :key="filter.value"
              type="button"
              class="prompt-filter-chip"
              :class="{ active: activePromptFilter === filter.value }"
              @click="activePromptFilter = filter.value"
            >
              {{ filter.label }}
            </button>
          </div>

          <button
            v-for="prompt in visiblePromptOptions"
            :key="prompt.id"
            type="button"
            class="prompt-node-option"
            :class="{ 'in-flow': promptAlreadyInFlow(prompt) }"
            :disabled="!workspace.activeFlow || workspace.flowLoading || promptAlreadyInFlow(prompt)"
            @click="addPromptNode(prompt)"
          >
            <div class="prompt-node-option-meta">
              <span class="badge">{{ prompt.category }}</span>
              <span v-if="promptAlreadyInFlow(prompt)" class="prompt-node-status">已在 Flow 中</span>
            </div>
            <strong>{{ prompt.title }}</strong>
            <small>{{ prompt.description }}</small>
          </button>

          <div v-if="!prompts.length" class="prompt-node-empty">
            <strong>先沉淀一个 Prompt 资产</strong>
            <p>把稳定的工作方式保存到 Prompt Library，再作为 Flow 节点复用。</p>
            <button type="button" class="secondary-button" @click="goToPromptLibrary">打开 Prompt Library</button>
          </div>
          <div v-else-if="!filteredPromptOptions.length" class="prompt-node-empty">
            <strong>没有匹配的 Prompt</strong>
            <p>换一个关键词，或去 Library 创建一个更贴近当前 Flow 的 Prompt。</p>
            <button type="button" class="secondary-button" @click="goToPromptLibrary">创建 Prompt</button>
          </div>
        </div>

        <div v-if="workspace.activeFlow" class="flow-run-section">
          <div class="section-heading compact">
            <h3>最近执行</h3>
            <span>{{ flowRuns.length ? `${flowRuns.length} 条记录` : '暂无记录' }}</span>
          </div>
          <div v-if="flowRunsLoading" class="run-timeline">
            <article v-for="item in 2" :key="item" class="run-item skeleton-run"></article>
          </div>
          <div v-else-if="flowRuns.length" class="run-timeline">
            <button
              v-for="run in flowRuns"
              :key="run.id"
              type="button"
              class="run-item"
              :class="{ active: selectedFlowRun?.id === run.id }"
              @click="selectFlowRun(run)"
            >
              <time>{{ formatDate(run.createdAt) }}</time>
              <div class="run-item-heading">
                <strong>{{ run.summary }}</strong>
                <span v-if="run.flowRunSnapshot">已固定快照</span>
              </div>
              <p>{{ run.result }}</p>
            </button>
          </div>
          <p v-else class="quiet-note">
            从这个 Flow 发送到 Task 并执行后，记录会回到这里。
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { listFlowRuns, listFlowVersions, restoreFlowVersion } from '@/api/flows'
import { createPrompt, listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import { compareFlowRevision } from '@/utils/flowRevisions'
import { extractPromptVariables } from '@/utils/promptVariables'
import type { FlowNode, FlowNodeType, FlowVersion, PromptAsset, SavePromptPayload, TaskHistoryItem, TaskRunResponse } from '@/types'

type FlowNodeRunState = 'idle' | 'queued' | 'running' | 'completed' | 'error'
type FlowRunPhase = 'idle' | 'running' | 'completed' | 'error'
type FlowTemplate = {
  category: string
  title: string
  description: string
  intent: string
  nodes: Array<Pick<FlowNode, 'title' | 'description' | 'content'>>
}

const router = useRouter()
const workspace = useWorkspaceStore()

const flowIntent = ref('')
const selectedFlowTemplate = ref('')
const flowTitle = ref('')
const flowDescription = ref('')
const flowRunContext = ref('')
const flowVariableValues = ref<Record<string, string>>({})
const nodeTitle = ref('')
const nodeDescription = ref('')
const nodeContent = ref('')
const prompts = ref<PromptAsset[]>([])
const promptSearch = ref('')
const activePromptFilter = ref('all')
const flowRuns = ref<TaskHistoryItem[]>([])
const flowRunsLoading = ref(false)
const selectedFlowRun = ref<TaskHistoryItem | null>(null)
const flowVersions = ref<FlowVersion[]>([])
const flowVersionsLoading = ref(false)
const selectedFlowVersion = ref<FlowVersion | null>(null)
const restoringFlowVersion = ref(false)
const flowExecutionVisible = ref(false)
const savingResultPrompt = ref(false)
const savingNodePrompt = ref(false)
const savedResultPrompt = ref<PromptAsset | null>(null)
const flowRunPhase = ref<FlowRunPhase>('idle')
const flowRunStartedAt = ref('')
const flowRunCompletedAt = ref('')
const nodeRunStates = ref<Record<string, FlowNodeRunState>>({})
const selectedNodeId = ref('')
let flowProgressTimers: number[] = []

const flowTemplates: FlowTemplate[] = [
  {
    category: 'Product',
    title: 'Idea to MVP',
    description: '把一个模糊产品想法拆成定位、MVP 边界、风险和任务。',
    intent:
      '把一个产品想法依次拆解为：一句话定位、目标用户、核心问题、MVP 功能边界、不做什么、主要风险、下一步执行任务。',
    nodes: [
      {
        title: 'Clarify Product Shape',
        description: '把模糊想法整理为产品定位、目标用户和核心问题。',
        content:
          '你是一位资深产品负责人。请先明确这个产品想法的用户、问题和定位。\n\n请输出：\n1. 一句话定位\n2. 目标用户\n3. 用户当前痛点\n4. 现有替代方案\n5. Flow 需要优先解决的问题'
      },
      {
        title: 'Define MVP Boundary',
        description: '收敛 MVP 范围、优先级和不做事项。',
        content:
          '请基于产品定位继续收敛 MVP。\n\n请输出：\n1. 必须做的核心功能\n2. 可以延后的功能\n3. 明确不做什么\n4. 第一版验收标准\n5. 主要风险和验证方式'
      }
    ]
  },
  {
    category: 'Engineering',
    title: 'Spec to API',
    description: '把业务目标转成接口方案、数据边界和测试建议。',
    intent:
      '把一个业务目标依次拆解为：核心资源、REST API 草案、请求响应结构、错误码、边界条件、后端分层建议和测试用例。',
    nodes: [
      {
        title: 'Model API Resources',
        description: '识别业务目标里的核心资源和资源关系。',
        content:
          '你是一位 Staff Backend Engineer。请先从业务目标中识别 API 资源模型。\n\n请输出：\n1. 核心资源\n2. 资源之间的关系\n3. 关键字段\n4. 状态流转\n5. 需要避免的过度设计'
      },
      {
        title: 'Draft REST Contract',
        description: '生成 REST API、请求响应、错误码和测试边界。',
        content:
          '请基于资源模型设计 REST API 契约。\n\n请输出：\n1. Endpoint 列表\n2. 请求 JSON 示例\n3. 响应 JSON 示例\n4. 错误码\n5. 边界条件\n6. 必须覆盖的测试用例'
      }
    ]
  },
  {
    category: 'Research',
    title: 'Research Brief',
    description: '把研究主题整理成问题框架、信息缺口和行动路径。',
    intent:
      '把一个研究主题依次整理为：研究目标、关键问题、已知事实、核心假设、信息缺口、调研路径和一周内可执行计划。',
    nodes: [
      {
        title: 'Frame Research Questions',
        description: '把研究主题拆成关键问题、假设和信息缺口。',
        content:
          '你是一位产品研究员。请把研究主题整理成可执行的研究框架。\n\n请输出：\n1. 研究目标\n2. 关键问题\n3. 已知事实\n4. 核心假设\n5. 信息缺口'
      },
      {
        title: 'Plan Research Actions',
        description: '把研究框架转化为调研路径和一周行动计划。',
        content:
          '请基于研究框架制定调研行动计划。\n\n请输出：\n1. 调研路径\n2. 资料收集清单\n3. 访谈或验证对象\n4. 一周内行动计划\n5. 最终交付物结构'
      }
    ]
  },
  {
    category: 'Operations',
    title: 'Meeting to Actions',
    description: '把会议记录沉淀成决策、待确认事项和可执行的行动计划。',
    intent:
      '把一次会议记录或讨论要点依次整理为：会议目标、达成共识、关键决策、待确认问题、行动清单（含负责人和截止时间建议）以及对外同步摘要。',
    nodes: [
      {
        title: 'Distill Decisions',
        description: '从原始讨论中识别会议目标、共识、决策和未决问题。',
        content:
          '你是一位高效的项目协作负责人。请从会议记录中提炼真正影响后续工作的内容。\n\n请输出：\n1. 会议目标\n2. 已达成的共识\n3. 已确认的关键决策\n4. 仍待确认的问题\n5. 容易被遗漏的风险或依赖项'
      },
      {
        title: 'Turn Decisions into Actions',
        description: '把决策转化为有优先级、负责人建议和时间边界的行动。',
        content:
          '请基于会议决策整理一份可直接执行的行动计划。\n\n请输出：\n1. 行动项（按优先级排序）\n2. 每项的建议负责人角色\n3. 截止时间建议或前置条件\n4. 下次同步前需要产出的内容\n5. 一段可发送给团队的简洁同步摘要'
      }
    ]
  }
]

const selectedNode = computed<FlowNode | null>(() => {
  if (!workspace.activeFlow) {
    return null
  }
  return workspace.activeFlow.nodes.find((node) => node.id === selectedNodeId.value) || workspace.activeFlow.nodes[0] || null
})

const flowMetaChanged = computed(() => {
  if (!workspace.activeFlow) {
    return false
  }
  return flowTitle.value.trim() !== workspace.activeFlow.title || flowDescription.value.trim() !== workspace.activeFlow.description
})

const selectedFlowVersionDiff = computed(() => {
  if (!workspace.activeFlow || !selectedFlowVersion.value) {
    return null
  }
  return compareFlowRevision(workspace.activeFlow, selectedFlowVersion.value)
})

const flowPromptVariables = computed(() => {
  const variables = (workspace.activeFlow?.nodes || [])
    .filter((node) => node.type === 'prompt')
    .flatMap((node) => extractPromptVariables(node.content || ''))

  return Array.from(new Set(variables))
})

const flowRunInputPreview = computed(() => workspace.composeActiveFlowInput(flowRunContext.value, flowVariableValues.value))
const flowReadyToRun = computed(() => Boolean(workspace.activeProvider))
const activeProviderLabel = computed(() => workspace.activeProvider?.model || 'Provider 未配置')
const flowBriefItems = computed(() => {
  const nodes = workspace.activeFlow?.nodes || []
  const promptCount = nodes.filter((node) => node.type === 'prompt').length
  const outputNode = nodes.find((node) => node.type === 'output')

  return [
    { label: 'Flow steps', value: `${nodes.length} 个节点` },
    { label: 'Prompt assets', value: promptCount ? `${promptCount} 个 Prompt` : '等待加入' },
    { label: 'Output', value: outputNode?.title || 'Structured Result' }
  ]
})

const selectedFlowTemplateDetail = computed(() => {
  return selectedFlowTemplate.value ? flowTemplates.find((item) => item.title === selectedFlowTemplate.value) || null : null
})

const activeFlowResult = computed<TaskRunResponse | null>(() => {
  if (selectedFlowRun.value) {
    return {
      summary: selectedFlowRun.value.summary,
      result: selectedFlowRun.value.result,
      raw: '',
      taskId: selectedFlowRun.value.id,
      flowRunSnapshot: selectedFlowRun.value.flowRunSnapshot || null
    }
  }

  return workspace.latestResult
})

const activeFlowRunSnapshot = computed(() => activeFlowResult.value?.flowRunSnapshot || null)

const flowResultHeading = computed(() => {
  return selectedFlowRun.value ? '基于历史结果继续推进' : '基于这次结果继续推进'
})

const selectedNodeState = computed<FlowNodeRunState>(() => {
  if (!selectedNode.value) {
    return 'idle'
  }
  return nodeStatus(selectedNode.value.id)
})

const nodeCanEditContent = computed(() => {
  return selectedNode.value?.type === 'input' || selectedNode.value?.type === 'prompt'
})

const nodeCanSaveAsPrompt = computed(() => {
  return Boolean(
    workspace.activeFlow &&
      selectedNode.value &&
      nodeCanEditContent.value &&
      nodeTitle.value.trim() &&
      nodeDescription.value.trim() &&
      nodeContent.value.trim()
  )
})

const nodeCanSendToTask = computed(() => {
  return Boolean(selectedNode.value && nodeCanEditContent.value && nodeContent.value.trim())
})

const nodeEditorChanged = computed(() => {
  if (!selectedNode.value) {
    return false
  }
  const contentChanged = nodeCanEditContent.value && nodeContent.value.trim() !== (selectedNode.value.content || '')
  return (
    nodeTitle.value.trim() !== selectedNode.value.title ||
    nodeDescription.value.trim() !== selectedNode.value.description ||
    contentChanged
  )
})

const selectedPromptIndex = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return -1
  }
  return promptNodes.value.findIndex((node) => node.id === selectedNode.value?.id)
})

const promptNodes = computed(() => workspace.activeFlow?.nodes.filter((node) => node.type === 'prompt') || [])
const activeFlowPromptIds = computed(() => new Set(promptNodes.value.map((node) => node.promptId).filter(Boolean)))
const canMoveSelectedNodeUp = computed(() => selectedPromptIndex.value > 0)
const canMoveSelectedNodeDown = computed(() => {
  return selectedPromptIndex.value >= 0 && selectedPromptIndex.value < promptNodes.value.length - 1
})

const promptCategories = computed(() => {
  return Array.from(new Set(prompts.value.map((prompt) => prompt.category).filter(Boolean))).slice(0, 5)
})

const promptFilterChips = computed(() => [
  { label: '全部', value: 'all' },
  { label: '收藏', value: 'favorite' },
  ...promptCategories.value.map((category) => ({ label: category, value: `category:${category}` }))
])

const filteredPromptOptions = computed(() => {
  const keyword = promptSearch.value.trim().toLowerCase()
  const filteredByCategory = prompts.value.filter((prompt) => {
    if (activePromptFilter.value === 'favorite') {
      return prompt.favorite
    }

    if (activePromptFilter.value.startsWith('category:')) {
      return prompt.category === activePromptFilter.value.replace('category:', '')
    }

    return true
  })

  if (!keyword) {
    return filteredByCategory
  }

  return filteredByCategory.filter((prompt) =>
    [prompt.title, prompt.category, prompt.description, prompt.content, ...prompt.tags]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
})

const visiblePromptOptions = computed(() => filteredPromptOptions.value.slice(0, 8))

function promptAlreadyInFlow(prompt: PromptAsset) {
  return activeFlowPromptIds.value.has(prompt.id)
}

const flowRunTitle = computed(() => {
  const labels: Record<FlowRunPhase, string> = {
    idle: 'Flow Ready',
    running: 'Flow 正在执行',
    completed: 'Flow 执行完成',
    error: 'Flow 执行失败'
  }
  return labels[flowRunPhase.value]
})

const flowRunDescription = computed(() => {
  if (flowRunPhase.value === 'running') {
    const currentNode = workspace.activeFlow?.nodes.find((node) => nodeStatus(node.id) === 'running')
    const startedAt = flowRunStartedAt.value ? `，开始于 ${formatDate(flowRunStartedAt.value)}` : ''
    return currentNode ? `正在处理 ${currentNode.title}${startedAt}` : `正在连接 Prompt、AI 执行与结构化输出${startedAt}。`
  }

  if (flowRunPhase.value === 'completed') {
    return flowRunCompletedAt.value ? `完成于 ${formatDate(flowRunCompletedAt.value)}，结果已沉淀到当前画布。` : '结果已沉淀到当前画布。'
  }

  if (flowRunPhase.value === 'error') {
    return '执行没有完成，请检查当前 Provider 或稍后重试。'
  }

  return 'Flow 已准备好执行。'
})

watch(
  () => workspace.activeFlow?.id,
  () => {
    resetFlowRunState()
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
    flowTitle.value = workspace.activeFlow?.title || ''
    flowDescription.value = workspace.activeFlow?.description || ''
    flowRunContext.value = ''
    flowVariableValues.value = buildFlowVariableValues(flowPromptVariables.value)
    flowRuns.value = []
    flowVersions.value = []
    flowExecutionVisible.value = false
    selectedFlowRun.value = null
    selectedFlowVersion.value = null
    savedResultPrompt.value = null
    if (workspace.activeFlow?.id) {
      loadFlowRuns(workspace.activeFlow.id)
      loadFlowVersions(workspace.activeFlow.id)
    }
  },
  { immediate: true }
)

watch(
  () => workspace.activeFlow?.updatedAt,
  (updatedAt, previousUpdatedAt) => {
    const flowId = workspace.activeFlow?.id
    if (flowId && updatedAt && updatedAt !== previousUpdatedAt) {
      loadFlowVersions(flowId)
    }
  }
)

watch(flowPromptVariables, (variables) => {
  flowVariableValues.value = buildFlowVariableValues(variables, flowVariableValues.value)
})

watch(
  () => selectedNode.value?.id,
  () => syncSelectedNodeEditor(),
  { immediate: true }
)

onMounted(async () => {
  await Promise.all([workspace.loadFlowDrafts(), workspace.loadApiKeys(), loadPromptAssets()])
})

onBeforeUnmount(() => {
  clearFlowProgressTimers()
})

async function loadPromptAssets() {
  try {
    const { data } = await listPrompts()
    prompts.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt Library 加载失败')
  }
}

async function loadFlowRuns(flowId: string) {
  flowRunsLoading.value = true
  try {
    const { data } = await listFlowRuns(flowId)
    if (workspace.activeFlow?.id === flowId) {
      flowRuns.value = data
      if (selectedFlowRun.value && !data.some((run) => run.id === selectedFlowRun.value?.id)) {
        selectedFlowRun.value = null
      }
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Flow 执行记录加载失败')
  } finally {
    flowRunsLoading.value = false
  }
}

async function loadFlowVersions(flowId: string) {
  flowVersionsLoading.value = true
  try {
    const { data } = await listFlowVersions(flowId)
    if (workspace.activeFlow?.id !== flowId) {
      return
    }
    flowVersions.value = data
    if (selectedFlowVersion.value && !data.some((version) => version.id === selectedFlowVersion.value?.id)) {
      selectedFlowVersion.value = null
    }
  } catch (error: any) {
    if (workspace.activeFlow?.id === flowId) {
      ElMessage.error(error.response?.data?.message || 'Flow 修订记录加载失败')
    }
  } finally {
    if (workspace.activeFlow?.id === flowId) {
      flowVersionsLoading.value = false
    }
  }
}

async function restoreFlowVersionSnapshot(version: FlowVersion) {
  const flow = workspace.activeFlow
  if (!flow) {
    return
  }

  restoringFlowVersion.value = true
  try {
    const { data } = await restoreFlowVersion(flow.id, version.id)
    workspace.replaceFlowDraft(data)
    flowTitle.value = data.title
    flowDescription.value = data.description
    selectedNodeId.value = data.nodes[0]?.id || ''
    syncSelectedNodeEditor()
    flowRunContext.value = ''
    flowVariableValues.value = buildFlowVariableValues(flowPromptVariables.value)
    selectedFlowRun.value = null
    flowExecutionVisible.value = false
    savedResultPrompt.value = null
    selectedFlowVersion.value = null
    resetFlowRunState()
    await loadFlowVersions(data.id)
    ElMessage.success('Flow 已恢复到选中修订')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Flow 修订恢复失败')
  } finally {
    restoringFlowVersion.value = false
  }
}

async function createFlow() {
  const template = selectedFlowTemplate.value
    ? flowTemplates.find((item) => item.title === selectedFlowTemplate.value)
    : null
  const flow = template
    ? await workspace.createFlowFromTemplate(template.title, flowIntent.value, template.nodes)
    : await workspace.createFlowDraft(flowIntent.value)
  if (!flow) {
    return
  }
  flowIntent.value = ''
  selectedFlowTemplate.value = ''
  selectedNodeId.value = flow.nodes[0]?.id || ''
  ElMessage.success('Flow 草稿已创建')
}

async function duplicateActiveFlow() {
  const flow = await workspace.duplicateActiveFlowDraft()
  if (!flow) {
    return
  }
  selectedNodeId.value = flow.nodes[0]?.id || ''
  resetFlowRunState()
  ElMessage.success('Flow 变体已创建')
}

function selectFlow(id: string) {
  workspace.selectFlowDraft(id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
}

function useFlowTemplate(intent: string) {
  const template = flowTemplates.find((item) => item.intent === intent)
  selectedFlowTemplate.value = template?.title || ''
  flowIntent.value = intent
}

async function addPromptNode(prompt: PromptAsset) {
  const addedNode = await workspace.addPromptToActiveFlow(prompt)
  selectedNodeId.value = addedNode?.id || selectedNodeId.value
  resetFlowRunState()
  if (addedNode) {
    ElMessage.success('Prompt 已加入 Flow')
  }
}

async function removeSelectedNode() {
  if (!selectedNode.value) {
    return
  }
  await workspace.removeFlowNode(selectedNode.value.id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
  resetFlowRunState()
}

async function saveSelectedNode() {
  if (!selectedNode.value) {
    return
  }

  const updatedFlow = await workspace.updateFlowNode(selectedNode.value.id, {
    title: nodeTitle.value,
    description: nodeDescription.value,
    content: nodeCanEditContent.value ? nodeContent.value : selectedNode.value.content
  })

  if (!updatedFlow) {
    return
  }

  resetFlowRunState()
  syncSelectedNodeEditor()
  ElMessage.success('节点已保存')
}

async function moveSelectedPromptNode(direction: 'up' | 'down') {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return
  }

  const movedFlow = await workspace.moveFlowPromptNode(selectedNode.value.id, direction)
  if (!movedFlow) {
    return
  }

  selectedNodeId.value = selectedNode.value.id
  resetFlowRunState()
  ElMessage.success('Prompt 顺序已调整')
}

async function duplicateSelectedPromptNode() {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return
  }

  const duplicatedNode = await workspace.duplicateFlowPromptNode(selectedNode.value.id)
  if (!duplicatedNode) {
    return
  }

  selectedNodeId.value = duplicatedNode.id
  resetFlowRunState()
  syncSelectedNodeEditor()
  ElMessage.success('Prompt 变体已创建')
}

function selectFlowRun(run: TaskHistoryItem) {
  selectedFlowRun.value = run
  savedResultPrompt.value = null
  flowExecutionVisible.value = true
  flowRunPhase.value = 'completed'
  flowRunCompletedAt.value = run.createdAt
  if (workspace.activeFlow) {
    nodeRunStates.value = workspace.activeFlow.nodes.reduce<Record<string, FlowNodeRunState>>((states, node) => {
      states[node.id] = 'completed'
      return states
    }, {})
  }
}

function useLatestResultAsRunContext() {
  if (!activeFlowResult.value) {
    return
  }

  const continuationContext = [
    '上一轮 Flow 执行结果：',
    '',
    `Summary: ${activeFlowResult.value.summary}`,
    '',
    'Result:',
    activeFlowResult.value.result,
    '',
    '请基于以上结果继续迭代，保持输出结构清晰。'
  ].join('\n')

  flowRunContext.value = flowRunContext.value.trim()
    ? `${flowRunContext.value.trim()}\n\n---\n\n${continuationContext}`
    : continuationContext
  flowExecutionVisible.value = false
  resetFlowRunState()
  ElMessage.success('已带入 Run Brief')
}

async function saveLatestResultAsPrompt() {
  const prompt = await ensureLatestResultPrompt()
  if (prompt) {
    ElMessage.success('已保存到 Prompt Library')
  }
}

async function saveLatestResultAndAddToFlow() {
  const prompt = await ensureLatestResultPrompt()
  if (!prompt) {
    return
  }

  const addedNode = await workspace.addPromptToActiveFlow(prompt)
  if (!addedNode) {
    return
  }

  selectedNodeId.value = addedNode.id
  resetFlowRunState()
  syncSelectedNodeEditor()
  ElMessage.success('已作为 Prompt 节点加入 Flow')
}

async function saveSelectedNodeAsPrompt() {
  if (!workspace.activeFlow || !selectedNode.value || !nodeCanSaveAsPrompt.value) {
    return
  }

  const payload: SavePromptPayload = {
    title: `${nodeTitle.value.trim()} 资产`,
    category: selectedNode.value.type === 'prompt' ? 'Flow Prompt' : 'Flow Input',
    description: `从 Flow「${workspace.activeFlow.title}」的「${nodeTitle.value.trim()}」节点沉淀出的可复用工作方式。`,
    content: buildNodePromptAsset(),
    tags: ['Flow', nodeLabel(selectedNode.value.type), workspace.activeFlow.title],
    favorite: false
  }

  savingNodePrompt.value = true
  try {
    const { data } = await createPrompt(payload)
    prompts.value = [data, ...prompts.value.filter((prompt) => prompt.id !== data.id)]
    ElMessage.success('节点已沉淀到 Prompt Library')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 保存失败')
  } finally {
    savingNodePrompt.value = false
  }
}

async function ensureLatestResultPrompt() {
  if (!activeFlowResult.value || !workspace.activeFlow) {
    return null
  }

  if (savedResultPrompt.value) {
    return savedResultPrompt.value
  }

  const payload: SavePromptPayload = {
    title: `${workspace.activeFlow.title} 输出复用`,
    category: 'Flow Output',
    description: `从 Flow「${workspace.activeFlow.title}」执行结果沉淀出的可复用输出模式。`,
    content: buildResultPromptAsset(),
    tags: ['Flow', 'Result', 'Reusable'],
    favorite: false
  }

  savingResultPrompt.value = true
  try {
    const { data } = await createPrompt(payload)
    prompts.value = [data, ...prompts.value.filter((prompt) => prompt.id !== data.id)]
    savedResultPrompt.value = data
    return data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 保存失败')
    return null
  } finally {
    savingResultPrompt.value = false
  }
}

async function saveFlowMeta() {
  const updatedFlow = await workspace.updateFlowMeta(flowTitle.value, flowDescription.value)
  if (!updatedFlow) {
    return
  }
  flowTitle.value = updatedFlow.title
  flowDescription.value = updatedFlow.description
  resetFlowRunState()
  ElMessage.success('Flow 已保存')
}

async function confirmDeleteFlow() {
  if (!workspace.activeFlow) {
    return
  }

  try {
    await ElMessageBox.confirm(`删除「${workspace.activeFlow.title}」后无法继续编辑这个 Flow。`, '删除 Flow', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const deleted = await workspace.deleteFlowDraft(workspace.activeFlow.id)
    if (deleted) {
      flowTitle.value = workspace.activeFlow?.title || ''
      flowDescription.value = workspace.activeFlow?.description || ''
      selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
      ElMessage.success('Flow 已删除')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Flow 删除失败')
    }
  }
}

function sendFlowToTaskWorkspace() {
  workspace.sendFlowToTask(flowRunContext.value, flowVariableValues.value)
  router.push('/tasks')
}

function buildFlowVariableValues(variables: string[], currentValues: Record<string, string> = {}) {
  return Object.fromEntries(variables.map((variable) => [variable, currentValues[variable] || '']))
}

function sendSelectedNodeToTaskWorkspace() {
  if (!selectedNode.value || !nodeCanSendToTask.value) {
    return
  }

  const promptSource =
    selectedNode.value.type === 'prompt' && selectedNode.value.promptId
      ? { id: selectedNode.value.promptId, title: selectedNode.value.promptTitle || nodeTitle.value.trim() }
      : null

  workspace.prepareTask(buildNodeTaskInput(), promptSource)
  router.push('/tasks')
}

function goToApiKeys() {
  router.push('/api-keys')
}

function goToPromptLibrary() {
  router.push('/prompts')
}

async function executeFlowNow() {
  const flow = workspace.activeFlow
  const flowId = flow?.id
  if (!flow || !flowId) {
    return
  }

  if (!flowReadyToRun.value) {
    ElMessage.warning('请先配置并激活 AI Provider')
    goToApiKeys()
    return
  }

  savedResultPrompt.value = null
  selectedFlowRun.value = null
  startFlowRun(flow.nodes)
  const result = await workspace.executeActiveFlow(flowRunContext.value, flowVariableValues.value)
  if (result && flowId) {
    completeFlowRun()
    flowExecutionVisible.value = true
    await loadFlowRuns(flowId)
    return
  }

  failFlowRun()
}

function nodeLabel(type: FlowNodeType) {
  const labels: Record<FlowNodeType, string> = {
    input: 'Input',
    prompt: 'Prompt',
    'ai-task': 'AI Task',
    output: 'Output'
  }
  return labels[type]
}

function flowRevisionChangeLabel(kind: 'restore' | 'remove' | 'update' | 'reorder') {
  const labels = {
    restore: '加入',
    remove: '移除',
    update: '还原',
    reorder: '排序'
  }
  return labels[kind]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function nodeStatus(nodeId: string): FlowNodeRunState {
  return nodeRunStates.value[nodeId] || 'idle'
}

function startFlowRun(nodes: FlowNode[]) {
  clearFlowProgressTimers()
  flowRunPhase.value = 'running'
  flowRunStartedAt.value = new Date().toISOString()
  flowRunCompletedAt.value = ''
  flowExecutionVisible.value = false

  nodeRunStates.value = nodes.reduce<Record<string, FlowNodeRunState>>((states, node, index) => {
    states[node.id] = index === 0 ? 'running' : 'queued'
    return states
  }, {})

  nodes.slice(1).forEach((node, index) => {
    const timer = window.setTimeout(() => {
      if (flowRunPhase.value !== 'running') {
        return
      }
      const previousNode = nodes[index]
      nodeRunStates.value = {
        ...nodeRunStates.value,
        [previousNode.id]: 'completed',
        [node.id]: 'running'
      }
    }, (index + 1) * 700)
    flowProgressTimers.push(timer)
  })
}

function completeFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  clearFlowProgressTimers()
  flowRunPhase.value = 'completed'
  flowRunCompletedAt.value = new Date().toISOString()
  nodeRunStates.value = workspace.activeFlow.nodes.reduce<Record<string, FlowNodeRunState>>((states, node) => {
    states[node.id] = 'completed'
    return states
  }, {})
}

function failFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  clearFlowProgressTimers()
  flowRunPhase.value = 'error'
  const runningNode = workspace.activeFlow.nodes.find((node) => nodeStatus(node.id) === 'running')
  nodeRunStates.value = {
    ...nodeRunStates.value,
    ...(runningNode ? { [runningNode.id]: 'error' as FlowNodeRunState } : {})
  }
}

function resetFlowRunState() {
  clearFlowProgressTimers()
  flowRunPhase.value = 'idle'
  flowRunStartedAt.value = ''
  flowRunCompletedAt.value = ''
  nodeRunStates.value = {}
}

function clearFlowProgressTimers() {
  flowProgressTimers.forEach((timer) => window.clearTimeout(timer))
  flowProgressTimers = []
}

function connectorCompleted(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(currentNode && nextNode && nodeStatus(currentNode.id) === 'completed' && nodeStatus(nextNode.id) === 'completed')
}

function connectorRunning(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(currentNode && nextNode && nodeStatus(currentNode.id) === 'completed' && nodeStatus(nextNode.id) === 'running')
}

function nodeStateLabel(state: FlowNodeRunState) {
  const labels: Record<FlowNodeRunState, string> = {
    idle: 'Ready',
    queued: 'Queued',
    running: 'Running',
    completed: 'Done',
    error: 'Error'
  }
  return labels[state]
}

function nodeStateTitle(state: FlowNodeRunState) {
  const labels: Record<FlowNodeRunState, string> = {
    idle: '等待执行',
    queued: '排队等待',
    running: '正在处理',
    completed: '已完成',
    error: '需要检查'
  }
  return labels[state]
}

function nodeStateDescription(node: FlowNode) {
  const descriptions: Record<FlowNodeType, string> = {
    input: '读取 Flow 目标，作为本次执行的上下文起点。',
    prompt: '将可复用 Prompt 合并到本次 AI 工作流中。',
    'ai-task': '调用当前激活的 AI Provider 完成结构化任务。',
    output: '把 Summary、Key Points 与 Result 沉淀为可回看的执行结果。'
  }
  return descriptions[node.type]
}

function syncSelectedNodeEditor() {
  nodeTitle.value = selectedNode.value?.title || ''
  nodeDescription.value = selectedNode.value?.description || ''
  nodeContent.value = selectedNode.value?.content || ''
}

function buildResultPromptAsset() {
  if (!activeFlowResult.value || !workspace.activeFlow) {
    return ''
  }

  return [
    '你是一位 AI Workflow 设计助手。请参考下面这次已验证的 Flow 输出模式，生成同类高质量结果。',
    '',
    `Flow: ${workspace.activeFlow.title}`,
    `目标: ${workspace.activeFlow.description}`,
    '',
    '可替换输入：',
    '{input}',
    '',
    '参考 Summary:',
    activeFlowResult.value.summary,
    '',
    '参考 Result:',
    activeFlowResult.value.result,
    '',
    '请保持：',
    '1. 先给出清晰 Summary',
    '2. 再拆解关键要点',
    '3. 最后输出可执行的详细结果',
    '4. 不要照抄参考内容，要根据新输入重新生成'
  ].join('\n')
}

function buildNodePromptAsset() {
  if (!workspace.activeFlow || !selectedNode.value) {
    return ''
  }

  return [
    `# ${nodeTitle.value.trim()}`,
    '',
    `来源 Flow：${workspace.activeFlow.title}`,
    `节点类型：${nodeLabel(selectedNode.value.type)}`,
    '',
    '## 使用场景',
    nodeDescription.value.trim(),
    '',
    '## Prompt 内容',
    nodeContent.value.trim()
  ].join('\n')
}

function buildNodeTaskInput() {
  if (!workspace.activeFlow || !selectedNode.value) {
    return ''
  }

  return [
    `请基于 Flow「${workspace.activeFlow.title}」中的节点完成一次独立 AI 任务。`,
    '',
    `节点：${nodeTitle.value.trim()}`,
    `说明：${nodeDescription.value.trim()}`,
    '',
    '节点内容：',
    nodeContent.value.trim()
  ].join('\n')
}
</script>
