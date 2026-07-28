<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">个人空间</p>
      <h1>{{ workspace.profileName }}</h1>
      <p>当前本地创作身份与工作区信息。</p>
    </header>

    <section class="surface profile-card">
      <div class="profile-avatar">{{ workspace.profileInitial }}</div>
      <div class="profile-identity">
        <span class="section-kicker">Local Profile</span>
        <h2>{{ workspace.profileName }}</h2>
        <p>{{ workspace.workspaceName }}</p>
      </div>
      <RouterLink to="/settings" class="secondary-button">编辑偏好</RouterLink>
    </section>
  </section>
</template>

<script setup lang="ts">
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()
</script>
