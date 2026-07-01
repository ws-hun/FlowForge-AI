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
        <button class="primary-button" :disabled="!canSubmit" @click="submit">保存并激活</button>
      </section>

      <section class="provider-list">
        <article v-for="item in workspace.apiKeys" :key="item.id" class="soft-card provider-card">
          <div class="row-between">
            <strong>{{ item.provider }}</strong>
            <span class="badge">{{ item.active ? '已激活' : '备用' }}</span>
          </div>
          <p class="mono">{{ item.maskedKey }}</p>
          <p class="muted">{{ item.model }} · {{ item.baseUrl }}</p>
          <div class="row-between">
            <button class="secondary-button" :disabled="item.active" @click="workspace.activateProvider(item.id)">激活</button>
            <button class="danger-button" @click="workspace.removeProvider(item.id)">删除</button>
          </div>
        </article>
        <div v-if="!workspace.apiKeys.length" class="empty-state">暂无 Provider</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'
import type { Provider } from '@/types'

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

const canSubmit = computed(() => form.provider && form.apiKey && form.baseUrl && form.model)

function applyDefaults() {
  form.baseUrl = defaults[form.provider].baseUrl
  form.model = defaults[form.provider].model
}

async function submit() {
  await workspace.saveProvider({ ...form, active: true })
  form.apiKey = ''
}
</script>
