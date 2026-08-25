<template>
  <header class="top-nav">
    <RouterLink to="/" class="top-brand">
      <img :src="logo" alt="FlowForge AI" class="brand-logo" />
    </RouterLink>

    <nav class="top-links">
      <RouterLink to="/">工作空间</RouterLink>
      <RouterLink to="/workflows">流程</RouterLink>
      <RouterLink to="/prompts">提示词库</RouterLink>
      <RouterLink to="/history">历史</RouterLink>
      <RouterLink to="/settings">设置</RouterLink>
      <RouterLink
        to="/api-keys"
        class="workspace-status top-mobile-status"
        :class="systemStatus"
        :title="systemStatusTitle"
      >
        <i></i>
        <span>{{ systemStatusLabel }}</span>
      </RouterLink>
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
      <RouterLink to="/api-keys" class="workspace-status" :class="systemStatus" :title="systemStatusTitle">
        <i></i>
        <span>{{ systemStatusLabel }}</span>
      </RouterLink>
      <button type="button" class="search-pill" title="搜索" aria-label="打开全局搜索" @click="searchOpen = true">
        <Search />
      </button>
      <div ref="userMenuRef" class="top-user-menu">
        <button
          type="button"
          class="user-avatar"
          :title="auth.user?.displayName"
          :aria-expanded="userMenuOpen"
          aria-haspopup="menu"
          @click="userMenuOpen = !userMenuOpen"
        >
          {{ auth.userInitial }}
        </button>
        <Transition name="menu-fade">
          <div v-if="userMenuOpen" class="user-popover" role="menu">
            <div class="user-popover-identity">
              <strong>{{ auth.user?.displayName }}</strong>
              <small>{{ auth.user?.email }}</small>
            </div>
            <RouterLink to="/profile" role="menuitem" @click="userMenuOpen = false">
              <User />
              <span>个人空间</span>
            </RouterLink>
            <button type="button" role="menuitem" :disabled="auth.loading" @click="signOut">
              <SwitchButton />
              <span>退出登录</span>
            </button>
          </div>
        </Transition>
      </div>
    </div>

    <GlobalSearchDialog :open="searchOpen" @close="searchOpen = false" />
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Search, SwitchButton, User } from '@element-plus/icons-vue'
import GlobalSearchDialog from '@/components/shell/GlobalSearchDialog.vue'
import logo from '@/assets/icons/logo.png'
import { getHealth } from '@/api/system'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAuthStore } from '@/stores/auth'

const searchOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
const workspace = useWorkspaceStore()
const auth = useAuthStore()
const healthState = ref<'checking' | 'ready' | 'offline'>('checking')
let healthTimer: number | null = null
const systemStatus = computed(() => {
  if (healthState.value === 'offline') return 'offline'
  if (healthState.value === 'checking') return 'checking'
  return workspace.activeProvider ? 'ready' : 'provider'
})
const systemStatusLabel = computed(() => {
  const labels = {
    checking: 'Checking',
    ready: 'Ready',
    provider: 'Provider',
    offline: 'Offline'
  }
  return labels[systemStatus.value]
})
const systemStatusTitle = computed(() => {
  if (systemStatus.value === 'ready') return '应用、数据库和 AI Provider 已就绪'
  if (systemStatus.value === 'provider') return '应用已就绪，请配置或激活 AI Provider'
  if (systemStatus.value === 'offline') return '后端或数据库当前不可用'
  return '正在检查应用状态'
})

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

async function refreshHealth() {
  if (!navigator.onLine) {
    healthState.value = 'offline'
    return
  }
  try {
    const { data } = await getHealth()
    healthState.value = data.status === 'up' && data.database === 'reachable' ? 'ready' : 'offline'
  } catch {
    healthState.value = 'offline'
  }
}

function handleOffline() {
  healthState.value = 'offline'
}

function handleOutsideClick(event: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(event.target as Node)) {
    userMenuOpen.value = false
  }
}

async function signOut() {
  await auth.logout()
  window.location.assign('/auth')
}

onMounted(() => {
  window.addEventListener('keydown', handleSearchShortcut)
  window.addEventListener('online', refreshHealth)
  window.addEventListener('offline', handleOffline)
  document.addEventListener('click', handleOutsideClick)
  void refreshHealth()
  healthTimer = window.setInterval(refreshHealth, 30_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleSearchShortcut)
  window.removeEventListener('online', refreshHealth)
  window.removeEventListener('offline', handleOffline)
  document.removeEventListener('click', handleOutsideClick)
  if (healthTimer !== null) {
    window.clearInterval(healthTimer)
  }
})
</script>
