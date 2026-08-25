import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getAuthStatus,
  login as loginRequest,
  logout as logoutRequest,
  setupWorkspaceOwner
} from '@/api/auth'
import type { AuthCredentials, AuthSetupPayload, AuthUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  let bootstrapPromise: Promise<boolean> | null = null
  const ready = ref(false)
  const loading = ref(false)
  const setupRequired = ref(false)
  const user = ref<AuthUser | null>(null)
  const connectionError = ref('')

  const authenticated = computed(() => Boolean(user.value))
  const userInitial = computed(() => Array.from(user.value?.displayName.trim() || '')[0]?.toUpperCase() || 'A')

  async function bootstrap(force = false) {
    if (ready.value && !force) {
      return authenticated.value
    }
    if (!bootstrapPromise) {
      bootstrapPromise = refreshStatus().finally(() => {
        bootstrapPromise = null
      })
    }
    return bootstrapPromise
  }

  async function refreshStatus() {
    loading.value = true
    connectionError.value = ''
    try {
      const { data } = await getAuthStatus()
      applyStatus(data)
      return data.authenticated
    } catch (error: any) {
      user.value = null
      setupRequired.value = false
      connectionError.value = error.response?.data?.message || '无法连接 FlowForge 服务'
      return false
    } finally {
      ready.value = true
      loading.value = false
    }
  }

  async function login(payload: AuthCredentials) {
    loading.value = true
    connectionError.value = ''
    try {
      const { data } = await loginRequest(payload)
      applyStatus(data)
      return { ok: true as const, message: '' }
    } catch (error: any) {
      return {
        ok: false as const,
        message: error.response?.data?.message || '登录失败，请稍后重试'
      }
    } finally {
      ready.value = true
      loading.value = false
    }
  }

  async function setup(payload: AuthSetupPayload) {
    loading.value = true
    connectionError.value = ''
    try {
      const { data } = await setupWorkspaceOwner(payload)
      applyStatus(data)
      return { ok: true as const, message: '' }
    } catch (error: any) {
      if (error.response?.status === 409) {
        await refreshStatus()
      }
      return {
        ok: false as const,
        message: error.response?.data?.message || '工作区所有者创建失败'
      }
    } finally {
      ready.value = true
      loading.value = false
    }
  }

  async function logout() {
    loading.value = true
    try {
      await logoutRequest()
    } finally {
      user.value = null
      setupRequired.value = false
      ready.value = true
      loading.value = false
    }
  }

  function applyStatus(status: { setupRequired: boolean; authenticated: boolean; user?: AuthUser | null }) {
    setupRequired.value = status.setupRequired
    user.value = status.authenticated ? status.user || null : null
  }

  return {
    ready,
    loading,
    setupRequired,
    user,
    connectionError,
    authenticated,
    userInitial,
    bootstrap,
    login,
    setup,
    logout
  }
})
