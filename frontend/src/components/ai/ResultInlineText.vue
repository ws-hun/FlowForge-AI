<template>
  <template v-for="(segment, index) in segments" :key="index">
    <strong v-if="segment.type === 'strong'" class="result-inline-strong">{{ segment.content }}</strong>
    <code v-else-if="segment.type === 'code'" class="result-inline-code">{{ segment.content }}</code>
    <em v-else-if="segment.type === 'emphasis'">{{ segment.content }}</em>
    <a
      v-else-if="segment.type === 'link'"
      :href="segment.href"
      :target="segment.href?.startsWith('mailto:') ? undefined : '_blank'"
      rel="noopener noreferrer"
    >{{ segment.content }}</a>
    <span v-else>{{ segment.content }}</span>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { parseInlineMarkdown } from '@/utils/resultMarkdown'

const props = defineProps<{
  text: string
}>()

const segments = computed(() => parseInlineMarkdown(props.text))
</script>
