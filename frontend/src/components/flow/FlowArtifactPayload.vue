<template>
  <div v-if="hasPayload" class="flow-artifact-payload">
    <div class="flow-artifact-payload-actions">
      <button
        type="button"
        title="下载原始产物"
        aria-label="下载原始产物"
        @click="downloadPayload"
      >
        <Download />
      </button>
    </div>
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
import { Download } from '@element-plus/icons-vue'
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

function downloadPayload() {
  if (!hasPayload.value || typeof props.detail.payload !== 'string') return
  const mediaType = props.detail.mediaType?.split(';')[0]?.trim().toLowerCase() || 'text/plain'
  const extension = mediaType === 'application/json' ? 'json' : mediaType === 'text/markdown' ? 'md' : 'txt'
  const blob = new Blob([props.detail.payload], { type: `${mediaType};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = createArtifactFilename(props.detail.artifactType, extension)
  link.hidden = true
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function createArtifactFilename(artifactType: string, extension: string) {
  const safeType = artifactType.replace(/[^a-z0-9-]/gi, '-').toLowerCase() || 'node-artifact'
  return `flowforge-${safeType}.${extension}`
}
</script>
