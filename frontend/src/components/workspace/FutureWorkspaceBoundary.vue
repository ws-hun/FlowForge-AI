<template>
  <section class="future-workspace-boundary">
    <header class="quiet-header">
      <p class="page-kicker">{{ kicker }}</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </header>

    <div class="future-workspace-next-step">
      <span class="future-workspace-status"><i></i> Future workspace</span>
      <h2>{{ nextStepTitle }}</h2>
      <p>{{ nextStepDescription }}</p>
      <div class="future-workspace-actions">
        <RouterLink :to="primaryTo" class="primary-button">{{ primaryLabel }}</RouterLink>
        <RouterLink v-if="secondaryTo && secondaryLabel" :to="secondaryTo" class="ghost-button">
          {{ secondaryLabel }}
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

defineProps<{
  kicker: string
  title: string
  description: string
  nextStepTitle: string
  nextStepDescription: string
  primaryLabel: string
  primaryTo: RouteLocationRaw
  secondaryLabel?: string
  secondaryTo?: RouteLocationRaw
}>()
</script>
