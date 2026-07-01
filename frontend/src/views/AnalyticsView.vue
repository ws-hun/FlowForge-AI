<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">洞察</p>
      <h1>安静地理解你的 AI 工作。</h1>
      <p>用轻量洞察理解工作流质量、Provider 状态和下一步创作方向。</p>
    </header>

    <div class="quiet-list">
      <article class="soft-card quiet-row">
        <strong>最近任务</strong>
        <span>{{ workspace.tasks.length }} 条执行记录</span>
      </article>
      <article class="soft-card quiet-row">
        <strong>当前 Provider</strong>
        <span>{{ workspace.activeProvider?.provider || '尚未配置' }}</span>
      </article>
      <article class="soft-card quiet-row">
        <strong>建议</strong>
        <span>先沉淀高频 Prompt，再将它们编排为 Flow。</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()
</script>
