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
            <button
              type="button"
              class="ghost-button flow-add-context-button"
              :disabled="workspace.flowLoading || workspace.running"
              @click="addContextNode"
            >
              <el-icon><Plus /></el-icon>
              添加上下文
            </button>
            <button
              type="button"
              class="ghost-button"
              :disabled="workspace.running || hasIncompleteFlowNodes"
              @click="sendFlowToTaskWorkspace"
            >
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

        <div v-if="workspace.activeFlow && !providerReadyToRun" class="flow-readiness-note">
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
              :class="[
                node.type,
                `is-${nodeStatus(node.id)}`,
                { active: selectedNode?.id === node.id, 'is-incomplete': nodeNeedsContent(node) }
              ]"
              @click="selectFlowNode(node.id)"
            >
              <div class="flow-node-meta">
                <span class="flow-node-type">{{ nodeLabel(node.type) }}</span>
                <span class="flow-node-state">{{ nodeNeedsContent(node) ? 'Needs content' : nodeStateLabel(nodeStatus(node.id)) }}</span>
              </div>
              <strong>{{ node.title }}</strong>
              <p>{{ node.description }}</p>
            </button>
            <div
              v-if="index < workspace.activeFlow.nodes.length - 1"
              class="flow-connector"
              :class="{
                prepared: connectorPrepared(index),
                active: connectorCompleted(index),
                running: connectorRunning(index)
              }"
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

          <div v-if="hasIncompleteFlowNodes" class="flow-readiness-note flow-node-content-readiness">
            <span class="flow-run-dot warning"></span>
            <div>
              <strong>还有节点内容待完善</strong>
              <p>执行前请补充 {{ incompleteFlowNodeLabels }}，空节点不会被静默忽略。</p>
            </div>
            <button type="button" class="secondary-button" @click="selectFirstIncompleteNode">完善节点</button>
          </div>

          <div v-if="flowVariables.length" class="flow-variable-inputs" :class="{ 'has-missing': hasMissingFlowVariables }">
            <div class="flow-variable-heading">
              <div>
                <span class="section-kicker">Flow 变量</span>
                <p>为输入、Context、Prompt、执行指令或交付重点补充本次运行上下文。</p>
              </div>
              <span>{{ flowVariableStatusLabel }}</span>
            </div>

            <div class="flow-variable-grid">
              <div
                v-for="(variable, index) in flowVariables"
                :key="variable"
                class="flow-variable-field"
                :class="{ 'is-missing': !flowVariableValues[variable]?.trim() }"
              >
                <div class="flow-variable-name-row">
                  <label :for="`flow-variable-${index}`">{{ '{' + variable + '}' }}</label>
                  <button
                    type="button"
                    class="flow-variable-rename-button"
                    title="重命名变量"
                    :aria-label="`重命名变量 ${variable}`"
                    :disabled="workspace.flowLoading || workspace.running"
                    @click="renameFlowVariable(variable)"
                  >
                    <el-icon><EditPen /></el-icon>
                  </button>
                </div>
                <small>用于 {{ flowVariableUsageLabel(variable) }}</small>
                <textarea
                  :id="`flow-variable-${index}`"
                  v-model="flowVariableValues[variable]"
                  class="quiet-textarea"
                  :placeholder="`填写 ${variable}`"
                ></textarea>
              </div>
            </div>

            <div v-if="hasMissingFlowVariables" class="flow-variable-readiness">
              <span class="flow-run-dot warning"></span>
              <p>执行前请填写 {{ missingFlowVariableLabels }}。带入 Task 后也可继续补充。</p>
            </div>
          </div>

          <textarea
            v-model="flowRunContext"
            class="quiet-textarea flow-context-input"
            placeholder="为本次运行补充上下文，例如目标用户、输出格式、约束条件或业务背景..."
          ></textarea>

          <details class="flow-input-preview" @toggle="onFlowExecutionPreviewToggle">
            <summary>查看服务端执行输入</summary>
            <div v-if="flowExecutionPreviewLoading" class="flow-input-preview-status">
              正在按保存的 Flow 编译本次输入...
            </div>
            <div v-else-if="flowExecutionPreview" class="flow-input-preview-content">
              <div class="flow-input-preview-meta">
                <span>{{ flowExecutionPreviewStale ? 'Run Brief 已更新' : '与本次执行保持一致' }}</span>
                <button
                  v-if="flowExecutionPreviewStale"
                  type="button"
                  class="text-button"
                  @click="loadFlowExecutionPreview"
                >
                  刷新输入
                </button>
              </div>
              <pre>{{ flowExecutionPreview.executionInput }}</pre>
            </div>
            <div v-else class="flow-input-preview-status">
              <span>{{ flowExecutionPreviewError || (flowExecutionPreviewStale ? 'Run Brief 已更新，请刷新执行输入。' : '展开后将从服务端生成执行输入。') }}</span>
              <button
                v-if="flowExecutionPreviewError || flowExecutionPreviewStale"
                type="button"
                class="text-button"
                @click="loadFlowExecutionPreview"
              >
                {{ flowExecutionPreviewError ? '重试' : '刷新输入' }}
              </button>
            </div>
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
          <FlowRunSnapshot
            v-if="activeFlowRunSnapshot"
            :snapshot="activeFlowRunSnapshot"
            can-create-flow
            can-reuse-run-settings
            :creating="workspace.flowLoading"
            @create-flow="createFlowFromSnapshot"
            @reuse-run-settings="reuseFlowRunSettings"
          />
          <AiResultDocument
            class="flow-execution-result"
            :summary="activeFlowResult.summary"
            :result="activeFlowResult.result"
            :raw="activeFlowResult.raw"
            :provider="activeFlowResult.provider"
            :model="activeFlowResult.model"
            :input-tokens="activeFlowResult.inputTokens"
            :output-tokens="activeFlowResult.outputTokens"
            :total-tokens="activeFlowResult.totalTokens"
            :duration-ms="activeFlowResult.durationMs"
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
          <div class="editor-save-state" :class="{ dirty: flowMetaChanged }">
            <span></span>
            {{ flowMetaChanged ? 'Flow 目标尚未保存' : 'Flow 目标已保存' }}
          </div>
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

          <div class="node-status-card" :class="[selectedNodeState, { incomplete: selectedNodeIncomplete }]">
            <span>{{ selectedNodeIncomplete ? 'Needs content' : nodeStateLabel(selectedNodeState) }}</span>
            <strong>{{ selectedNodeIncomplete ? '补充节点内容' : nodeStateTitle(selectedNodeState, selectedNode) }}</strong>
            <p>
              {{ selectedNodeIncomplete ? '填写并保存节点内容后，它才会进入真实 Flow 执行。' : nodeStateDescription(selectedNode, selectedNodeState) }}
            </p>
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

          <div
            v-if="selectedNode.type === 'input' && selectedNode.id !== primaryInputNodeId"
            class="flow-node-order-actions is-context"
          >
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedContextUp || workspace.flowLoading"
              @click="moveSelectedContextNode('up')"
            >
              上移
            </button>
            <button
              type="button"
              class="ghost-button"
              :disabled="!canMoveSelectedContextDown || workspace.flowLoading"
              @click="moveSelectedContextNode('down')"
            >
              下移
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
              <span>{{ nodeContentLabel }}</span>
              <textarea
                v-model="nodeContent"
                class="quiet-textarea flow-node-content-editor"
                :placeholder="nodeContentPlaceholder"
              ></textarea>
            </label>
            <div class="editor-save-state" :class="{ dirty: nodeEditorChanged }">
              <span></span>
              {{ nodeEditorChanged ? '节点修改尚未保存' : '节点内容已保存' }}
            </div>
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
            v-if="canRemoveSelectedNode"
            type="button"
            class="ghost-button"
            @click="removeSelectedNode"
          >
            {{ selectedNode.type === 'prompt' ? '移除此 Prompt 节点' : '移除此上下文节点' }}
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
              :class="{ active: selectedFlowRun?.id === run.id, failed: run.status === 'failed' }"
              :disabled="run.status === 'failed'"
              @click="selectFlowRun(run)"
            >
              <time>{{ formatDate(run.createdAt) }}</time>
              <div class="run-item-heading">
                <strong>{{ run.summary }}</strong>
                <span v-if="run.status === 'failed'" class="error">执行失败</span>
                <span v-else-if="run.flowRunSnapshot">已固定快照</span>
              </div>
              <span v-if="formatExecutionSource(run.provider, run.model, run.totalTokens, run.durationMs)" class="run-provenance">
                {{ formatExecutionSource(run.provider, run.model, run.totalTokens, run.durationMs) }}
              </span>
              <p>{{ run.status === 'failed' ? run.errorMessage || run.result : run.result }}</p>
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
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Plus } from '@element-plus/icons-vue'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import { formatExecutionSource } from '@/utils/aiProvider'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { listFlowRuns, listFlowVersions, previewFlowExecution, restoreFlowVersion } from '@/api/flows'
import { createPrompt, listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import { compareFlowRevision } from '@/utils/flowRevisions'
import { extractPromptVariables, isValidPromptVariableName } from '@/utils/promptVariables'
import type {
  FlowNode,
  FlowNodeType,
  FlowExecutionPreviewResponse,
  FlowRunSnapshot as FlowRunSnapshotType,
  FlowVersion,
  PromptAsset,
  SavePromptPayload,
  TaskHistoryItem,
  TaskRunResponse
} from '@/types'

type FlowNodeRunState = 'idle' | 'prepared' | 'running' | 'completed' | 'error'
type FlowRunPhase = 'idle' | 'running' | 'completed' | 'error'
type FlowTemplate = {
  category: string
  title: string
  description: string
  intent: string
  nodes: Array<Pick<FlowNode, 'title' | 'description' | 'content'>>
}
type PendingNodeEditorPatch = {
  nodeId: string
  title?: string
  description?: string
  content?: string
}

const router = useRouter()
const workspace = useWorkspaceStore()

const flowIntent = ref('')
const selectedFlowTemplate = ref('')
const flowTitle = ref('')
const flowDescription = ref('')
const flowRunContext = ref('')
const flowVariableValues = ref<Record<string, string>>({})
const flowExecutionPreview = ref<FlowExecutionPreviewResponse | null>(null)
const flowExecutionPreviewLoading = ref(false)
const flowExecutionPreviewStale = ref(false)
const flowExecutionPreviewError = ref('')
const flowExecutionPreviewRequestVersion = ref(0)
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

const flowVariableNodeMap = computed<Record<string, FlowNode[]>>(() => {
  return (workspace.activeFlow?.nodes || []).reduce<Record<string, FlowNode[]>>((usageMap, node) => {
    extractPromptVariables(node.content || '').forEach((variable) => {
      usageMap[variable] = [...(usageMap[variable] || []), node]
    })
    return usageMap
  }, {})
})
const flowVariables = computed(() => Object.keys(flowVariableNodeMap.value))

const incompleteFlowNodes = computed(() =>
  (workspace.activeFlow?.nodes || []).filter((node) => nodeNeedsContent(node))
)
const hasIncompleteFlowNodes = computed(() => incompleteFlowNodes.value.length > 0)
const incompleteFlowNodeLabels = computed(() => incompleteFlowNodes.value.map((node) => `「${node.title}」`).join('、'))
const missingFlowVariables = computed(() =>
  flowVariables.value.filter((variable) => !flowVariableValues.value[variable]?.trim())
)
const hasMissingFlowVariables = computed(() => missingFlowVariables.value.length > 0)
const missingFlowVariableLabels = computed(() =>
  missingFlowVariables.value.map((variable) => `{${variable}}`).join('、')
)
const flowVariableStatusLabel = computed(() =>
  hasMissingFlowVariables.value ? `${missingFlowVariables.value.length} 项待填写` : `${flowVariables.value.length} 个变量已就绪`
)
const providerReadyToRun = computed(() => Boolean(workspace.activeProvider))
const flowReadyToRun = computed(() =>
  providerReadyToRun.value && !hasIncompleteFlowNodes.value && !hasMissingFlowVariables.value
)
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
      provider: selectedFlowRun.value.provider,
      model: selectedFlowRun.value.model,
      inputTokens: selectedFlowRun.value.inputTokens,
      outputTokens: selectedFlowRun.value.outputTokens,
      totalTokens: selectedFlowRun.value.totalTokens,
      durationMs: selectedFlowRun.value.durationMs,
      executionInput: selectedFlowRun.value.input,
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
const selectedNodeIncomplete = computed(() => Boolean(selectedNode.value && nodeNeedsContent(selectedNode.value)))

const primaryInputNodeId = computed(() => {
  return workspace.activeFlow?.nodes.find((node) => node.type === 'input')?.id || ''
})

const canRemoveSelectedNode = computed(() => {
  if (!selectedNode.value) {
    return false
  }
  return (
    selectedNode.value.type === 'prompt' ||
    (selectedNode.value.type === 'input' && selectedNode.value.id !== primaryInputNodeId.value)
  )
})

const nodeCanEditContent = computed(() => {
  return (
    selectedNode.value?.type === 'input' ||
    selectedNode.value?.type === 'prompt' ||
    selectedNode.value?.type === 'ai-task' ||
    selectedNode.value?.type === 'output'
  )
})

const nodeCanReuseContent = computed(() => {
  return selectedNode.value?.type === 'input' || selectedNode.value?.type === 'prompt'
})

const nodeContentLabel = computed(() => {
  if (selectedNode.value?.type === 'prompt') {
    return 'Prompt content'
  }
  if (selectedNode.value?.type === 'ai-task') {
    return 'Execution guidance'
  }
  if (selectedNode.value?.type === 'output') {
    return 'Delivery focus'
  }
  return 'Input content'
})

const nodeContentPlaceholder = computed(() => {
  if (selectedNode.value?.type === 'ai-task') {
    return '定义模型应如何组织、评估和交付本次结果...'
  }
  if (selectedNode.value?.type === 'output') {
    return '定义这次结果需要保留的表达重点、行动性和交付标准...'
  }
  return '定义这个节点在 Flow 中提供的上下文...'
})

const nodeCanSaveAsPrompt = computed(() => {
  return Boolean(
    workspace.activeFlow &&
      selectedNode.value &&
      nodeCanReuseContent.value &&
      nodeTitle.value.trim() &&
      nodeDescription.value.trim() &&
      nodeContent.value.trim()
  )
})

const nodeCanSendToTask = computed(() => {
  return Boolean(selectedNode.value && nodeCanReuseContent.value && nodeContent.value.trim())
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
const contextNodes = computed(() => workspace.activeFlow?.nodes.filter((node) => node.type === 'input').slice(1) || [])
const activeFlowPromptIds = computed(() => new Set(promptNodes.value.map((node) => node.promptId).filter(Boolean)))
const canMoveSelectedNodeUp = computed(() => selectedPromptIndex.value > 0)
const canMoveSelectedNodeDown = computed(() => {
  return selectedPromptIndex.value >= 0 && selectedPromptIndex.value < promptNodes.value.length - 1
})
const selectedContextIndex = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== 'input') {
    return -1
  }
  return contextNodes.value.findIndex((node) => node.id === selectedNode.value?.id)
})
const canMoveSelectedContextUp = computed(() => selectedContextIndex.value > 0)
const canMoveSelectedContextDown = computed(() => {
  return selectedContextIndex.value >= 0 && selectedContextIndex.value < contextNodes.value.length - 1
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
    const startedAt = flowRunStartedAt.value ? `，开始于 ${formatDate(flowRunStartedAt.value)}` : ''
    return `本次 Flow 上下文已固定，AI Task 正在调用当前 Provider 生成结构化结果${startedAt}。Input 和 Prompt 仅作为准备步骤。`
  }

  if (flowRunPhase.value === 'completed') {
    return flowRunCompletedAt.value
      ? `完成于 ${formatDate(flowRunCompletedAt.value)}。AI Task 已返回结果，Output 已沉淀为可复用记录。`
      : 'AI Task 已返回结果，Output 已沉淀为可复用记录。'
  }

  if (flowRunPhase.value === 'error') {
    return 'AI Task 未能完成，Output 未产生。请检查当前 Provider 配置或稍后重试。'
  }

  return 'Flow 已准备好执行。'
})

watch(
  () => workspace.activeFlow?.id,
  () => {
    const activeFlowId = workspace.activeFlow?.id
    const runSeed = activeFlowId ? workspace.consumeFlowRunSeed(activeFlowId) : null
    resetFlowExecutionPreview()
    resetFlowRunState()
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
    flowTitle.value = workspace.activeFlow?.title || ''
    flowDescription.value = workspace.activeFlow?.description || ''
    flowRunContext.value = runSeed?.runtimeContext || ''
    flowVariableValues.value = buildFlowVariableValues(flowVariables.value, runSeed?.variableValues)
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
      invalidateFlowExecutionPreview()
      loadFlowVersions(flowId)
    }
  }
)

watch(flowVariables, (variables) => {
  flowVariableValues.value = buildFlowVariableValues(variables, flowVariableValues.value)
})

watch([flowRunContext, flowVariableValues], () => {
  invalidateFlowExecutionPreview()
}, { deep: true })

watch([flowMetaChanged, nodeEditorChanged], ([hasFlowChanges, hasNodeChanges]) => {
  if (hasFlowChanges || hasNodeChanges) {
    invalidateFlowExecutionPreview()
  }
})

watch(
  () => selectedNode.value?.id,
  () => syncSelectedNodeEditor(),
  { immediate: true }
)

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  await Promise.all([workspace.loadFlowDrafts(), workspace.loadApiKeys(), loadPromptAssets()])
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(() => resolvePendingEdits())

async function loadPromptAssets() {
  try {
    const { data } = await listPrompts()
    prompts.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt Library 加载失败')
  }
}

function invalidateFlowExecutionPreview() {
  flowExecutionPreviewRequestVersion.value += 1
  if (flowExecutionPreview.value) {
    flowExecutionPreviewStale.value = true
  }
  if (flowExecutionPreviewLoading.value) {
    flowExecutionPreviewStale.value = true
  }
  flowExecutionPreviewError.value = ''
}

function resetFlowExecutionPreview() {
  flowExecutionPreviewRequestVersion.value += 1
  flowExecutionPreview.value = null
  flowExecutionPreviewLoading.value = false
  flowExecutionPreviewStale.value = false
  flowExecutionPreviewError.value = ''
}

function onFlowExecutionPreviewToggle(event: Event) {
  const details = event.currentTarget as HTMLDetailsElement
  if (details.open && (!flowExecutionPreview.value || flowExecutionPreviewStale.value)) {
    void loadFlowExecutionPreview()
  }
}

async function loadFlowExecutionPreview() {
  if (!workspace.activeFlow || flowExecutionPreviewLoading.value) {
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  const flow = workspace.activeFlow
  if (!flow) {
    return
  }

  const flowId = flow.id
  const requestVersion = flowExecutionPreviewRequestVersion.value
  flowExecutionPreviewLoading.value = true
  flowExecutionPreviewError.value = ''
  try {
    const { data } = await previewFlowExecution(flowId, {
      runtimeContext: flowRunContext.value,
      variableValues: flowVariableValues.value
    })
    if (workspace.activeFlow?.id === flowId && flowExecutionPreviewRequestVersion.value === requestVersion) {
      flowExecutionPreview.value = data
      flowExecutionPreviewStale.value = false
    }
  } catch (error: any) {
    if (workspace.activeFlow?.id === flowId && flowExecutionPreviewRequestVersion.value === requestVersion) {
      flowExecutionPreviewError.value = error.response?.data?.message || '执行输入生成失败'
    }
  } finally {
    flowExecutionPreviewLoading.value = false
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

function resetEditorsToSavedState() {
  flowTitle.value = workspace.activeFlow?.title || ''
  flowDescription.value = workspace.activeFlow?.description || ''
  syncSelectedNodeEditor()
}

function capturePendingNodeEditorPatch(): PendingNodeEditorPatch | null {
  const node = selectedNode.value
  if (!node || !nodeEditorChanged.value) {
    return null
  }

  const patch: PendingNodeEditorPatch = { nodeId: node.id }
  if (nodeTitle.value.trim() !== node.title) {
    patch.title = nodeTitle.value
  }
  if (nodeDescription.value.trim() !== node.description) {
    patch.description = nodeDescription.value
  }
  if (nodeCanEditContent.value && nodeContent.value.trim() !== (node.content || '')) {
    patch.content = nodeContent.value
  }
  return patch
}

function rebasePendingNodeEditorPatch(patch: PendingNodeEditorPatch) {
  const node = selectedNode.value
  if (!node || node.id !== patch.nodeId) {
    return false
  }

  nodeTitle.value = patch.title ?? node.title
  nodeDescription.value = patch.description ?? node.description
  nodeContent.value = patch.content ?? node.content ?? ''
  return true
}

async function persistPendingEdits() {
  const hadFlowChanges = flowMetaChanged.value
  const hadNodeChanges = nodeEditorChanged.value
  const pendingNodePatch = hadNodeChanges ? capturePendingNodeEditorPatch() : null
  if (hadFlowChanges && !(await persistFlowMeta(false, pendingNodePatch))) {
    return false
  }
  if (hadNodeChanges && !(await persistSelectedNode(false))) {
    return false
  }
  if (hadFlowChanges || hadNodeChanges) {
    ElMessage.success('未保存修改已保存')
  }
  return true
}

async function resolvePendingEdits() {
  if (!flowMetaChanged.value && !nodeEditorChanged.value) {
    return true
  }

  try {
    await ElMessageBox.confirm('当前 Flow 或节点还有未保存修改。', '未保存修改', {
      confirmButtonText: '保存并继续',
      cancelButtonText: '放弃修改',
      distinguishCancelAndClose: true,
      closeOnClickModal: false,
      type: 'warning'
    })
    return await persistPendingEdits()
  } catch (action) {
    if (action === 'cancel') {
      resetEditorsToSavedState()
      return true
    }
    return false
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!flowMetaChanged.value && !nodeEditorChanged.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

async function restoreFlowVersionSnapshot(version: FlowVersion) {
  if (!(await resolvePendingEdits())) {
    return
  }

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
    flowVariableValues.value = buildFlowVariableValues(flowVariables.value)
    invalidateFlowExecutionPreview()
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
  if (!(await resolvePendingEdits())) {
    return
  }

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
  if (!(await resolvePendingEdits())) {
    return
  }

  const flow = await workspace.duplicateActiveFlowDraft()
  if (!flow) {
    return
  }
  selectedNodeId.value = flow.nodes[0]?.id || ''
  resetFlowRunState()
  ElMessage.success('Flow 变体已创建')
}

async function createFlowFromSnapshot(snapshot: FlowRunSnapshotType) {
  if (!(await resolvePendingEdits())) {
    return
  }

  const flow = await workspace.createFlowFromRunSnapshot(snapshot)
  if (!flow) {
    return
  }

  selectedNodeId.value = flow.nodes[0]?.id || ''
  ElMessage.success('已创建新的 Flow，并带入本次运行上下文')
}

async function selectFlow(id: string) {
  if (id === workspace.activeFlowId || !(await resolvePendingEdits())) {
    return
  }

  workspace.selectFlowDraft(id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
}

async function selectFlowNode(nodeId: string) {
  if (nodeId === selectedNode.value?.id || !(await resolvePendingEdits())) {
    return
  }
  selectedNodeId.value = nodeId
}

function useFlowTemplate(intent: string) {
  const template = flowTemplates.find((item) => item.intent === intent)
  selectedFlowTemplate.value = template?.title || ''
  flowIntent.value = intent
}

async function addPromptNode(prompt: PromptAsset) {
  if (!(await resolvePendingEdits())) {
    return
  }

  const addedNode = await workspace.addPromptToActiveFlow(prompt)
  selectedNodeId.value = addedNode?.id || selectedNodeId.value
  resetFlowRunState()
  if (addedNode) {
    ElMessage.success('Prompt 已加入 Flow')
  }
}

async function addContextNode() {
  if (!(await resolvePendingEdits())) {
    return
  }

  const addedNode = await workspace.addContextToActiveFlow()
  if (!addedNode) {
    return
  }

  selectedNodeId.value = addedNode.id
  resetFlowRunState()
  ElMessage.success('上下文节点已加入 Flow')
}

async function removeSelectedNode() {
  if (!selectedNode.value || !canRemoveSelectedNode.value) {
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }
  await workspace.removeFlowNode(selectedNode.value.id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
  resetFlowRunState()
}

async function persistSelectedNode(notify: boolean) {
  if (!selectedNode.value) {
    return true
  }
  if (!nodeEditorChanged.value) {
    return true
  }

  const updatedFlow = await workspace.updateFlowNode(selectedNode.value.id, {
    title: nodeTitle.value,
    description: nodeDescription.value,
    content: nodeCanEditContent.value ? nodeContent.value : selectedNode.value.content
  })

  if (!updatedFlow) {
    return false
  }

  resetFlowRunState()
  syncSelectedNodeEditor()
  if (notify) {
    ElMessage.success('节点已保存')
  }
  return true
}

async function saveSelectedNode() {
  await persistSelectedNode(true)
}

async function renameFlowVariable(variable: string) {
  if (!(await resolvePendingEdits())) {
    return
  }

  try {
    const { value } = await ElMessageBox.prompt(
      `当前变量会同步更新 ${flowVariableNodeMap.value[variable]?.length || 0} 个节点。`,
      `重命名 {${variable}}`,
      {
        confirmButtonText: '重命名',
        cancelButtonText: '取消',
        inputValue: variable,
        inputPlaceholder: '输入新的变量名',
        inputValidator: (input) =>
          isValidPromptVariableName(input || '') || '仅支持中文、字母、数字、下划线和连字符'
      }
    )
    const nextVariable = value?.trim() || ''
    if (!nextVariable || nextVariable === variable) {
      return
    }
    if (flowVariables.value.includes(nextVariable)) {
      ElMessage.warning(`Flow 中已存在变量 {${nextVariable}}`)
      return
    }

    const currentValue = flowVariableValues.value[variable] || ''
    const renamedVariables = flowVariables.value.map((item) => (item === variable ? nextVariable : item))
    const updatedFlow = await workspace.renameFlowVariable(variable, nextVariable)
    if (!updatedFlow) {
      return
    }

    flowVariableValues.value = buildFlowVariableValues(renamedVariables, {
      ...flowVariableValues.value,
      [nextVariable]: currentValue
    })
    resetFlowRunState()
    ElMessage.success(`变量已重命名为 {${nextVariable}}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('变量重命名失败')
    }
  }
}

async function moveSelectedPromptNode(direction: 'up' | 'down') {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  const nodeId = selectedNode.value?.type === 'prompt' ? selectedNode.value.id : ''
  if (!nodeId) {
    return
  }

  const movedFlow = await workspace.moveFlowPromptNode(nodeId, direction)
  if (!movedFlow) {
    return
  }

  selectedNodeId.value = nodeId
  resetFlowRunState()
  ElMessage.success('Prompt 顺序已调整')
}

async function moveSelectedContextNode(direction: 'up' | 'down') {
  if (!selectedNode.value || selectedNode.value.type !== 'input') {
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  const nodeId = selectedNode.value?.type === 'input' ? selectedNode.value.id : ''
  if (!nodeId) {
    return
  }

  const movedFlow = await workspace.moveFlowContextNode(nodeId, direction)
  if (!movedFlow) {
    return
  }

  selectedNodeId.value = nodeId
  resetFlowRunState()
  ElMessage.success('上下文顺序已调整')
}

async function duplicateSelectedPromptNode() {
  if (!selectedNode.value || selectedNode.value.type !== 'prompt') {
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  const nodeId = selectedNode.value?.type === 'prompt' ? selectedNode.value.id : ''
  if (!nodeId) {
    return
  }

  const duplicatedNode = await workspace.duplicateFlowPromptNode(nodeId)
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
    nodeRunStates.value = buildNodeRunStates(workspace.activeFlow.nodes, 'completed')
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

function reuseFlowRunSettings(snapshot: FlowRunSnapshotType) {
  if (!workspace.activeFlow || snapshot.flowId !== workspace.activeFlow.id) {
    return
  }

  const snapshotVariableValues = snapshot.variableValues || {}
  const reusedVariableCount = flowVariables.value.filter((variable) => snapshotVariableValues[variable]?.trim()).length
  flowRunContext.value = snapshot.runtimeContext || ''
  flowVariableValues.value = buildFlowVariableValues(flowVariables.value, snapshotVariableValues)
  flowExecutionVisible.value = false
  selectedFlowRun.value = null
  resetFlowRunState()

  const variableMessage = reusedVariableCount ? `，并带入 ${reusedVariableCount} 个变量` : ''
  ElMessage.success(`已带入本次运行配置${variableMessage}`)
}

async function saveLatestResultAsPrompt() {
  const prompt = await ensureLatestResultPrompt()
  if (prompt) {
    ElMessage.success('已保存到 Prompt Library')
  }
}

async function saveLatestResultAndAddToFlow() {
  if (!(await resolvePendingEdits())) {
    return
  }

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
  if (!(await resolvePendingEdits())) {
    return
  }

  if (!workspace.activeFlow || !selectedNode.value || !nodeCanSaveAsPrompt.value) {
    return
  }

  const payload: SavePromptPayload = {
    title: `${nodeTitle.value.trim()} 资产`,
    category: selectedNode.value.type === 'prompt' ? 'Flow Prompt' : 'Flow Input',
    description: `从 Flow「${workspace.activeFlow.title}」的「${nodeTitle.value.trim()}」节点沉淀出的可复用工作方式。`,
    content: buildNodePromptAsset(),
    tags: ['Flow', nodeLabel(selectedNode.value.type), workspace.activeFlow.title],
    favorite: false,
    sourceFlowId: workspace.activeFlow.id,
    sourceNodeId: selectedNode.value.id
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
    favorite: false,
    sourceTaskId: activeFlowResult.value.taskId || null
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

async function persistFlowMeta(notify: boolean, pendingNodePatch: PendingNodeEditorPatch | null) {
  if (!flowMetaChanged.value) {
    return true
  }

  const updatedFlow = await workspace.updateFlowMeta(flowTitle.value, flowDescription.value)
  if (!updatedFlow) {
    return false
  }
  flowTitle.value = updatedFlow.title
  flowDescription.value = updatedFlow.description
  if (pendingNodePatch) {
    if (!rebasePendingNodeEditorPatch(pendingNodePatch)) {
      ElMessage.error('节点编辑状态已变化，请重新确认修改')
      return false
    }
  } else {
    syncSelectedNodeEditor()
  }
  resetFlowRunState()
  if (notify) {
    ElMessage.success('Flow 已保存')
  }
  return true
}

async function saveFlowMeta() {
  await persistFlowMeta(true, capturePendingNodeEditorPatch())
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

async function sendFlowToTaskWorkspace() {
  if (!(await resolvePendingEdits())) {
    return
  }

  if (hasIncompleteFlowNodes.value) {
    ElMessage.warning(`请先完善 Flow 节点：${incompleteFlowNodes.value.map((node) => node.title).join('、')}`)
    return
  }

  workspace.sendFlowToTask(flowRunContext.value, flowVariableValues.value)
  await router.push('/tasks')
}

async function selectFirstIncompleteNode() {
  if (!(await resolvePendingEdits())) {
    return
  }

  const firstIncompleteNode = incompleteFlowNodes.value[0]
  if (!firstIncompleteNode) {
    return
  }

  selectedNodeId.value = firstIncompleteNode.id
}

function buildFlowVariableValues(variables: string[], currentValues: Record<string, string> = {}) {
  return Object.fromEntries(variables.map((variable) => [variable, currentValues[variable] || '']))
}

async function sendSelectedNodeToTaskWorkspace() {
  if (!(await resolvePendingEdits())) {
    return
  }

  if (!selectedNode.value || !nodeCanSendToTask.value) {
    return
  }

  const promptSource =
    selectedNode.value.type === 'prompt' && selectedNode.value.promptId
      ? { id: selectedNode.value.promptId, title: selectedNode.value.promptTitle || nodeTitle.value.trim() }
      : null

  workspace.prepareTask(buildNodeTaskInput(), promptSource)
  await router.push('/tasks')
}

function goToApiKeys() {
  router.push('/api-keys')
}

function goToPromptLibrary() {
  router.push('/prompts')
}

async function executeFlowNow() {
  if (!workspace.activeFlow || !(await resolvePendingEdits())) {
    return
  }

  const flow = workspace.activeFlow
  const flowId = flow.id
  if (!flowId) {
    return
  }

  if (!providerReadyToRun.value) {
    ElMessage.warning('请先配置并激活 AI Provider')
    goToApiKeys()
    return
  }

  if (hasIncompleteFlowNodes.value) {
    ElMessage.warning(`请先完善 Flow 节点：${incompleteFlowNodes.value.map((node) => node.title).join('、')}`)
    await selectFirstIncompleteNode()
    return
  }

  if (hasMissingFlowVariables.value) {
    ElMessage.warning(`请先填写 Flow 变量：${missingFlowVariables.value.join('、')}`)
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

function flowVariableUsageLabel(variable: string) {
  return (flowVariableNodeMap.value[variable] || [])
    .map((node) => `${flowVariableNodeTypeLabel(node)}「${node.title}」`)
    .join('、')
}

function flowVariableNodeTypeLabel(node: FlowNode) {
  if (node.type === 'input' && node.id !== primaryInputNodeId.value) {
    return 'Context'
  }
  return nodeLabel(node.type)
}

function nodeNeedsContent(node: FlowNode) {
  return !node.content?.trim()
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
  flowRunPhase.value = 'running'
  flowRunStartedAt.value = new Date().toISOString()
  flowRunCompletedAt.value = ''
  flowExecutionVisible.value = false
  nodeRunStates.value = buildNodeRunStates(nodes, 'running')
}

function completeFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  flowRunPhase.value = 'completed'
  flowRunCompletedAt.value = new Date().toISOString()
  nodeRunStates.value = buildNodeRunStates(workspace.activeFlow.nodes, 'completed')
}

function failFlowRun() {
  if (!workspace.activeFlow) {
    return
  }
  flowRunPhase.value = 'error'
  nodeRunStates.value = buildNodeRunStates(workspace.activeFlow.nodes, 'error')
}

function resetFlowRunState() {
  flowRunPhase.value = 'idle'
  flowRunStartedAt.value = ''
  flowRunCompletedAt.value = ''
  nodeRunStates.value = {}
}

function buildNodeRunStates(nodes: FlowNode[], phase: Exclude<FlowRunPhase, 'idle'>) {
  return nodes.reduce<Record<string, FlowNodeRunState>>((states, node) => {
    if (node.type === 'input' || node.type === 'prompt') {
      states[node.id] = 'prepared'
      return states
    }

    if (node.type === 'ai-task') {
      states[node.id] = phase === 'running' ? 'running' : phase === 'completed' ? 'completed' : 'error'
      return states
    }

    states[node.id] = phase === 'completed' ? 'completed' : 'idle'
    return states
  }, {})
}

function connectorCompleted(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(
    currentNode &&
      nextNode &&
      currentNode.type === 'ai-task' &&
      nextNode.type === 'output' &&
      nodeStatus(currentNode.id) === 'completed' &&
      nodeStatus(nextNode.id) === 'completed'
  )
}

function connectorRunning(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  return Boolean(
    currentNode &&
      nextNode &&
      currentNode.type === 'ai-task' &&
      nextNode.type === 'output' &&
      nodeStatus(currentNode.id) === 'running' &&
      nodeStatus(nextNode.id) === 'idle'
  )
}

function connectorPrepared(index: number) {
  const nodes = workspace.activeFlow?.nodes || []
  const currentNode = nodes[index]
  const nextNode = nodes[index + 1]
  const nextNodeState = nextNode ? nodeStatus(nextNode.id) : 'idle'
  return Boolean(
    currentNode &&
      nextNode &&
      nodeStatus(currentNode.id) === 'prepared' &&
      ['prepared', 'running', 'completed'].includes(nextNodeState)
  )
}

function nodeStateLabel(state: FlowNodeRunState) {
  const labels: Record<FlowNodeRunState, string> = {
    idle: 'Ready',
    prepared: 'Prepared',
    running: 'Running',
    completed: 'Done',
    error: 'Error'
  }
  return labels[state]
}

function nodeStateTitle(state: FlowNodeRunState, node: FlowNode) {
  if (state === 'idle') {
    return node.type === 'output' ? '等待结果' : '等待运行'
  }

  const labels = {
    prepared: '已准备',
    running: '正在处理',
    completed: '已完成',
    error: '需要检查'
  }
  return labels[state]
}

function nodeStateDescription(node: FlowNode, state: FlowNodeRunState) {
  if (state === 'prepared') {
    return '已固定为本次运行的上下文，不会作为独立模型调用执行。'
  }

  if (state === 'running') {
    return '当前 Flow 正通过激活的 AI Provider 发起一次结构化任务调用。'
  }

  if (state === 'completed') {
    return node.type === 'output'
      ? '结构化结果已保存，可继续带入下一轮或沉淀为 Prompt。'
      : node.type === 'ai-task'
        ? 'AI Provider 已返回结构化结果，并已交给 Output 节点记录。'
        : '该节点已作为这次已完成运行的上下文快照保留。'
  }

  if (state === 'error') {
    return 'AI Task 未能完成，检查 Provider 配置后可以重新运行。'
  }

  if (node.type === 'output') {
    return '等待 AI Task 返回结构化结果后记录本次运行。'
  }

  const descriptions: Record<FlowNodeType, string> = {
    input: '读取 Flow 目标，作为本次执行的上下文起点。',
    prompt: '将可复用 Prompt 合并到本次 AI 工作流中。',
    'ai-task': '将上游上下文与已保存的执行指令交给当前 AI Provider。',
    output: '定义本次结果的交付重点，并记录可回看的结构化结果。'
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
