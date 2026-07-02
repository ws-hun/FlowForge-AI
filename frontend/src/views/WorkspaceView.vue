<template>
  <section class="workspace-home">
    <div class="home-hero">
      <p class="page-kicker">FlowForge</p>
      <h1 class="page-title">把想法变成 AI 工作流。</h1>
      <p class="page-subtitle">从一句自然语言开始，整理任务意图，进入命令工作区，并逐步形成可复用的 Flow。</p>
    </div>

    <div class="create-composer surface">
      <div class="composer-heading">
        <span>New Flow</span>
        <strong>描述你想创建的 AI 工作</strong>
      </div>
      <textarea
        ref="inputRef"
        v-model="workspace.taskInput"
        class="workspace-input"
        placeholder="例如：帮我把一个产品想法拆解成 PRD、接口草案和执行任务..."
      ></textarea>
      <div class="composer-footer">
        <span>{{ workspace.taskInput.trim() ? '已准备进入 AI Command Workspace' : '先写下一个想法，或从模板开始' }}</span>
        <button class="primary-button" :disabled="!workspace.taskInput.trim()" @click="startBuilding">开始构建</button>
      </div>
    </div>

    <section class="template-section">
      <div class="section-heading">
        <h2>从模板开始</h2>
        <span>选择一个创作起点</span>
      </div>
      <div class="template-grid">
        <button
          v-for="template in templates"
          :key="template.title"
          type="button"
          class="soft-card template-card"
          @click="useTemplate(template.prompt)"
        >
          <span class="badge">{{ template.category }}</span>
          <strong>{{ template.title }}</strong>
          <p>{{ template.description }}</p>
        </button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()
const inputRef = ref<HTMLTextAreaElement | null>(null)

const templates = [
  {
    title: '需求拆解 Flow',
    category: '产品',
    description: '把模糊想法整理为目标、用户场景、功能范围和下一步任务。',
    prompt: '请将这个产品想法拆解为目标用户、核心问题、MVP 功能、优先级和下一步执行任务：'
  },
  {
    title: 'PRD 总结 Flow',
    category: '文档',
    description: '从长文本中提炼背景、目标、范围、风险和验收标准。',
    prompt: '请将下面的 PRD 或需求描述整理为背景、目标、功能范围、风险和验收标准：'
  },
  {
    title: '代码审查 Flow',
    category: '工程',
    description: '聚焦质量、风险、边界情况和测试缺口。',
    prompt: '请审查下面的代码或技术方案，指出主要风险、可维护性问题、边界情况和测试建议：'
  },
  {
    title: '市场分析 Flow',
    category: '研究',
    description: '从一个方向出发，形成竞品、机会、用户和定位分析。',
    prompt: '请围绕下面的市场方向，分析目标用户、竞品格局、机会点、差异化定位和验证计划：'
  }
]

function startBuilding() {
  if (!workspace.taskInput.trim()) {
    return
  }
  router.push('/tasks')
}

async function useTemplate(prompt: string) {
  workspace.taskInput = prompt
  await nextTick()
  inputRef.value?.focus()
}
</script>
