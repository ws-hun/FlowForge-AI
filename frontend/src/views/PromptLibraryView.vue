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
        <span>先创建一个常用工作方式，或加入一组 FlowForge Starter Prompt。</span>
      </div>
      <div class="empty-actions">
        <button type="button" class="primary-button" @click="openCreate">新建 Prompt</button>
        <button type="button" class="secondary-button" @click="createStarterPrompts">添加示例</button>
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

        <footer class="prompt-detail-actions">
          <button type="button" class="ghost-button" @click="openEditFromDetail">编辑 Prompt</button>
          <button type="button" class="primary-button" @click="sendPreparedPrompt">进入 Task</button>
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
  listPrompts,
  togglePromptFavorite,
  updatePrompt
} from '@/api/prompts'
import { useWorkspaceStore } from '@/stores/workspace'
import type { PromptAsset, SavePromptPayload } from '@/types'

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
const selectedPrompt = ref<PromptAsset | null>(null)
const variableValues = ref<Record<string, string>>({})

const form = reactive<SavePromptPayload>({
  title: '',
  category: '',
  description: '',
  content: '',
  tags: [],
  favorite: false
})

const starterPrompts: SavePromptPayload[] = [
  {
    title: '需求拆解 Flow',
    category: '产品',
    description: '把模糊想法整理为目标用户、MVP 范围、优先级和下一步任务。',
    content: '请将下面的产品想法拆解为：目标用户、核心问题、MVP 功能、优先级、风险和下一步执行任务。\n\n输入：{idea}',
    tags: ['产品', 'MVP', '拆解'],
    favorite: true
  },
  {
    title: '接口设计 Flow',
    category: '工程',
    description: '根据业务目标生成 REST API 草案、数据结构和边界条件。',
    content: '请根据下面的业务目标，设计 REST API 草案，包括资源路径、请求体、响应体、错误码、边界条件和测试建议。\n\n目标：{goal}',
    tags: ['API', '后端', '设计'],
    favorite: false
  },
  {
    title: '风险评审 Flow',
    category: '评审',
    description: '识别方案中的技术风险、依赖风险、用户体验风险和测试缺口。',
    content: '请评审下面的方案，输出主要风险、影响范围、优先级、缓解建议和必须补充的测试。\n\n方案：{proposal}',
    tags: ['质量', '评审', '风险'],
    favorite: false
  },
  {
    title: '会议总结 Flow',
    category: '协作',
    description: '把会议记录整理成结论、决策、行动项和待确认问题。',
    content: '请将下面的会议记录整理为：核心结论、已决策事项、行动项、负责人、截止时间和待确认问题。\n\n会议记录：{notes}',
    tags: ['总结', '团队', '行动项'],
    favorite: false
  }
]

const categories = computed(() => {
  const values = Array.from(new Set(prompts.value.map((prompt) => prompt.category).filter(Boolean)))
  return ['all', ...values]
})

const visibleCategories = computed(() => categories.value.slice(0, 6))

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
      await updatePrompt(editingPrompt.value.id, payload)
      ElMessage.success('Prompt 已更新')
    } else {
      await createPrompt(payload)
      ElMessage.success('Prompt 已保存')
    }
    dialogOpen.value = false
    await loadPromptAssets()
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
    await Promise.all(starterPrompts.map((prompt) => createPrompt(prompt)))
    ElMessage.success('示例 Prompt 已加入 Library')
    await loadPromptAssets()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '示例 Prompt 创建失败')
  } finally {
    saving.value = false
  }
}

function openPromptDetail(prompt: PromptAsset) {
  const variables = extractVariables(prompt.content)
  selectedPrompt.value = prompt
  variableValues.value = Object.fromEntries(variables.map((variable) => [variable, '']))
  detailOpen.value = true
}

function sendPreparedPrompt() {
  if (!selectedPrompt.value) {
    return
  }
  detailOpen.value = false
  sendToTask(preparedPromptPreview.value)
}

function sendToTask(content: string) {
  workspace.taskInput = content
  ElMessage.success('Prompt 已带入 AI Command Workspace')
  router.push('/tasks')
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
</script>
