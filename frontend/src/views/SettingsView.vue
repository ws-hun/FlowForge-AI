<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">设置</p>
      <h1>简单、清晰的工作区设置。</h1>
      <p>Apple 风格的设置分区，聚焦 Provider、安全和工作区偏好。</p>
    </header>

    <div class="settings-layout">
      <aside class="settings-nav surface">
        <a v-for="item in sections" :key="item">{{ item }}</a>
      </aside>

      <section class="settings-detail surface">
        <div class="setting-block">
          <div>
            <h2>Provider</h2>
            <p>添加 DeepSeek 或 OpenAI 密钥。密钥保存在数据库中，不写入配置文件。</p>
          </div>
          <RouterLink to="/api-keys" class="secondary-button">管理</RouterLink>
        </div>

        <div class="setting-block">
          <div>
            <h2>默认工作区</h2>
            <p>FlowForge Workspace</p>
          </div>
          <button class="ghost-button">编辑</button>
        </div>

        <div class="setting-block">
          <div>
            <h2>界面偏好</h2>
            <p>浅色、安静、低信息密度。</p>
          </div>
          <span class="badge">浅色</span>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
const sections = ['工作区', 'Provider', '安全', '偏好']
</script>
