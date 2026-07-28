<template>
  <FutureWorkspaceBoundary
    kicker="Agents"
    title="先沉淀工作方式，再交给智能体。"
    description="Agent Runtime 尚未接入。FlowForge 当前只展示真实可执行能力，不创建虚假的智能体状态。"
    next-step-title="从可复用 Flow 开始"
    next-step-description="先把目标、Prompt、执行指令和输出标准编排为稳定 Flow。未来 Agent 将建立在这些可追溯资产之上。"
    primary-label="打开 Flow Space"
    primary-to="/workflows"
    secondary-label="整理 Prompt"
    secondary-to="/prompts"
  />
</template>

<script setup lang="ts">
import FutureWorkspaceBoundary from '@/components/workspace/FutureWorkspaceBoundary.vue'
</script>
