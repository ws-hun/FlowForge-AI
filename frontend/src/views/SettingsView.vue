<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">设置</p>
      <h1>让工作空间保持简单。</h1>
      <p>调整当前浏览器中的工作区身份，检查 Provider，并确认已支持的界面偏好。</p>
    </header>

    <div class="settings-layout">
      <aside class="settings-nav surface" aria-label="设置分类">
        <button
          v-for="section in sections"
          :key="section.id"
          type="button"
          :class="{ active: activeSection === section.id }"
          @click="activeSection = section.id"
        >
          <component :is="section.icon" />
          <span>{{ section.label }}</span>
        </button>
      </aside>

      <section class="settings-detail surface">
        <template v-if="activeSection === 'workspace'">
          <div class="settings-section-heading">
            <span class="section-kicker">Workspace Identity</span>
            <h2>当前工作空间</h2>
            <p>这些名称保存在当前浏览器，用于保持本地创作空间的一致身份。</p>
          </div>
          <div class="settings-form">
            <label>
              <span>工作区名称</span>
              <input v-model="draft.workspaceName" class="quiet-input" maxlength="80" />
            </label>
            <label>
              <span>个人显示名</span>
              <input v-model="draft.profileName" class="quiet-input" maxlength="80" />
            </label>
          </div>
          <div class="settings-save-row">
            <span>{{ preferencesChanged ? '有尚未保存的本地修改' : '本地偏好已保存' }}</span>
            <button type="button" class="primary-button" :disabled="!canSavePreferences" @click="savePreferences">
              保存偏好
            </button>
          </div>
        </template>

        <template v-else-if="activeSection === 'provider'">
          <div class="settings-section-heading">
            <span class="section-kicker">AI Provider</span>
            <h2>模型连接</h2>
            <p>FlowForge 使用当前激活的 Provider 执行 AI Command 和 Flow。</p>
          </div>
          <div class="setting-block provider-setting-block">
            <div>
              <h3>{{ workspace.activeProvider?.provider || '尚未配置 Provider' }}</h3>
              <p v-if="workspace.activeProvider">
                {{ workspace.activeProvider.model }} · {{ workspace.activeProvider.maskedKey }}
              </p>
              <p v-else>添加 DeepSeek 或 OpenAI API Key 后即可执行真实任务。</p>
            </div>
            <RouterLink to="/api-keys" class="secondary-button">管理 Provider</RouterLink>
          </div>
        </template>

        <template v-else>
          <div class="settings-section-heading">
            <span class="section-kicker">Appearance</span>
            <h2>界面偏好</h2>
            <p>当前版本以浅色、低噪音工作空间为唯一正式主题。</p>
          </div>
          <div class="appearance-choice" aria-label="当前界面主题">
            <span class="appearance-preview">
              <i></i>
              <i></i>
              <i></i>
            </span>
            <span>
              <strong>Calm Light</strong>
              <small>暖白背景、柔和边框和克制的蓝色主操作。</small>
            </span>
            <em>当前</em>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { Component } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Setting, Sunny } from '@element-plus/icons-vue'
import { useWorkspaceStore } from '@/stores/workspace'

type SettingsSection = 'workspace' | 'provider' | 'appearance'

const workspace = useWorkspaceStore()
const activeSection = ref<SettingsSection>('workspace')
const sections: Array<{ id: SettingsSection; label: string; icon: Component }> = [
  { id: 'workspace', label: '工作区', icon: Setting },
  { id: 'provider', label: 'Provider', icon: Key },
  { id: 'appearance', label: '界面', icon: Sunny }
]
const draft = reactive({
  workspaceName: workspace.workspaceName,
  profileName: workspace.profileName
})
const preferencesChanged = computed(() =>
  draft.workspaceName.trim() !== workspace.workspaceName || draft.profileName.trim() !== workspace.profileName
)
const canSavePreferences = computed(() =>
  Boolean(preferencesChanged.value && draft.workspaceName.trim() && draft.profileName.trim())
)

watch(
  [() => workspace.workspaceName, () => workspace.profileName],
  ([workspaceName, profileName]) => {
    if (!preferencesChanged.value) {
      draft.workspaceName = workspaceName
      draft.profileName = profileName
    }
  }
)

function savePreferences() {
  if (!workspace.updateWorkspacePreferences(draft)) {
    ElMessage.warning('工作区名称和个人显示名不能为空')
    return
  }
  draft.workspaceName = workspace.workspaceName
  draft.profileName = workspace.profileName
  ElMessage.success('本地工作区偏好已保存')
}
</script>
