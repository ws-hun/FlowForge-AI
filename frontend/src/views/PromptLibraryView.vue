<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">提示词库</p>
      <h1>可复用的提示词空间。</h1>
      <p>像 Notion Gallery 一样组织团队 Prompt，收藏、分类、搜索都保持轻量。</p>
    </header>

    <div class="library-toolbar">
      <input class="quiet-input" placeholder="搜索提示词..." />
      <button class="secondary-button">全部分类</button>
      <button class="ghost-button">仅收藏</button>
    </div>

    <div class="gallery-grid">
      <article v-for="prompt in prompts" :key="prompt.title" class="soft-card prompt-tile">
        <div class="row-between">
          <span class="badge">{{ prompt.category }}</span>
          <button class="ghost-button">★</button>
        </div>
        <strong>{{ prompt.title }}</strong>
        <p>{{ prompt.desc }}</p>
        <div class="tag-cloud">
          <span v-for="tag in prompt.tags" :key="tag">#{{ tag }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
const prompts = [
  { title: '需求拆解', category: '产品', desc: '将复杂需求拆分为可执行任务和优先级。', tags: ['产品', '规划'] },
  { title: '接口设计', category: '工程', desc: '根据目标生成 REST API 设计草案。', tags: ['API', '后端'] },
  { title: '风险评审', category: '评审', desc: '识别方案中的依赖、风险和测试缺口。', tags: ['质量', '评审'] },
  { title: '会议总结', category: '协作', desc: '把会议记录整理成结论和行动项。', tags: ['总结', '团队'] }
]
</script>
