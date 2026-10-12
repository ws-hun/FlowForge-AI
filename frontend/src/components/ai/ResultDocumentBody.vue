<template>
  <div class="document-body rendered-document" :class="{ 'rendered-document--unframed': unframed }">
    <template v-for="(block, index) in blocks" :key="`${block.type}-${index}`">
      <h4 v-if="block.type === 'heading'" :class="`level-${block.level}`">
        <ResultInlineText :text="block.content" />
      </h4>
      <p v-else-if="block.type === 'paragraph'"><ResultInlineText :text="block.content" /></p>
      <ul v-else-if="block.type === 'list' && !block.ordered" class="rendered-list">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <ResultInlineText :text="item" />
        </li>
      </ul>
      <ol v-else-if="block.type === 'list'" class="rendered-list">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <ResultInlineText :text="item" />
        </li>
      </ol>
      <div v-else-if="block.type === 'code'" class="rendered-code-wrap">
        <span v-if="block.language">{{ block.language }}</span>
        <pre class="rendered-code">{{ block.content }}</pre>
      </div>
      <blockquote v-else-if="block.type === 'quote'">
        <ResultInlineText :text="block.content" />
      </blockquote>
      <hr v-else-if="block.type === 'divider'" />
    </template>
  </div>
</template>

<script setup lang="ts">
import ResultInlineText from '@/components/ai/ResultInlineText.vue'
import type { ResultBlock } from '@/utils/resultMarkdown'

withDefaults(defineProps<{ blocks: ResultBlock[]; unframed?: boolean }>(), { unframed: false })
</script>
