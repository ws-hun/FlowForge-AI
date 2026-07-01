<template>
  <header class="top-nav">
    <RouterLink to="/" class="top-brand">
      <span>F</span>
      <strong>FlowForge</strong>
    </RouterLink>

    <nav class="top-links">
      <RouterLink to="/">工作空间</RouterLink>
      <RouterLink to="/workflows">流程</RouterLink>
      <RouterLink to="/agents">智能体</RouterLink>
      <RouterLink to="/prompts">提示词库</RouterLink>
      <RouterLink to="/history">历史</RouterLink>
      <RouterLink to="/settings">设置</RouterLink>
    </nav>

    <div class="top-actions">
      <button class="search-pill">搜索</button>
      <RouterLink to="/profile" class="user-avatar">A</RouterLink>
    </div>
  </header>
</template>
