<template>
  <section class="workspace-home">
    <div class="home-hero">
      <p class="page-kicker">FlowForge</p>
      <h1 class="page-title">The Calm AI Workspace</h1>
      <p class="page-subtitle">构建、执行和管理 AI 工作流。把想法写下来，FlowForge 会帮助你从任务开始，逐步形成可运行的 AI Flow。</p>
    </div>

    <div class="create-composer">
      <textarea
        v-model="workspace.taskInput"
        class="workspace-input"
        placeholder="描述你想创建或执行的 AI 工作..."
      ></textarea>
      <div class="composer-footer">
        <span>支持任务执行、工作流草稿、Prompt 生成</span>
        <button class="primary-button" @click="startBuilding">开始构建</button>
      </div>
    </div>

    <div class="workspace-sections">
      <section>
        <div class="section-heading">
          <h2>继续工作</h2>
          <span>最近入口</span>
        </div>
        <div class="quiet-list">
          <RouterLink v-for="item in continueItems" :key="item.title" :to="item.to" class="soft-card quiet-row">
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </RouterLink>
        </div>
      </section>

      <section>
        <div class="section-heading">
          <h2>模板</h2>
          <span>创作起点</span>
        </div>
        <div class="workspace-grid">
          <article v-for="template in templates" :key="template" class="soft-card template-card">
            <span class="badge">模板</span>
            <strong>{{ template }}</strong>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()

const continueItems = [
  { title: '运行一个 AI 任务', desc: '进入命令工作区，执行一次结构化 AI 任务。', to: '/tasks' },
  { title: '配置 Provider', desc: '在设置中添加 DeepSeek 或 OpenAI API Key。', to: '/settings' },
  { title: '查看历史记录', desc: '回顾最近的任务执行上下文。', to: '/history' }
]

const templates = ['需求拆解 Flow', 'PRD 总结 Flow', '代码审查 Flow', '市场分析 Flow']

function startBuilding() {
  router.push('/tasks')
}
</script>
