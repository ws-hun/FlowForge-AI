<template>
  <div v-if="hasPayload" class="flow-artifact-payload">
    <template v-if="isDocument">
      <ResultDocumentBody :blocks="blocks" unframed />
      <details :key="detail.id" class="flow-artifact-raw">
        <summary>原始产物</summary>
        <pre class="flow-artifact-text">{{ detail.payload }}</pre>
      </details>
    </template>
    <pre v-else class="flow-artifact-text">{{ detail.payload }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ResultDocumentBody from '@/components/ai/ResultDocumentBody.vue'
import type { FlowNodeArtifactDetail } from '@/types'
import { resolveResultDocument } from '@/utils/resultDocument'
import { parseResultMarkdown } from '@/utils/resultMarkdown'

const props = defineProps<{ detail: FlowNodeArtifactDetail }>()
const hasPayload = computed(() => props.detail.state === 'materialized' && typeof props.detail.payload === 'string')
const isDocument = computed(() => {
  const mediaType = props.detail.mediaType?.split(';')[0]?.trim().toLowerCase()
  return (props.detail.artifactType === 'provider-result' || props.detail.artifactType === 'result-document')
    && (mediaType === 'text/markdown' || mediaType === 'application/json')
})
const blocks = computed(() => isDocument.value && hasPayload.value
  ? parseResultMarkdown(resolveResultDocument(props.detail.payload || '').markdown)
  : [])
</script>
