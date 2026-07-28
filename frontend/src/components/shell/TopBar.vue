<template>
  <header class="top-nav">
    <RouterLink to="/" class="top-brand">
      <img :src="logo" alt="FlowForge AI" class="brand-logo" />
    </RouterLink>

    <nav class="top-links">
      <RouterLink to="/">工作空间</RouterLink>
      <RouterLink to="/workflows">流程</RouterLink>
      <RouterLink to="/agents">智能体</RouterLink>
      <RouterLink to="/prompts">提示词库</RouterLink>
      <RouterLink to="/history">历史</RouterLink>
      <RouterLink to="/settings">设置</RouterLink>
      <button
        type="button"
        class="top-mobile-search"
        title="搜索"
        aria-label="打开全局搜索"
        @click="searchOpen = true"
      >
        <Search />
      </button>
    </nav>

    <div class="top-actions">
      <button type="button" class="search-pill" title="搜索" aria-label="打开全局搜索" @click="searchOpen = true">
        <Search />
      </button>
      <RouterLink to="/profile" class="user-avatar" :title="workspace.profileName">{{ workspace.profileInitial }}</RouterLink>
    </div>

    <GlobalSearchDialog :open="searchOpen" @close="searchOpen = false" />
  </header>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import GlobalSearchDialog from '@/components/shell/GlobalSearchDialog.vue'
import logo from '@/assets/icons/logo.png'
import { useWorkspaceStore } from '@/stores/workspace'

const searchOpen = ref(false)
const workspace = useWorkspaceStore()

function handleSearchShortcut(event: KeyboardEvent) {
  const target = event.target
  const typing = target instanceof HTMLElement && target.matches('input, textarea, select, [contenteditable="true"]')
  const commandSearch = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k'
  const slashSearch = event.key === '/' && !typing && !event.metaKey && !event.ctrlKey && !event.altKey
  if (!commandSearch && !slashSearch) {
    return
  }
  event.preventDefault()
  searchOpen.value = true
}

onMounted(() => window.addEventListener('keydown', handleSearchShortcut))
onBeforeUnmount(() => window.removeEventListener('keydown', handleSearchShortcut))
</script>
