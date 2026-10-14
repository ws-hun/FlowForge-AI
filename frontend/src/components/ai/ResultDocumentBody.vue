<template>
  <div class="document-body rendered-document" :class="{ 'rendered-document--unframed': unframed }">
    <template v-for="(block, index) in blocks" :key="`${block.type}-${index}`">
      <component v-if="block.type === 'heading'" :is="headingTag(block.level)" :class="`level-${block.level}`">
        <ResultInlineText :text="block.content" />
      </component>
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
      <ul v-else-if="block.type === 'checklist'" class="rendered-checklist">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <input
            type="checkbox"
            :checked="item.checked"
            disabled
            :aria-labelledby="`${checklistId}-${index}-${itemIndex}`"
          />
          <span :id="`${checklistId}-${index}-${itemIndex}`"><ResultInlineText :text="item.content" /></span>
        </li>
      </ul>
      <div v-else-if="block.type === 'table'" class="rendered-table-scroll" role="region" tabindex="0" aria-label="结果表格">
        <table class="rendered-table">
          <thead>
            <tr>
              <th v-for="(header, column) in block.headers" :key="column" scope="col" :style="{ textAlign: block.alignments[column] }">
                <ResultInlineText :text="header" />
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
              <td v-for="(cell, column) in row" :key="column" :style="{ textAlign: block.alignments[column] }">
                <ResultInlineText :text="cell" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
import { useId } from 'vue'
import ResultInlineText from '@/components/ai/ResultInlineText.vue'
import type { ResultBlock } from '@/utils/resultMarkdown'

withDefaults(defineProps<{ blocks: ResultBlock[]; unframed?: boolean }>(), { unframed: false })

const checklistId = useId()

function headingTag(level: number) {
  return `h${Math.max(1, Math.min(4, level))}`
}
</script>
