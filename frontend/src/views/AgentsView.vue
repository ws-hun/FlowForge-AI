<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">智能体</p>
      <h1>为不同工作方式准备智能体。</h1>
      <p>智能体是你工作空间里的专业协作者，帮助你把重复工作交给稳定的能力单元。</p>
    </header>

    <div class="gallery-grid">
      <article v-for="agent in agents" :key="agent.name" class="soft-card prompt-tile">
        <span class="badge">Agent</span>
        <strong>{{ agent.name }}</strong>
        <p>{{ agent.desc }}</p>
        <div class="tag-cloud">
          <span v-for="tag in agent.tags" :key="tag">{{ tag }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
const agents = [
  { name: '产品分析 Agent', desc: '分析需求、拆解任务、生成 PRD 摘要。', tags: ['产品', '研究'] },
  { name: '代码审查 Agent', desc: '聚焦质量、风险和测试缺口。', tags: ['代码', '评审'] },
  { name: '数据洞察 Agent', desc: '解释任务历史和使用趋势。', tags: ['洞察', '协作'] }
]
</script>
