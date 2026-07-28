<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">API Key</p>
      <h1>Provider 密钥空间</h1>
      <p>在界面里添加和激活 API Key。密钥只提交，不明文回显。</p>
    </header>

    <div class="two-column">
      <section class="surface provider-editor">
        <label>
          <span>Provider</span>
          <select v-model="form.provider" class="quiet-select" @change="applyDefaults">
            <option value="deepseek">DeepSeek</option>
            <option value="openai">OpenAI</option>
          </select>
        </label>
        <label>
          <span>模型</span>
          <input v-model="form.model" class="quiet-input" />
        </label>
        <label>
          <span>Base URL</span>
          <input v-model="form.baseUrl" class="quiet-input" />
        </label>
        <label>
          <span>API Key</span>
          <input v-model="form.apiKey" type="password" class="quiet-input mono" placeholder="粘贴 API Key" />
        </label>
        <button class="primary-button" :disabled="!canSubmit || workspace.settingsLoading" @click="submit">
          {{ workspace.settingsLoading ? '保存中...' : '保存并激活' }}
        </button>
      </section>

      <section class="provider-list">
        <article v-for="item in workspace.apiKeys" :key="item.id" class="soft-card provider-card">
          <div class="row-between">
            <strong>{{ item.provider }}</strong>
            <span class="badge">{{ item.active ? '已激活' : '备用' }}</span>
          </div>
          <p class="mono">{{ item.maskedKey }}</p>
          <p class="muted">{{ item.model }} · {{ item.baseUrl }}</p>
          <small class="provider-updated-at">更新于 {{ formatDate(item.updatedAt) }}</small>
          <div class="row-between">
            <button
              class="secondary-button"
              :disabled="item.active || workspace.settingsLoading"
              @click="activate(item.id)"
            >
              {{ pendingProviderId === item.id && pendingAction === 'activate' ? '激活中...' : '激活' }}
            </button>
            <button
              class="danger-button"
              :disabled="workspace.settingsLoading"
              @click="confirmRemove(item)"
            >
              {{ pendingProviderId === item.id && pendingAction === 'remove' ? '删除中...' : '删除' }}
            </button>
          </div>
        </article>
        <div v-if="!workspace.apiKeys.length" class="empty-state">暂无 Provider</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useWorkspaceStore } from '@/stores/workspace'
import type { ApiKeyConfig, Provider } from '@/types'

const workspace = useWorkspaceStore()
const defaults = {
  deepseek: { baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  openai: { baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini' }
}

const form = reactive({
  provider: 'deepseek' as Provider,
  apiKey: '',
  baseUrl: defaults.deepseek.baseUrl,
  model: defaults.deepseek.model
})
const pendingProviderId = ref('')
const pendingAction = ref<'activate' | 'remove' | ''>('')

const canSubmit = computed(() => form.provider && form.apiKey && form.baseUrl && form.model)

function applyDefaults() {
  form.baseUrl = defaults[form.provider].baseUrl
  form.model = defaults[form.provider].model
}

async function submit() {
  if (await workspace.saveProvider({ ...form, active: true })) {
    form.apiKey = ''
  }
}

async function activate(id: string) {
  pendingProviderId.value = id
  pendingAction.value = 'activate'
  try {
    await workspace.activateProvider(id)
  } finally {
    pendingProviderId.value = ''
    pendingAction.value = ''
  }
}

async function confirmRemove(item: ApiKeyConfig) {
  try {
    await ElMessageBox.confirm(
      item.active
        ? `删除当前激活的 ${item.provider} 后，AI Command 和 Flow 将暂停执行。`
        : `删除 ${item.provider} 配置后，需要重新填写完整 API Key 才能恢复。`,
      '删除 Provider',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  pendingProviderId.value = item.id
  pendingAction.value = 'remove'
  try {
    await workspace.removeProvider(item.id)
  } finally {
    pendingProviderId.value = ''
    pendingAction.value = ''
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}
</script>
