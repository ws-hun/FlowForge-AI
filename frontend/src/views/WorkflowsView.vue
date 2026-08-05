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

        <div class="flow-intent-editor">
          <textarea
            v-model="flowIntent"
            class="quiet-textarea flow-intent-input"
            placeholder="例如：把一个产品想法依次拆解成 PRD、接口草案和任务清单..."
          ></textarea>
          <div v-if="flowCreationDraftRecovered" class="editor-save-state dirty">
            <span></span>
            已恢复未创建的 Flow 想法
          </div>
        </div>

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
            @click="useFlowTemplate(template)"
          >
            <span>{{ template.category }}</span>
            <strong>{{ template.title }}</strong>
            <small>{{ template.description }}</small>
            <em>{{ template.nodes.length }} Prompt nodes</em>
          </button>

          <div v-if="selectedFlowTemplateDetail" class="flow-template-preview">
            <div class="flow-template-preview-heading">
              <span>Template Flow</span>
              <button type="button" class="text-button" @click="detachFlowTemplate">移除模板</button>
            </div>
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
        <div v-if="orphanedFlowEditorDraft" class="flow-conflict-note flow-draft-recovery-note orphaned">
          <span class="flow-run-dot warning"></span>
          <div>
            <strong>本地 Flow 草稿仍然可用</strong>
            <p>原 Flow 或编辑节点已不存在。完整快照仍保留在当前浏览器，可创建独立恢复副本继续工作。</p>
          </div>
          <div class="flow-recovery-actions">
            <button type="button" class="secondary-button" :disabled="workspace.flowLoading" @click="recoverOrphanedFlowEditorDraft">
              创建恢复副本
            </button>
            <button type="button" class="ghost-button" :disabled="workspace.flowLoading" @click="discardFlowEditorDraft">
              放弃草稿
            </button>
          </div>
        </div>

        <div
          v-else-if="flowEditorDraftRecovered && !flowConflictVisible"
          class="flow-conflict-note flow-draft-recovery-note"
        >
          <span class="flow-run-dot"></span>
          <div>
            <strong>已恢复未保存的 Flow 草稿</strong>
            <p>刷新前的 Flow 目标与节点编辑已回到原位置，可继续完善或保存。</p>
          </div>
          <button type="button" class="ghost-button" @click="discardFlowEditorDraft">放弃草稿</button>
        </div>

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

        <div v-if="flowConflictVisible" class="flow-conflict-note">
          <span class="flow-run-dot warning"></span>
          <div>
            <strong>已载入这个 Flow 的最新版本</strong>
            <p>另一窗口先完成了保存。本地编辑仍被保留，请核对后再次保存，或采用最新版本。</p>
          </div>
          <button type="button" class="ghost-button" @click="adoptLatestFlowAfterConflict">采用最新版本</button>
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

          <div class="flow-run-draft-state">
            <div
              class="editor-save-state"
              :class="{ idle: !hasFlowRunDraftContent, recovered: flowRunDraftRecovered }"
            >
              <span></span>
              {{ flowRunDraftStateLabel }}
            </div>
            <div v-if="hasFlowRunDraftContent" class="flow-run-draft-actions">
              <button
                v-if="flowRunContext.trim()"
                type="button"
                class="text-button"
                :disabled="workspace.flowLoading || workspace.running"
                @click="persistRunBriefAsContext"
              >
                <el-icon><Plus /></el-icon>
                固化到 Flow
              </button>
              <button type="button" class="text-button" @click="clearCurrentFlowRunDraft">
                清除 Run Brief
              </button>
            </div>
          </div>

          <FlowExecutionInputPreview
            :key="workspace.activeFlow.id"
            :flow-id="workspace.activeFlow.id"
            :runtime-context="flowRunContext"
            :variable-values="flowVariableValues"
            :source-version="workspace.activeFlow.updatedAt"
            :dirty="flowMetaChanged || nodeEditorChanged"
            :before-load="resolvePendingEdits"
            node-action-label="定位节点"
            variable-action-label="填写变量"
            @open-node="openExecutionPreviewNode"
            @focus-variable="focusExecutionPreviewVariable"
          />
        </section>

        <section v-if="workspace.activeFlow && flowExecutionVisible && activeFlowResult" class="flow-result-loop">
          <div class="flow-result-loop-actions">
            <div>
              <span class="section-kicker">Iteration</span>
              <strong>{{ flowResultHeading }}</strong>
            </div>
            <div class="flow-result-actions">
              <button
                v-if="activeFlowResult?.taskId"
                type="button"
                class="ghost-button"
                @click="openSelectedRunHistory"
              >
                在 History 打开
              </button>
              <button
                v-if="activeFlowResultFailed"
                type="button"
                class="secondary-button"
                :disabled="workspace.running"
                @click="rerunSelectedFlowRun"
              >
                {{ workspace.running ? '重跑中...' : '使用当前 Provider 重跑' }}
              </button>
              <template v-else>
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
              </template>
            </div>
          </div>
          <FlowRunTrace
            v-if="activeFlowResult.flowRunTrace"
            :trace="activeFlowResult.flowRunTrace"
            node-action-label="定位节点"
            :navigable-node-ids="workspace.activeFlow.nodes.map((node) => node.id)"
            @open-node="openExecutionPreviewNode"
          />
          <FlowRunSnapshot
            v-if="activeFlowRunSnapshot"
            :snapshot="activeFlowRunSnapshot"
            :current-flow="workspace.activeFlow"
            :current-run-settings="{ runtimeContext: flowRunContext, variableValues: flowVariableValues }"
            can-create-flow
            can-reuse-run-settings
            :can-open-source-flow="flowSourceStillAvailable(activeFlowRunSnapshot)"
            :creating="workspace.flowLoading"
            @create-flow="createFlowFromSnapshot"
            @reuse-run-settings="reuseFlowRunSettings"
            @open-source-flow="openFlowSnapshotSource"
          />
          <div v-if="activeFlowResultFailed" class="failed-run-detail flow-run-failure-detail">
            <span class="section-kicker">Execution Error</span>
            <strong>{{ selectedFlowRun?.errorMessage || activeFlowResult.result }}</strong>
            <p>节点准备状态与固定执行输入已保留。使用当前 Provider 重跑会创建一条新的可比较运行，不会覆盖这次失败记录。</p>
          </div>
          <AiResultDocument
            v-else
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
          <div v-if="workspace.activeFlow.sourceFlowId" class="flow-origin-strip">
            <div>
              <span class="section-kicker">Derived Flow</span>
              <strong>{{ workspace.activeFlow.sourceFlowTitle || '来源 Flow' }}</strong>
              <p>
                {{ workspace.activeFlow.sourceFlowVersionNumber
                  ? `从 v${workspace.activeFlow.sourceFlowVersionNumber} 修订创建的独立变体`
                  : '基于来源 Flow 创建的独立变体' }}
              </p>
            </div>
            <button type="button" class="ghost-button" @click="openActiveFlowSource">打开来源</button>
          </div>
          <input v-model="flowTitle" class="quiet-input" placeholder="Flow 标题" />
          <textarea v-model="flowDescription" class="quiet-textarea" placeholder="Flow 目标"></textarea>
          <div class="editor-save-state" :class="{ dirty: flowMetaChanged }">
            <span></span>
            {{ flowEditorDraftRecovered && flowMetaChanged
              ? '已恢复本地未保存的 Flow 目标'
              : flowMetaChanged
                ? 'Flow 目标尚未保存'
                : 'Flow 目标已保存' }}
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
              <div class="flow-version-actions">
                <button
                  type="button"
                  class="secondary-button"
                  :disabled="branchingFlowVersion || restoringFlowVersion || workspace.flowLoading"
                  @click="createFlowVariantFromVersion(selectedFlowVersion)"
                >
                  {{ branchingFlowVersion ? '创建中...' : '创建变体' }}
                </button>
                <button
                  type="button"
                  class="ghost-button"
                  :disabled="restoringFlowVersion || branchingFlowVersion || workspace.flowLoading || !selectedFlowVersionDiff?.hasChanges"
                  @click="restoreFlowVersionSnapshot(selectedFlowVersion)"
                >
                  {{ restoringFlowVersion ? '恢复中...' : '恢复此修订' }}
                </button>
              </div>
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
          <div ref="nodeInspector" class="panel-heading">
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

          <section v-if="selectedNode.type === 'prompt' && selectedNode.promptId" class="flow-node-prompt-source">
            <div>
              <span class="section-kicker">Linked Prompt</span>
              <strong>{{ selectedNodeSourcePrompt?.title || selectedNode.promptTitle || '来源 Prompt' }}</strong>
              <p>{{ selectedNodePromptSourceDescription }}</p>
            </div>
            <div class="flow-node-prompt-source-actions">
              <button
                type="button"
                class="ghost-button"
                :disabled="!selectedNodeSourcePrompt"
                @click="openSelectedNodePrompt"
              >
                打开 Prompt
              </button>
              <button
                v-if="selectedNodeSourcePrompt && !selectedNodePromptInSync"
                type="button"
                class="secondary-button"
                :disabled="workspace.flowLoading"
                @click="syncSelectedNodePrompt"
              >
                用 Library 版本替换
              </button>
            </div>
          </section>

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
              {{ flowEditorDraftRecovered && nodeEditorChanged
                ? '已恢复本地未保存的节点内容'
                : nodeEditorChanged
                  ? '节点修改尚未保存'
                  : '节点内容已保存' }}
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
              @click="selectFlowRun(run)"
            >
              <time>{{ formatDate(run.createdAt) }}</time>
              <div class="run-item-heading">
                <strong>{{ run.summary }}</strong>
                <span v-if="run.status === 'failed'" class="error">执行失败 · 可检查</span>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Plus } from '@element-plus/icons-vue'
import AiResultDocument from '@/components/ai/AiResultDocument.vue'
import FlowExecutionInputPreview from '@/components/flow/FlowExecutionInputPreview.vue'
import FlowRunTrace from '@/components/flow/FlowRunTrace.vue'
import { formatExecutionSource } from '@/utils/aiProvider'
import FlowRunSnapshot from '@/components/flow/FlowRunSnapshot.vue'
import { listFlowRuns, listFlowVersions } from '@/api/flows'
import { createPrompt, listPrompts } from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import { compareFlowRevision } from '@/utils/flowRevisions'
import { canPersistFlowContext, MAX_FLOW_CONTEXT_LENGTH } from '@/utils/flowContext'
import { shouldConfirmFlowRunSettingsReplacement } from '@/utils/flowRunSnapshots'
import { persistFlowCreationDraft, readFlowCreationDraft } from '@/utils/flowCreationDraft'
import {
  buildRecoveredFlowSnapshot,
  captureFlowEditorSnapshot,
  persistFlowEditorDraft,
  readFlowEditorDraft,
  removeFlowEditorDraft
} from '@/utils/flowEditorDraft'
import { extractPromptVariables, isValidPromptVariableName } from '@/utils/promptVariables'
import type { FlowEditorDraft } from '@/utils/flowEditorDraft'
import type {
  FlowNode,
  FlowNodeType,
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
const route = useRoute()
const workspace = useWorkspaceStore()

const initialFlowCreationDraft = readFlowCreationDraft()
const flowIntent = ref(initialFlowCreationDraft?.intent || '')
const selectedFlowTemplate = ref(initialFlowCreationDraft?.templateTitle || '')
const flowCreationDraftRecovered = ref(Boolean(initialFlowCreationDraft))
const flowTitle = ref('')
const flowDescription = ref('')
const flowRunContext = ref('')
const flowVariableValues = ref<Record<string, string>>({})
const flowRunDraftRecovered = ref(false)
const flowRunDraftHydrating = ref(false)
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
const branchingFlowVersion = ref(false)
const flowExecutionVisible = ref(false)
const savingResultPrompt = ref(false)
const savingNodePrompt = ref(false)
const savedResultPrompt = ref<PromptAsset | null>(null)
const flowRunPhase = ref<FlowRunPhase>('idle')
const flowRunStartedAt = ref('')
const flowRunCompletedAt = ref('')
const nodeRunStates = ref<Record<string, FlowNodeRunState>>({})
const selectedNodeId = ref('')
const nodeInspector = ref<HTMLElement | null>(null)
const flowRouteReady = ref(false)
const routeSelectionApplying = ref(false)
const flowEditorDraftReady = ref(false)
const flowEditorDraftRecovered = ref(false)
const flowDraftRevisionConflict = ref(false)
const flowEditorDraft = ref<FlowEditorDraft | null>(readFlowEditorDraft())
const orphanedFlowEditorDraft = ref<FlowEditorDraft | null>(null)

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

if (
  selectedFlowTemplate.value &&
  !flowTemplates.some((template) => template.title === selectedFlowTemplate.value)
) {
  selectedFlowTemplate.value = ''
  persistFlowCreationDraft({
    intent: flowIntent.value,
    templateTitle: '',
    updatedAt: new Date().toISOString()
  })
}

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
const hasFlowRunDraftContent = computed(() =>
  Boolean(
    flowRunContext.value.trim() ||
      Object.values(flowVariableValues.value).some((value) => value?.trim())
  )
)
const flowRunDraftStateLabel = computed(() => {
  if (flowRunDraftRecovered.value) {
    return '已恢复上次 Run Brief'
  }
  return hasFlowRunDraftContent.value ? 'Run Brief 已自动保存' : '等待补充本次运行上下文'
})
const providerReadyToRun = computed(() => Boolean(workspace.activeProvider))
const flowReadyToRun = computed(() =>
  providerReadyToRun.value && !hasIncompleteFlowNodes.value && !hasMissingFlowVariables.value
)
const flowConflictVisible = computed(() =>
  workspace.flowConflictId === workspace.activeFlow?.id || flowDraftRevisionConflict.value
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
      flowRunSnapshot: selectedFlowRun.value.flowRunSnapshot || null,
      flowRunTrace: selectedFlowRun.value.flowRunTrace || null
    }
  }

  return workspace.latestResult
})

const activeFlowRunSnapshot = computed(() => activeFlowResult.value?.flowRunSnapshot || null)
const activeFlowResultFailed = computed(() => selectedFlowRun.value?.status === 'failed')

const flowResultHeading = computed(() => {
  if (activeFlowResultFailed.value) return '检查失败节点并恢复执行'
  return selectedFlowRun.value ? '基于历史结果继续推进' : '基于这次结果继续推进'
})

const selectedNodeState = computed<FlowNodeRunState>(() => {
  if (!selectedNode.value) {
    return 'idle'
  }
  return nodeStatus(selectedNode.value.id)
})
const selectedNodeIncomplete = computed(() => Boolean(selectedNode.value && nodeNeedsContent(selectedNode.value)))
const selectedNodeSourcePrompt = computed(() => {
  const promptId = selectedNode.value?.type === 'prompt' ? selectedNode.value.promptId : null
  return promptId ? prompts.value.find((prompt) => prompt.id === promptId) || null : null
})
const selectedNodePromptInSync = computed(() => {
  const node = selectedNode.value
  const prompt = selectedNodeSourcePrompt.value
  return Boolean(
    node &&
      prompt &&
      node.title === prompt.title &&
      node.description === prompt.description &&
      (node.content || '') === prompt.content
  )
})
const selectedNodePromptSourceDescription = computed(() => {
  if (!selectedNodeSourcePrompt.value) {
    return '来源 Prompt 已不可用，当前节点快照仍可独立编辑和执行。'
  }
  return selectedNodePromptInSync.value
    ? '当前节点与 Prompt Library 中的内容一致。'
    : '当前节点与 Library 版本不同。替换前会保留 Flow 修订快照。'
})

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

watch([flowIntent, selectedFlowTemplate], () => {
  if (!flowIntent.value.trim()) {
    flowCreationDraftRecovered.value = false
    persistFlowCreationDraft(null)
    return
  }

  persistFlowCreationDraft({
    intent: flowIntent.value,
    templateTitle: selectedFlowTemplate.value,
    updatedAt: new Date().toISOString()
  })
})

watch(
  () => workspace.activeFlow?.id,
  () => {
    const activeFlowId = workspace.activeFlow?.id
    const runSeed = activeFlowId ? workspace.consumeFlowRunSeed(activeFlowId) : null
    const localDraft = activeFlowId ? workspace.getFlowRunDraft(activeFlowId) : null
    const runDraft = runSeed || localDraft
    const nextRunContext = runDraft?.runtimeContext || ''
    const nextVariableValues = buildFlowVariableValues(flowVariables.value, runDraft?.variableValues)
    flowRunDraftHydrating.value = true
    flowRunDraftRecovered.value = Boolean(
      localDraft &&
        !runSeed &&
        (nextRunContext.trim() || Object.values(nextVariableValues).some((value) => value?.trim()))
    )
    resetFlowRunState()
    selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
    flowTitle.value = workspace.activeFlow?.title || ''
    flowDescription.value = workspace.activeFlow?.description || ''
    flowRunContext.value = nextRunContext
    flowVariableValues.value = nextVariableValues
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
    if (!routeSelectionApplying.value) {
      void syncActiveRouteState()
    }
    void nextTick(() => {
      flowRunDraftHydrating.value = false
    })
  },
  { immediate: true }
)

watch(
  [() => route.query.flow, () => route.query.node, () => route.query.run],
  ([flowId, nodeId, runId]) => {
    if (flowRouteReady.value) {
      void applyFlowRouteSelection(flowId, nodeId, runId)
    }
  }
)

watch(selectedNodeId, (nodeId) => {
  if (flowRouteReady.value && !routeSelectionApplying.value) {
    void syncActiveRouteState()
  }
})

watch(
  () => selectedFlowRun.value?.id,
  () => {
    if (flowRouteReady.value && !routeSelectionApplying.value) {
      void syncActiveRouteState()
    }
  }
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

watch(flowVariables, (variables) => {
  flowVariableValues.value = buildFlowVariableValues(variables, flowVariableValues.value)
})

watch([flowRunContext, flowVariableValues], () => {
  if (!flowRunDraftHydrating.value) {
    flowRunDraftRecovered.value = false
  }
  const flowId = workspace.activeFlow?.id
  if (flowId) {
    workspace.saveFlowRunDraft(flowId, flowRunContext.value, flowVariableValues.value)
  }
}, { deep: true })

watch(
  () => selectedNode.value?.id,
  () => {
    const draft = flowEditorDraft.value
    const draftNodeStillAvailable = workspace.activeFlow?.nodes.some((node) => node.id === draft?.nodeId)
    if (
      flowEditorDraftReady.value &&
      draft &&
      draft.flowId === workspace.activeFlow?.id &&
      draft.nodeChanged &&
      !draftNodeStillAvailable
    ) {
      orphanedFlowEditorDraft.value = draft
      flowEditorDraftRecovered.value = false
      flowDraftRevisionConflict.value = false
      return
    }
    syncSelectedNodeEditor()
  },
  { immediate: true }
)

watch(
  [
    () => workspace.activeFlow?.id,
    () => workspace.activeFlow?.revision,
    selectedNodeId,
    flowTitle,
    flowDescription,
    nodeTitle,
    nodeDescription,
    nodeContent
  ],
  () => syncFlowEditorDraft()
)

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  await Promise.all([workspace.bootstrap(), loadPromptAssets()])
  const draftRestored = workspace.flowAssetsReady ? await restoreFlowEditorDraft() : false
  flowEditorDraftReady.value = true
  flowRouteReady.value = true
  if (draftRestored) {
    await syncActiveRouteState()
  } else if (workspace.flowAssetsReady) {
    await applyFlowRouteSelection()
  }
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

async function loadFlowRuns(flowId: string) {
  flowRunsLoading.value = true
  try {
    const { data } = await listFlowRuns(flowId)
    if (workspace.activeFlow?.id === flowId) {
      const selectedRun = selectedFlowRun.value
      const preserveSelectedRun = Boolean(
        selectedRun && selectedRun.sourceFlowId === flowId && !data.some((run) => run.id === selectedRun.id)
      )
      flowRuns.value = preserveSelectedRun && selectedRun
        ? [selectedRun, ...data]
        : data
      if (selectedRun && selectedRun.sourceFlowId !== flowId) {
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

async function restoreFlowEditorDraft() {
  const draft = flowEditorDraft.value
  if (!draft) {
    return false
  }

  const sourceFlow = workspace.flowDrafts.find((flow) => flow.id === draft.flowId) || null
  const sourceNode = sourceFlow?.nodes.find((node) => node.id === draft.nodeId) || null
  if (!sourceFlow || (draft.nodeChanged && !sourceNode)) {
    orphanedFlowEditorDraft.value = draft
    ElMessage.warning('原 Flow 或编辑节点已不存在，本地草稿仍可创建为恢复副本')
    return true
  }

  workspace.selectFlowDraft(sourceFlow.id)
  await nextTick()
  selectedNodeId.value = sourceNode?.id || sourceFlow.nodes[0]?.id || ''
  await nextTick()
  flowTitle.value = sourceFlow.title
  flowDescription.value = sourceFlow.description
  syncSelectedNodeEditor()

  if (draft.flowChanged) {
    flowTitle.value = draft.flowTitle
    flowDescription.value = draft.flowDescription
  }
  if (draft.nodeChanged) {
    nodeTitle.value = draft.nodeTitle
    nodeDescription.value = draft.nodeDescription
    nodeContent.value = draft.nodeContent
  }

  if (!flowMetaChanged.value && !nodeEditorChanged.value) {
    clearFlowEditorDraft()
    return false
  }

  flowEditorDraftRecovered.value = true
  flowDraftRevisionConflict.value = draft.baseRevision !== sourceFlow.revision
  return true
}

function syncFlowEditorDraft() {
  if (!flowEditorDraftReady.value || orphanedFlowEditorDraft.value) {
    return
  }

  const flow = workspace.activeFlow
  const currentDraft = flowEditorDraft.value
  if (currentDraft && currentDraft.flowId !== flow?.id) {
    orphanedFlowEditorDraft.value = currentDraft
    flowEditorDraftRecovered.value = false
    flowDraftRevisionConflict.value = false
    return
  }
  if (!flow || (!flowMetaChanged.value && !nodeEditorChanged.value)) {
    clearFlowEditorDraft()
    return
  }

  const draft: FlowEditorDraft = {
    flowId: flow.id,
    baseRevision: currentDraft?.flowId === flow.id ? currentDraft.baseRevision : flow.revision,
    nodeId: selectedNode.value?.id || selectedNodeId.value,
    flowChanged: flowMetaChanged.value,
    nodeChanged: nodeEditorChanged.value,
    flowTitle: flowTitle.value,
    flowDescription: flowDescription.value,
    nodeTitle: nodeTitle.value,
    nodeDescription: nodeDescription.value,
    nodeContent: nodeContent.value,
    snapshot: captureFlowEditorSnapshot(flow),
    updatedAt: new Date().toISOString()
  }
  flowEditorDraft.value = draft
  persistFlowEditorDraft(draft)
}

async function recoverOrphanedFlowEditorDraft() {
  const draft = orphanedFlowEditorDraft.value
  if (!draft) {
    return
  }

  const selectedNodeIndex = draft.snapshot.nodes.findIndex((node) => node.id === draft.nodeId)
  const sourceStillAvailable = workspace.flowDrafts.some((flow) => flow.id === draft.flowId)
  const flow = await workspace.createFlowFromRecoveredEditor(
    buildRecoveredFlowSnapshot(draft),
    sourceStillAvailable ? draft.flowId : null
  )
  if (!flow) {
    return
  }

  clearFlowEditorDraft()
  await nextTick()
  selectedNodeId.value = flow.nodes[selectedNodeIndex]?.id || flow.nodes[0]?.id || ''
  flowTitle.value = flow.title
  flowDescription.value = flow.description
  syncSelectedNodeEditor()
  ElMessage.success('已创建 Flow 恢复副本')
}

function discardFlowEditorDraft() {
  if (!orphanedFlowEditorDraft.value) {
    resetEditorsToSavedState()
  }
  clearFlowEditorDraft()
  ElMessage.info('本地 Flow 草稿已放弃')
}

function adoptLatestFlowAfterConflict() {
  resetEditorsToSavedState()
  clearFlowEditorDraft()
  ElMessage.info('已采用最新 Flow')
}

function clearFlowEditorDraft() {
  flowEditorDraft.value = null
  orphanedFlowEditorDraft.value = null
  flowEditorDraftRecovered.value = false
  flowDraftRevisionConflict.value = false
  workspace.dismissFlowConflict()
  removeFlowEditorDraft()
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
  if (hadFlowChanges && hadNodeChanges && pendingNodePatch) {
    const updatedFlow = await workspace.updateFlowMetaAndNode(
      flowTitle.value,
      flowDescription.value,
      pendingNodePatch.nodeId,
      {
        title: nodeTitle.value,
        description: nodeDescription.value,
        content: nodeCanEditContent.value ? nodeContent.value : selectedNode.value?.content
      }
    )
    if (!updatedFlow) {
      return false
    }
    flowTitle.value = updatedFlow.title
    flowDescription.value = updatedFlow.description
    syncSelectedNodeEditor()
    resetFlowRunState()
    clearFlowEditorDraft()
    ElMessage.success('未保存修改已保存')
    return true
  }
  if (hadFlowChanges && !(await persistFlowMeta(false, pendingNodePatch))) {
    return false
  }
  if (hadNodeChanges && !(await persistSelectedNode(false))) {
    return false
  }
  if (hadFlowChanges || hadNodeChanges) {
    clearFlowEditorDraft()
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
      clearFlowEditorDraft()
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

async function createFlowVariantFromVersion(version: FlowVersion) {
  if (!(await resolvePendingEdits())) {
    return
  }

  branchingFlowVersion.value = true
  try {
    const flow = await workspace.createFlowFromRevision(version)
    if (!flow) {
      return
    }
    selectedNodeId.value = flow.nodes[0]?.id || ''
    selectedFlowVersion.value = null
    resetFlowRunState()
    ElMessage.success(`已从 v${version.versionNumber} 创建 Flow 变体`)
  } finally {
    branchingFlowVersion.value = false
  }
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
    const data = await workspace.restoreActiveFlowVersion(version.id)
    if (!data) {
      return
    }
    flowTitle.value = data.title
    flowDescription.value = data.description
    selectedNodeId.value = data.nodes[0]?.id || ''
    syncSelectedNodeEditor()
    flowRunContext.value = ''
    flowVariableValues.value = buildFlowVariableValues(flowVariables.value)
    selectedFlowRun.value = null
    flowExecutionVisible.value = false
    savedResultPrompt.value = null
    selectedFlowVersion.value = null
    resetFlowRunState()
    await loadFlowVersions(data.id)
    ElMessage.success('Flow 已恢复到选中修订')
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
  flowCreationDraftRecovered.value = false
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
  if (id === workspace.activeFlowId) {
    void syncActiveRouteState()
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  workspace.selectFlowDraft(id)
  selectedNodeId.value = workspace.activeFlow?.nodes[0]?.id || ''
}

async function openActiveFlowSource() {
  const sourceFlowId = workspace.activeFlow?.sourceFlowId
  if (!sourceFlowId) {
    return
  }

  await openSourceFlowById(sourceFlowId)
}

function flowSourceStillAvailable(snapshot: FlowRunSnapshotType) {
  return Boolean(
    snapshot.sourceFlowId && workspace.flowDrafts.some((flow) => flow.id === snapshot.sourceFlowId)
  )
}

async function openFlowSnapshotSource(snapshot: FlowRunSnapshotType) {
  if (!snapshot.sourceFlowId) {
    return
  }
  await openSourceFlowById(snapshot.sourceFlowId)
}

async function openSourceFlowById(sourceFlowId: string) {
  if (!(await resolvePendingEdits())) {
    return
  }

  if (!workspace.flowDrafts.some((flow) => flow.id === sourceFlowId)) {
    await workspace.loadFlowDrafts()
  }
  const sourceFlow = workspace.flowDrafts.find((flow) => flow.id === sourceFlowId)
  if (!sourceFlow) {
    ElMessage.warning('来源 Flow 已删除，来源标题和修订信息仍然保留')
    return
  }

  workspace.selectFlowDraft(sourceFlow.id)
  selectedNodeId.value = sourceFlow.nodes[0]?.id || ''
  ElMessage.success(`已打开来源 Flow「${sourceFlow.title}」`)
}

async function applyFlowRouteSelection(
  flowValue: unknown = route.query.flow,
  nodeValue: unknown = route.query.node,
  runValue: unknown = route.query.run
) {
  routeSelectionApplying.value = true
  try {
    const flowAvailable = await openFlowFromRoute(flowValue)
    if (flowAvailable) {
      await nextTick()
      await openNodeFromRoute(nodeValue)
      openFlowRunFromRoute(runValue)
    }
  } finally {
    routeSelectionApplying.value = false
    await syncActiveRouteState()
  }
}

async function openFlowFromRoute(value: unknown = route.query.flow) {
  const flowId = typeof value === 'string' ? value : ''
  if (!flowId || flowId === workspace.activeFlowId) {
    return true
  }

  const flow = workspace.flowDrafts.find((item) => item.id === flowId)
  if (!flow) {
    ElMessage.warning('指定的 Flow 已不存在或无法访问')
    void syncActiveRouteState()
    return false
  }

  if (!(await resolvePendingEdits())) {
    void syncActiveRouteState()
    return false
  }

  workspace.selectFlowDraft(flow.id)
  return true
}

async function openNodeFromRoute(value: unknown = route.query.node) {
  const nodeId = typeof value === 'string' ? value : ''
  if (!nodeId || nodeId === selectedNodeId.value) {
    return
  }

  const node = workspace.activeFlow?.nodes.find((item) => item.id === nodeId)
  if (!node) {
    ElMessage.warning('指定的 Flow 节点已不存在')
    await syncActiveRouteState()
    return
  }

  if (!(await resolvePendingEdits())) {
    await syncActiveRouteState()
    return
  }

  selectedNodeId.value = node.id
}

function openFlowRunFromRoute(value: unknown = route.query.run) {
  const runId = typeof value === 'string' ? value : ''
  if (!runId) {
    selectedFlowRun.value = null
    return
  }
  if (runId === selectedFlowRun.value?.id) {
    return
  }

  const run = workspace.tasks.find((item) => item.id === runId)
  if (!run || run.sourceFlowId !== workspace.activeFlowId) {
    selectedFlowRun.value = null
    ElMessage.warning('指定的 Flow 运行已不存在或不属于当前 Flow')
    return
  }

  if (!flowRuns.value.some((item) => item.id === run.id)) {
    flowRuns.value = [run, ...flowRuns.value]
  }
  selectFlowRun(run)
}

function syncActiveRouteState() {
  if (!flowRouteReady.value) {
    return Promise.resolve()
  }

  const flowId = workspace.activeFlowId
  const nodeId = selectedNodeId.value
  const runId = selectedFlowRun.value?.id || ''
  const routeFlowId = typeof route.query.flow === 'string' ? route.query.flow : ''
  const routeNodeId = typeof route.query.node === 'string' ? route.query.node : ''
  const routeRunId = typeof route.query.run === 'string' ? route.query.run : ''
  if (flowId === routeFlowId && nodeId === routeNodeId && runId === routeRunId) {
    return Promise.resolve()
  }

  const query = { ...route.query }
  if (flowId) {
    query.flow = flowId
  } else {
    delete query.flow
  }
  if (nodeId) {
    query.node = nodeId
  } else {
    delete query.node
  }
  if (runId) {
    query.run = runId
  } else {
    delete query.run
  }
  return router.replace({ query })
}

async function selectFlowNode(nodeId: string) {
  if (nodeId === selectedNode.value?.id || !(await resolvePendingEdits())) {
    return
  }
  selectedNodeId.value = nodeId
}

async function openExecutionPreviewNode(nodeId: string) {
  if (!workspace.activeFlow?.nodes.some((node) => node.id === nodeId)) {
    ElMessage.warning('这个执行段对应的节点已不存在，请刷新预览')
    return
  }

  await selectFlowNode(nodeId)
  if (selectedNodeId.value !== nodeId) {
    return
  }

  await nextTick()
  nodeInspector.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

async function focusExecutionPreviewVariable(variable: string) {
  const variableIndex = flowVariables.value.indexOf(variable)
  if (variableIndex < 0) {
    ElMessage.warning('这个变量已不存在，请刷新预览')
    return
  }

  await nextTick()
  const input = document.getElementById(`flow-variable-${variableIndex}`) as HTMLTextAreaElement | null
  input?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  input?.focus({ preventScroll: true })
}

function useFlowTemplate(template: FlowTemplate) {
  const currentTemplate = selectedFlowTemplateDetail.value
  const currentIntent = flowIntent.value.trim()
  const stillUsingTemplateDefault = Boolean(
    currentTemplate && currentIntent === currentTemplate.intent.trim()
  )
  selectedFlowTemplate.value = template.title
  if (!currentIntent || stillUsingTemplateDefault) {
    flowIntent.value = template.intent
  }
  flowCreationDraftRecovered.value = false
}

function detachFlowTemplate() {
  selectedFlowTemplate.value = ''
  flowCreationDraftRecovered.value = false
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

async function persistRunBriefAsContext() {
  const content = flowRunContext.value.trim()
  if (!content) {
    return
  }
  if (!canPersistFlowContext(content)) {
    ElMessage.warning(`运行说明超过 ${MAX_FLOW_CONTEXT_LENGTH} 字符，请精简后再固化到 Flow`)
    return
  }
  if (!(await resolvePendingEdits())) {
    return
  }

  const addedNode = await workspace.addContextToActiveFlow(content)
  if (!addedNode) {
    return
  }

  flowRunContext.value = ''
  selectedNodeId.value = addedNode.id
  flowExecutionVisible.value = false
  selectedFlowRun.value = null
  resetFlowRunState()
  ElMessage.success('运行说明已固化为可复用上下文')
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

async function openSelectedNodePrompt() {
  const prompt = selectedNodeSourcePrompt.value
  if (!prompt || !(await resolvePendingEdits())) {
    return
  }
  await router.push({ path: '/prompts', query: { prompt: prompt.id } })
}

async function syncSelectedNodePrompt() {
  const node = selectedNode.value
  const prompt = selectedNodeSourcePrompt.value
  if (!node || !prompt || selectedNodePromptInSync.value || !(await resolvePendingEdits())) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '这会用 Prompt Library 当前的名称、说明和内容替换节点。Flow 修订历史会保留替换前状态。',
      '同步 Prompt 节点',
      {
        confirmButtonText: '替换节点',
        cancelButtonText: '取消',
        closeOnClickModal: false,
        type: 'warning'
      }
    )
  } catch {
    return
  }

  const updatedFlow = await workspace.syncFlowPromptNode(node.id, prompt)
  if (!updatedFlow) {
    return
  }

  selectedNodeId.value = node.id
  resetFlowRunState()
  syncSelectedNodeEditor()
  ElMessage.success('Prompt 节点已同步到 Library 版本')
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
  flowRunPhase.value = run.status === 'failed' ? 'error' : 'completed'
  flowRunCompletedAt.value = run.createdAt
  if (workspace.activeFlow) {
    nodeRunStates.value = run.flowRunTrace
      ? buildNodeRunStatesFromTrace(workspace.activeFlow.nodes, run)
      : buildNodeRunStates(workspace.activeFlow.nodes, run.status === 'failed' ? 'error' : 'completed')
  }
}

async function rerunSelectedFlowRun() {
  const sourceRun = selectedFlowRun.value
  const flowId = workspace.activeFlow?.id
  if (!sourceRun || !flowId) {
    return
  }
  if (!workspace.activeProvider) {
    ElMessage.warning('请先配置并激活 AI Provider')
    goToApiKeys()
    return
  }

  const result = await workspace.rerunHistoricalTask(sourceRun.id)
  await loadFlowRuns(flowId)
  const rerun = result?.taskId
    ? flowRuns.value.find((run) => run.id === result.taskId)
    : flowRuns.value.find((run) => run.rerunOfTaskId === sourceRun.id)
  if (rerun) {
    selectFlowRun(rerun)
  }
}

function openSelectedRunHistory() {
  const runId = activeFlowResult.value?.taskId
  if (!runId) {
    return
  }
  router.push({ path: '/history', query: { run: runId } })
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
  selectedFlowRun.value = null
  resetFlowRunState()
  ElMessage.success('已带入 Run Brief')
}

async function reuseFlowRunSettings(snapshot: FlowRunSnapshotType) {
  if (!workspace.activeFlow || snapshot.flowId !== workspace.activeFlow.id) {
    return
  }

  if (shouldConfirmFlowRunSettingsReplacement(
    { runtimeContext: flowRunContext.value, variableValues: flowVariableValues.value },
    snapshot
  )) {
    try {
      await ElMessageBox.confirm(
        '当前 Run Brief 已包含自动保存的运行说明或变量值。复用历史配置会替换这些内容。',
        '替换当前 Run Brief？',
        {
          confirmButtonText: '替换并复用',
          cancelButtonText: '保留当前内容',
          type: 'warning'
        }
      )
    } catch {
      return
    }
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

function clearCurrentFlowRunDraft() {
  const flowId = workspace.activeFlow?.id
  if (!flowId) {
    return
  }
  flowRunContext.value = ''
  flowVariableValues.value = buildFlowVariableValues(flowVariables.value)
  flowRunDraftRecovered.value = false
  workspace.clearFlowRunDraft(flowId)
  resetFlowRunState()
  ElMessage.success('Run Brief 已清除')
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
  await loadFlowRuns(flowId)
  const runStartedAt = Date.parse(flowRunStartedAt.value)
  const failedRun = flowRuns.value.find(
    (run) => run.status === 'failed' && Date.parse(run.createdAt) >= runStartedAt
  )
  if (failedRun) {
    selectFlowRun(failedRun)
  }
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

function buildNodeRunStatesFromTrace(nodes: FlowNode[], run: TaskHistoryItem) {
  const traceStateByNodeId = new Map(
    (run.flowRunTrace?.nodes || []).map((node) => [node.nodeId, node.status])
  )
  return nodes.reduce<Record<string, FlowNodeRunState>>((states, node) => {
    const traceState = traceStateByNodeId.get(node.id)
    states[node.id] = traceState === 'failed'
      ? 'error'
      : traceState === 'completed'
        ? 'completed'
        : traceState === 'prepared'
          ? 'prepared'
          : 'idle'
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
