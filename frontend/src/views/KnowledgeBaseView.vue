<template>
  <FutureWorkspaceBoundary
    kicker="Knowledge"
    title="上下文必须真实进入执行。"
    description="独立 Knowledge Runtime 尚未接入。当前版本不会把静态文档名称伪装成已经索引的知识来源。"
    next-step-title="先使用 Flow Context"
    next-step-description="在 Flow 中添加 Context 节点，把背景、规则和已有材料固定到服务端执行输入。"
    primary-label="为 Flow 添加 Context"
    primary-to="/workflows"
    secondary-label="打开 Prompt Library"
    secondary-to="/prompts"
  />
</template>

<script setup lang="ts">
import FutureWorkspaceBoundary from '@/components/workspace/FutureWorkspaceBoundary.vue'
</script>
