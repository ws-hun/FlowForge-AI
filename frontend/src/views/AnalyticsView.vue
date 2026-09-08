<template>
  <FutureWorkspaceBoundary
    kicker="洞察"
    title="先保留可解释运行，再形成洞察。"
    description="独立分析工作区尚未接入。当前版本优先保证每次执行的输入、来源、结果和节点轨迹可信。"
    next-step-title="从真实运行历史开始"
    next-step-description="历史已支持搜索、来源范围、失败恢复、结果对比和不可变 Flow 运行轨迹。"
    primary-label="打开历史"
    primary-to="/history"
    secondary-label="继续创建 Flow"
    secondary-to="/workflows"
  />
</template>

<script setup lang="ts">
import FutureWorkspaceBoundary from '@/components/workspace/FutureWorkspaceBoundary.vue'
</script>
