import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  activateApiKey,
  deleteApiKey,
  listApiKeys,
  listTasks,
  runTask,
  saveApiKey
} from '@/api/tasks'
import type { ApiKeyConfig, SaveApiKeyPayload, TaskHistoryItem, TaskRunResponse } from '@/types'

export const useWorkspaceStore = defineStore('workspace', () => {
  const tasks = ref<TaskHistoryItem[]>([])
  const apiKeys = ref<ApiKeyConfig[]>([])
  const latestResult = ref<TaskRunResponse | null>(null)
  const taskInput = ref('')
  const taskSourcePromptId = ref<string | null>(null)
  const taskSourcePromptTitle = ref('')
  const running = ref(false)
  const historyLoading = ref(false)
  const settingsLoading = ref(false)

  const activeProvider = computed(() => apiKeys.value.find((item) => item.active))

  async function loadTasks() {
    historyLoading.value = true
    try {
      const { data } = await listTasks()
      tasks.value = data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '历史记录加载失败')
    } finally {
      historyLoading.value = false
    }
  }

  async function loadApiKeys() {
    settingsLoading.value = true
    try {
      const { data } = await listApiKeys()
      apiKeys.value = data
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥加载失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function executeTask() {
    if (!taskInput.value.trim()) {
      return
    }

    running.value = true
    try {
      const { data } = await runTask(taskInput.value.trim(), taskSourcePromptId.value)
      latestResult.value = data
      taskInput.value = ''
      taskSourcePromptId.value = null
      taskSourcePromptTitle.value = ''
      ElMessage.success('任务执行完成')
      await loadTasks()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '任务执行失败')
    } finally {
      running.value = false
    }
  }

  function prepareTask(input: string, sourcePrompt?: { id: string; title: string } | null) {
    taskInput.value = input
    taskSourcePromptId.value = sourcePrompt?.id || null
    taskSourcePromptTitle.value = sourcePrompt?.title || ''
  }

  function clearTaskSource() {
    taskSourcePromptId.value = null
    taskSourcePromptTitle.value = ''
  }

  async function saveProvider(payload: SaveApiKeyPayload) {
    settingsLoading.value = true
    try {
      await saveApiKey(payload)
      ElMessage.success('API 密钥已保存')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥保存失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function activateProvider(id: string) {
    settingsLoading.value = true
    try {
      await activateApiKey(id)
      ElMessage.success('Provider 已激活')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'Provider 激活失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function removeProvider(id: string) {
    settingsLoading.value = true
    try {
      await deleteApiKey(id)
      ElMessage.success('API 密钥已删除')
      await loadApiKeys()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || 'API 密钥删除失败')
    } finally {
      settingsLoading.value = false
    }
  }

  async function bootstrap() {
    await Promise.all([loadTasks(), loadApiKeys()])
  }

  return {
    tasks,
    apiKeys,
    latestResult,
    taskInput,
    taskSourcePromptId,
    taskSourcePromptTitle,
    running,
    historyLoading,
    settingsLoading,
    activeProvider,
    bootstrap,
    loadTasks,
    loadApiKeys,
    executeTask,
    prepareTask,
    clearTaskSource,
    saveProvider,
    activateProvider,
    removeProvider
  }
})
