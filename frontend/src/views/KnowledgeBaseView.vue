<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">知识库</p>
      <h1>让工作流拥有上下文。</h1>
      <p>知识库用于承载文档、规则和团队语境。当前版本保留轻量结构。</p>
    </header>

    <div class="quiet-list">
      <article v-for="source in sources" :key="source" class="soft-card quiet-row">
        <strong>{{ source }}</strong>
        <span>已准备同步，未来可接入检索和向量化状态。</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
const sources = ['产品需求文档', 'API 设计规范', '团队知识库', '研发周报']
</script>
