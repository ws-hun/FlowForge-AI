<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">个人空间</p>
      <h1>{{ auth.user?.displayName }}</h1>
      <p>当前工作区所有者身份与本地创作偏好。</p>
    </header>

    <section class="surface profile-card">
      <div class="profile-avatar">{{ auth.userInitial }}</div>
      <div class="profile-identity">
        <span class="section-kicker">Workspace Owner</span>
        <h2>{{ auth.user?.displayName }}</h2>
        <p>{{ auth.user?.email }}</p>
        <small>{{ workspace.workspaceName }} · 当前浏览器偏好</small>
      </div>
      <RouterLink to="/settings" class="secondary-button">编辑偏好</RouterLink>
    </section>
  </section>
</template>

<script setup lang="ts">
import { useWorkspaceStore } from '@/stores/workspace'
import { useAuthStore } from '@/stores/auth'

const workspace = useWorkspaceStore()
const auth = useAuthStore()
</script>
