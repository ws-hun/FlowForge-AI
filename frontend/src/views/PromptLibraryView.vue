<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">Prompt Library</p>
      <h1>沉淀可复用的 AI 工作方式。</h1>
      <p>把有效的 Prompt 保存成资产，从这里搜索、收藏，并快速带入命令工作区执行。</p>
    </header>

    <div class="library-toolbar">
      <input v-model="search" class="quiet-input" placeholder="搜索 Prompt、标签或场景..." />
      <button
        v-for="category in visibleCategories"
        :key="category"
        type="button"
        class="category-pill"
        :class="{ active: activeCategory === category }"
        @click="activeCategory = category"
      >
        {{ category === 'all' ? '全部' : category }}
      </button>
      <button type="button" class="ghost-button" :class="{ active: favoriteOnly }" @click="favoriteOnly = !favoriteOnly">
        收藏
      </button>
      <button type="button" class="primary-button" @click="openCreate">新建 Prompt</button>
    </div>

    <section v-if="!loading" class="starter-section">
      <div class="section-heading">
        <div>
          <p class="section-kicker">Starter Prompt Pack</p>
          <h2>从已验证的工作方式开始。</h2>
        </div>
        <button type="button" class="secondary-button" :disabled="saving || allStarterPromptsAdded" @click="createStarterPrompts">
          {{ allStarterPromptsAdded ? '已全部加入' : '加入全部' }}
        </button>
      </div>

      <div class="starter-grid">
        <article
          v-for="starter in starterPrompts"
          :key="starter.title"
          class="soft-card starter-tile"
          @click="openStarterDetail(starter)"
        >
          <span class="starter-signal">{{ starter.signal }}</span>
          <strong>{{ starter.title }}</strong>
          <p>{{ starter.description }}</p>
          <div class="tag-cloud">
            <span v-for="tag in starter.tags" :key="tag">#{{ tag }}</span>
          </div>
          <button
            type="button"
            class="ghost-button"
            :disabled="starterPromptExists(starter)"
            @click.stop="importStarterPrompt(starter)"
          >
            {{ starterPromptExists(starter) ? '已加入' : '加入 Library' }}
          </button>
        </article>
      </div>
    </section>

    <div v-if="loading" class="gallery-grid">
      <article v-for="item in 3" :key="item" class="soft-card prompt-tile skeleton-tile"></article>
    </div>

    <div v-else-if="filteredPrompts.length" class="gallery-grid">
      <article v-for="prompt in filteredPrompts" :key="prompt.id" class="soft-card prompt-tile" @click="openPromptDetail(prompt)">
        <div class="row-between">
          <span class="badge">{{ prompt.category }}</span>
          <button type="button" class="icon-button" :class="{ active: prompt.favorite }" @click.stop="toggleFavorite(prompt)">
            {{ prompt.favorite ? '★' : '☆' }}
          </button>
        </div>

        <strong>{{ prompt.title }}</strong>
        <p>{{ prompt.description }}</p>

        <pre class="prompt-preview">{{ prompt.content }}</pre>

        <div class="tag-cloud">
          <span v-for="tag in prompt.tags" :key="tag">#{{ tag }}</span>
        </div>

        <div class="prompt-actions">
          <button type="button" class="primary-button" @click.stop="openPromptDetail(prompt)">准备执行</button>
          <button type="button" class="secondary-button" @click.stop="openEdit(prompt)">编辑</button>
          <button type="button" class="ghost-button" @click.stop="removePrompt(prompt)">删除</button>
        </div>
      </article>
    </div>

    <div v-else class="empty-state prompt-empty">
      <div>
        <strong>还没有可复用 Prompt</strong>
        <span>先创建一个常用工作方式，或从上方 Starter Prompt Pack 选择一个开始。</span>
      </div>
      <div class="empty-actions">
        <button type="button" class="primary-button" @click="openCreate">新建 Prompt</button>
      </div>
    </div>

    <el-dialog v-model="dialogOpen" :title="editingPrompt ? '编辑 Prompt' : '新建 Prompt'" width="680px" class="calm-dialog">
      <div class="prompt-form">
        <label>
          <span>名称</span>
          <input v-model="form.title" class="quiet-input" placeholder="例如：需求拆解 Flow" />
        </label>
        <label>
          <span>分类</span>
          <input v-model="form.category" class="quiet-input" placeholder="产品 / 工程 / 研究 / 文档" />
        </label>
        <label>
          <span>描述</span>
          <input v-model="form.description" class="quiet-input" placeholder="说明这个 Prompt 适合什么工作场景" />
        </label>
        <label>
          <span>Prompt 内容</span>
          <textarea
            v-model="form.content"
            class="workspace-input prompt-editor"
            placeholder="写下可复用 Prompt，可使用 {变量名} 标记输入位置..."
          ></textarea>
        </label>
        <label>
          <span>标签</span>
          <input v-model="tagInput" class="quiet-input" placeholder="用逗号分隔，例如：产品, PRD, 拆解" />
        </label>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <button type="button" class="ghost-button" @click="dialogOpen = false">取消</button>
          <button type="button" class="primary-button" :disabled="saving" @click="savePromptAsset">
            {{ saving ? '保存中...' : '保存 Prompt' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" size="560px" :with-header="false" class="prompt-detail-drawer">
      <aside v-if="selectedPrompt" class="prompt-detail">
        <header class="prompt-detail-header">
          <div>
            <span class="badge">{{ selectedPrompt.category }}</span>
            <h2>{{ selectedPrompt.title }}</h2>
            <p>{{ selectedPrompt.description }}</p>
          </div>
          <button type="button" class="icon-button" @click="detailOpen = false">×</button>
        </header>

        <div class="tag-cloud">
          <span v-for="tag in selectedPrompt.tags" :key="tag">#{{ tag }}</span>
        </div>

        <section class="detail-section">
          <div class="section-heading compact">
            <h3>Prompt</h3>
            <span>更新于 {{ formatDate(selectedPrompt.updatedAt) }}</span>
          </div>
          <pre class="detail-code">{{ selectedPrompt.content }}</pre>
        </section>

        <section v-if="promptVariables.length" class="detail-section">
          <div class="section-heading compact">
            <h3>变量</h3>
            <span>{{ promptVariables.length }} 个上下文输入</span>
          </div>
          <div class="variable-list">
            <label v-for="variable in promptVariables" :key="variable">
              <span>{{ variable }}</span>
              <textarea
                v-model="variableValues[variable]"
                class="quiet-textarea"
                :placeholder="`填写 ${variable} 的上下文...`"
              ></textarea>
            </label>
          </div>
        </section>

        <section class="detail-section">
          <div class="section-heading compact">
            <h3>执行预览</h3>
            <span>将发送到 AI Command Workspace</span>
          </div>
          <pre class="detail-code preview">{{ preparedPromptPreview }}</pre>
        </section>

        <section v-if="!isStarterDetail" class="detail-section">
          <div class="section-heading compact">
            <h3>最近执行</h3>
            <span>{{ promptRuns.length ? `${promptRuns.length} 条记录` : '还没有执行记录' }}</span>
          </div>
          <div v-if="promptRunsLoading" class="run-timeline">
            <article v-for="item in 2" :key="item" class="run-item skeleton-run"></article>
          </div>
          <div v-else-if="promptRuns.length" class="run-timeline">
            <article v-for="run in promptRuns" :key="run.id" class="run-item">
              <time>{{ formatDate(run.createdAt) }}</time>
              <strong>{{ run.summary }}</strong>
              <p>{{ run.result }}</p>
            </article>
          </div>
          <div v-else class="quiet-empty">
            从这个 Prompt 进入 AI Command Workspace 后，结果会沉淀在这里。
          </div>
        </section>

        <section v-if="!isStarterDetail" class="detail-section">
          <div class="section-heading compact">
            <h3>版本记录</h3>
            <span>{{ promptVersions.length ? `${promptVersions.length} 个快照` : '编辑后自动生成' }}</span>
          </div>
          <div v-if="promptVersionsLoading" class="version-list">
            <article v-for="item in 2" :key="item" class="version-item skeleton-run"></article>
          </div>
          <div v-else-if="promptVersions.length" class="version-list">
            <button
              v-for="version in promptVersions"
              :key="version.id"
              type="button"
              class="version-item"
              :class="{ active: selectedVersion?.id === version.id }"
              @click="selectedVersion = version"
            >
              <span>v{{ version.versionNumber }}</span>
              <strong>{{ version.title }}</strong>
              <time>{{ formatDate(version.createdAt) }}</time>
            </button>
          </div>
          <div v-else class="quiet-empty">
            第一次编辑 Prompt 后，旧内容会作为版本快照保存在这里。
          </div>

          <div v-if="selectedVersion" class="version-preview">
            <div class="row-between">
              <span class="badge">v{{ selectedVersion.versionNumber }}</span>
              <button type="button" class="ghost-button" :disabled="saving" @click="restoreVersionSnapshot(selectedVersion)">
                恢复此版本
              </button>
            </div>
            <strong>{{ selectedVersion.title }}</strong>
            <p>{{ selectedVersion.description }}</p>
            <pre class="detail-code">{{ selectedVersion.content }}</pre>
          </div>
        </section>

        <footer class="prompt-detail-actions">
          <template v-if="isStarterDetail">
            <button
              type="button"
              class="ghost-button"
              :disabled="selectedPrompt ? starterPromptExists(selectedPrompt) : false"
              @click="selectedPrompt && importStarterPrompt(selectedPrompt)"
            >
              {{ selectedPrompt && starterPromptExists(selectedPrompt) ? '已加入 Library' : '加入 Library' }}
            </button>
            <button type="button" class="secondary-button" :disabled="saving" @click="createFlowFromSelectedPrompt">
              加入并创建 Flow
            </button>
            <button type="button" class="primary-button" @click="importStarterAndRun">加入并进入 Task</button>
          </template>
          <template v-else>
            <button type="button" class="ghost-button" @click="openEditFromDetail">编辑 Prompt</button>
            <button type="button" class="secondary-button" :disabled="saving" @click="createFlowFromSelectedPrompt">
              创建 Flow
            </button>
            <button type="button" class="primary-button" @click="sendPreparedPrompt">进入 Task</button>
          </template>
        </footer>
      </aside>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPrompt,
  deletePrompt,
  listPromptRuns,
  listPromptVersions,
  listPrompts,
  restorePromptVersion,
  togglePromptFavorite,
  updatePrompt
} from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { PromptAsset, PromptVersion, SavePromptPayload, TaskHistoryItem } from '@/types'

type StarterPrompt = SavePromptPayload & {
  signal: string
}

const router = useRouter()
const workspace = useWorkspaceStore()

const prompts = ref<PromptAsset[]>([])
const search = ref('')
const activeCategory = ref('all')
const favoriteOnly = ref(false)
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const editingPrompt = ref<PromptAsset | null>(null)
const tagInput = ref('')
const detailOpen = ref(false)
const detailSource = ref<'library' | 'starter'>('library')
const selectedPrompt = ref<PromptAsset | null>(null)
const promptRuns = ref<TaskHistoryItem[]>([])
const promptRunsLoading = ref(false)
const promptVersions = ref<PromptVersion[]>([])
const promptVersionsLoading = ref(false)
const selectedVersion = ref<PromptVersion | null>(null)
const variableValues = ref<Record<string, string>>({})

const form = reactive<SavePromptPayload>({
  title: '',
  category: '',
  description: '',
  content: '',
  tags: [],
  favorite: false
})

const starterPrompts: StarterPrompt[] = [
  {
    signal: 'Idea to Scope',
    title: '产品想法成形 Flow',
    category: '产品',
    description: '把一句模糊想法整理成可执行的产品方向、MVP 范围和第一轮任务。',
    content:
      '你是一位资深产品负责人。请把下面的产品想法整理成一个清晰、可执行的 MVP 方案。\n\n产品想法：{idea}\n目标用户：{target_user}\n约束条件：{constraints}\n\n请按以下结构输出：\n1. 一句话定位\n2. 用户问题\n3. MVP 功能边界\n4. 不做什么\n5. 优先级建议\n6. 主要风险\n7. 接下来 3 个行动项',
    tags: ['产品', 'MVP', '规划'],
    favorite: true
  },
  {
    signal: 'Spec to API',
    title: '接口方案生成 Flow',
    category: '工程',
    description: '把业务目标转化为 REST API 草案、请求响应结构、错误边界和测试建议。',
    content:
      '你是一位 Staff Backend Engineer。请根据下面的信息设计一组简洁、可演进的 REST API。\n\n业务目标：{goal}\n核心资源：{resource}\n前端使用场景：{frontend_scenario}\n\n请输出：\n1. API 设计原则\n2. Endpoint 列表\n3. 请求体与响应体 JSON 示例\n4. 错误码与边界条件\n5. 数据校验规则\n6. 后端分层建议\n7. 必须覆盖的测试用例',
    tags: ['API', '后端', '工程'],
    favorite: false
  },
  {
    signal: 'Plan Review',
    title: '方案风险评审 Flow',
    category: '评审',
    description: '在开工前识别技术、体验、依赖和测试风险，避免方案只停留在乐观假设。',
    content:
      '你是一位严谨的技术评审负责人。请评审下面的方案，不要泛泛而谈，要指出具体风险和可执行的修正建议。\n\n方案：{proposal}\n当前阶段：{stage}\n关键限制：{limitations}\n\n请输出：\n1. 最高风险清单\n2. 每个风险的影响范围\n3. 发生概率与严重程度\n4. 缓解方案\n5. 需要补充的产品决策\n6. 需要补充的测试\n7. 是否建议现在推进',
    tags: ['评审', '质量', '风险'],
    favorite: false
  },
  {
    signal: 'Notes to Action',
    title: '会议行动项 Flow',
    category: '协作',
    description: '把松散会议记录整理成清晰结论、负责人、截止时间和待确认问题。',
    content:
      '你是一位高效的项目协作助手。请将下面的会议记录整理成可执行的团队同步文档。\n\n会议记录：{notes}\n项目背景：{context}\n\n请输出：\n1. 核心结论\n2. 已确认决策\n3. 行动项列表（事项 / 负责人 / 截止时间 / 优先级）\n4. 待确认问题\n5. 需要同步给谁\n6. 下一次跟进建议',
    tags: ['协作', '总结', '行动项'],
    favorite: false
  },
  {
    signal: 'Research Brief',
    title: '研究简报 Flow',
    category: '研究',
    description: '把一个研究主题整理成问题框架、信息缺口、调研路径和可交付简报。',
    content:
      '你是一位产品研究员。请围绕下面主题生成一份研究简报框架，重点是帮助团队快速理解问题和下一步调研路径。\n\n研究主题：{topic}\n已知背景：{background}\n目标产出：{deliverable}\n\n请输出：\n1. 研究目标\n2. 关键问题\n3. 已知事实与假设\n4. 信息缺口\n5. 调研路径\n6. 预期交付物结构\n7. 一周内可执行计划',
    tags: ['研究', '简报', '调研'],
    favorite: false
  },
  {
    signal: 'Draft to Doc',
    title: '文档润色 Flow',
    category: '文档',
    description: '把粗糙草稿改写成结构清晰、语气专业、可直接交付的文档。',
    content:
      '你是一位资深技术写作者。请把下面草稿改写成清晰、克制、可交付的专业文档。\n\n草稿：{draft}\n读者：{audience}\n期望语气：{tone}\n\n请输出：\n1. 改写后的完整文档\n2. 结构调整说明\n3. 删除或弱化的内容\n4. 仍需补充的信息',
    tags: ['文档', '写作', '润色'],
    favorite: false
  }
]

const categories = computed(() => {
  const values = Array.from(new Set(prompts.value.map((prompt) => prompt.category).filter(Boolean)))
  return ['all', ...values]
})

const visibleCategories = computed(() => categories.value.slice(0, 6))

const existingPromptTitles = computed(() => new Set(prompts.value.map((prompt) => normalizeTitle(prompt.title))))

const allStarterPromptsAdded = computed(() => starterPrompts.every((prompt) => starterPromptExists(prompt)))

const isStarterDetail = computed(() => detailSource.value === 'starter')

const filteredPrompts = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return prompts.value.filter((prompt) => {
    const categoryMatched = activeCategory.value === 'all' || prompt.category === activeCategory.value
    const favoriteMatched = !favoriteOnly.value || prompt.favorite
    const keywordMatched =
      !keyword ||
      [prompt.title, prompt.category, prompt.description, prompt.content, ...prompt.tags]
        .join(' ')
        .toLowerCase()
        .includes(keyword)

    return categoryMatched && favoriteMatched && keywordMatched
  })
})

const promptVariables = computed(() => {
  if (!selectedPrompt.value) {
    return []
  }
  return extractVariables(selectedPrompt.value.content)
})

const preparedPromptPreview = computed(() => {
  if (!selectedPrompt.value) {
    return ''
  }

  let preparedPrompt = selectedPrompt.value.content
  for (const variable of promptVariables.value) {
    const value = variableValues.value[variable]?.trim()
    preparedPrompt = preparedPrompt.split(`{${variable}}`).join(value || `{${variable}}`)
  }
  return preparedPrompt
})

onMounted(loadPromptAssets)

async function loadPromptAssets() {
  loading.value = true
  try {
    const { data } = await listPrompts()
    prompts.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt Library 加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingPrompt.value = null
  fillForm()
  dialogOpen.value = true
}

function openEdit(prompt: PromptAsset) {
  editingPrompt.value = prompt
  fillForm(prompt)
  dialogOpen.value = true
}

function openEditFromDetail() {
  if (!selectedPrompt.value) {
    return
  }
  detailOpen.value = false
  openEdit(selectedPrompt.value)
}

function fillForm(prompt?: PromptAsset) {
  form.title = prompt?.title || ''
  form.category = prompt?.category || ''
  form.description = prompt?.description || ''
  form.content = prompt?.content || ''
  form.tags = prompt?.tags || []
  form.favorite = prompt?.favorite || false
  tagInput.value = form.tags.join(', ')
}

function buildPayload(): SavePromptPayload {
  return {
    title: form.title.trim(),
    category: form.category.trim(),
    description: form.description.trim(),
    content: form.content.trim(),
    tags: tagInput.value
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
    favorite: form.favorite
  }
}

async function savePromptAsset() {
  const payload = buildPayload()
  if (!payload.title || !payload.category || !payload.description || !payload.content) {
    ElMessage.warning('请补全 Prompt 信息')
    return
  }

  saving.value = true
  try {
    if (editingPrompt.value) {
      const { data } = await updatePrompt(editingPrompt.value.id, payload)
      replacePromptInLibrary(data)
      if (selectedPrompt.value?.id === data.id) {
        selectedPrompt.value = data
        variableValues.value = buildVariableValues(data.content, true)
        await loadPromptVersions(data.id)
      }
      ElMessage.success('Prompt 已更新')
    } else {
      await createPrompt(payload)
      ElMessage.success('Prompt 已保存')
    }
    dialogOpen.value = false
    if (!editingPrompt.value) {
      await loadPromptAssets()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleFavorite(prompt: PromptAsset) {
  try {
    const { data } = await togglePromptFavorite(prompt.id)
    const index = prompts.value.findIndex((item) => item.id === prompt.id)
    if (index >= 0) {
      prompts.value[index] = data
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '收藏状态更新失败')
  }
}

async function removePrompt(prompt: PromptAsset) {
  try {
    await ElMessageBox.confirm(`删除「${prompt.title}」后无法在库中复用。`, '删除 Prompt', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePrompt(prompt.id)
    prompts.value = prompts.value.filter((item) => item.id !== prompt.id)
    ElMessage.success('Prompt 已删除')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || 'Prompt 删除失败')
    }
  }
}

async function createStarterPrompts() {
  saving.value = true
  try {
    const promptsToCreate = starterPrompts.filter((prompt) => !starterPromptExists(prompt))
    if (!promptsToCreate.length) {
      ElMessage.info('Starter Prompt 已全部在 Library 中')
      return
    }

    await Promise.all(promptsToCreate.map((prompt) => createPrompt(toSavePayload(prompt))))
    ElMessage.success('Starter Prompt Pack 已加入 Library')
    await loadPromptAssets()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Starter Prompt 创建失败')
  } finally {
    saving.value = false
  }
}

async function importStarterPrompt(prompt: SavePromptPayload, notifyExisting = true): Promise<PromptAsset | null> {
  const existingPrompt = findExistingPrompt(prompt)
  if (existingPrompt) {
    if (notifyExisting) {
      ElMessage.info('这个 Prompt 已在 Library 中')
    }
    return existingPrompt
  }

  saving.value = true
  try {
    const { data } = await createPrompt(toSavePayload(prompt))
    ElMessage.success('Prompt 已加入 Library')
    await loadPromptAssets()
    return data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 加入失败')
    return null
  } finally {
    saving.value = false
  }
}

function openPromptDetail(prompt: PromptAsset) {
  detailSource.value = 'library'
  selectedPrompt.value = prompt
  promptRuns.value = []
  promptVersions.value = []
  selectedVersion.value = null
  variableValues.value = buildVariableValues(prompt.content)
  detailOpen.value = true
  loadPromptRuns(prompt.id)
  loadPromptVersions(prompt.id)
}

function openStarterDetail(prompt: StarterPrompt) {
  detailSource.value = 'starter'
  promptRuns.value = []
  promptVersions.value = []
  selectedVersion.value = null
  selectedPrompt.value = {
    ...toSavePayload(prompt),
    id: prompt.title,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
  variableValues.value = buildVariableValues(prompt.content)
  detailOpen.value = true
}

function sendPreparedPrompt() {
  if (!selectedPrompt.value) {
    return
  }
  detailOpen.value = false
  sendToTask(preparedPromptPreview.value, selectedPrompt.value)
}

async function importStarterAndRun() {
  if (!selectedPrompt.value) {
    return
  }

  const prompt = await importStarterPrompt(selectedPrompt.value, false)
  if (!prompt) {
    return
  }
  detailOpen.value = false
  sendToTask(preparedPromptPreview.value, prompt)
}

async function createFlowFromSelectedPrompt() {
  if (!selectedPrompt.value) {
    return
  }

  const prompt = isStarterDetail.value
    ? await importStarterPrompt(selectedPrompt.value, false)
    : selectedPrompt.value

  if (!prompt) {
    return
  }

  const flow = await workspace.createFlowFromPrompt(prompt)
  if (!flow) {
    return
  }

  detailOpen.value = false
  ElMessage.success('Flow 已从 Prompt 创建')
  router.push('/workflows')
}

function sendToTask(content: string, prompt?: PromptAsset | null) {
  workspace.prepareTask(content, prompt ? { id: prompt.id, title: prompt.title } : null)
  ElMessage.success('Prompt 已带入 AI Command Workspace')
  router.push('/tasks')
}

async function loadPromptRuns(promptId: string) {
  promptRunsLoading.value = true
  try {
    const { data } = await listPromptRuns(promptId)
    if (selectedPrompt.value?.id !== promptId) {
      return
    }
    promptRuns.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 执行记录加载失败')
  } finally {
    promptRunsLoading.value = false
  }
}

async function loadPromptVersions(promptId: string) {
  promptVersionsLoading.value = true
  try {
    const { data } = await listPromptVersions(promptId)
    if (selectedPrompt.value?.id !== promptId) {
      return
    }
    promptVersions.value = data
    if (selectedVersion.value && !data.some((version) => version.id === selectedVersion.value?.id)) {
      selectedVersion.value = null
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 版本记录加载失败')
  } finally {
    promptVersionsLoading.value = false
  }
}

async function restoreVersionSnapshot(version: PromptVersion) {
  if (!selectedPrompt.value) {
    return
  }

  saving.value = true
  try {
    const { data } = await restorePromptVersion(selectedPrompt.value.id, version.id)
    replacePromptInLibrary(data)
    selectedPrompt.value = data
    selectedVersion.value = null
    variableValues.value = buildVariableValues(data.content, true)
    await loadPromptVersions(data.id)
    ElMessage.success('Prompt 已恢复到选中版本')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || 'Prompt 版本恢复失败')
  } finally {
    saving.value = false
  }
}

function replacePromptInLibrary(prompt: PromptAsset) {
  const index = prompts.value.findIndex((item) => item.id === prompt.id)
  if (index >= 0) {
    prompts.value[index] = prompt
  } else {
    prompts.value = [prompt, ...prompts.value]
  }
}

function buildVariableValues(content: string, preserveCurrent = false) {
  return Object.fromEntries(
    extractVariables(content).map((variable) => [variable, preserveCurrent ? variableValues.value[variable] || '' : ''])
  )
}

function extractVariables(content: string) {
  const matches = content.match(/\{[a-zA-Z0-9_\u4e00-\u9fa5-]+\}/g) || []
  return Array.from(new Set(matches.map((match) => match.slice(1, -1))))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function starterPromptExists(prompt: Pick<SavePromptPayload, 'title'>) {
  return existingPromptTitles.value.has(normalizeTitle(prompt.title))
}

function findExistingPrompt(prompt: Pick<SavePromptPayload, 'title'>) {
  return prompts.value.find((item) => normalizeTitle(item.title) === normalizeTitle(prompt.title)) || null
}

function normalizeTitle(title: string) {
  return title.trim().toLowerCase()
}

function toSavePayload(prompt: SavePromptPayload): SavePromptPayload {
  return {
    title: prompt.title,
    category: prompt.category,
    description: prompt.description,
    content: prompt.content,
    tags: [...prompt.tags],
    favorite: prompt.favorite
  }
}
</script>
