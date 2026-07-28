<template>
  <FutureWorkspaceBoundary
    kicker="Insights"
    title="先保留可解释运行，再形成洞察。"
    description="独立 Analytics Workspace 尚未接入。当前版本优先保证每次执行的输入、来源、结果和节点轨迹可信。"
    next-step-title="从真实运行历史开始"
    next-step-description="History 已支持搜索、来源范围、失败恢复、结果对比和不可变 Flow Run Trace。"
    primary-label="打开 History"
    primary-to="/history"
    secondary-label="继续创建 Flow"
    secondary-to="/workflows"
  />
</template>

<script setup lang="ts">
import FutureWorkspaceBoundary from '@/components/workspace/FutureWorkspaceBoundary.vue'
</script>
