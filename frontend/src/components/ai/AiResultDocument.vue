<template>
  <div class="result-doc surface" :class="{ compact }">
    <div class="result-doc-header">
      <div class="result-doc-meta">
        <span class="badge">AI Result</span>
        <div class="result-doc-meta-end">
          <span v-if="providerLabel || model || tokenUsageLabel || durationLabel" class="execution-source">
            <strong v-if="providerLabel">{{ providerLabel }}</strong>
            <code v-if="model">{{ model }}</code>
            <span v-if="tokenUsageLabel" :title="tokenUsageDetail">{{ tokenUsageLabel }}</span>
            <span v-if="durationLabel">{{ durationLabel }}</span>
          </span>
          <div class="result-doc-actions" role="group" aria-label="结果文档操作">
            <button
              type="button"
              class="icon-button"
              title="复制 Markdown"
              aria-label="复制结果 Markdown"
              @click="copyResult"
            >
              <el-icon><CopyDocument /></el-icon>
            </button>
            <button
              type="button"
              class="icon-button"
              title="下载 Markdown"
              aria-label="下载结果 Markdown"
              @click="downloadResult"
            >
              <el-icon><Download /></el-icon>
            </button>
          </div>
        </div>
      </div>
      <p class="page-kicker">摘要</p>
      <h2>{{ summary }}</h2>
    </div>

    <div class="doc-section">
      <h3>关键要点</h3>
      <ul class="key-point-list">
        <li v-for="point in keyPoints" :key="point">{{ point }}</li>
      </ul>
    </div>

    <div class="doc-section">
      <h3>结果</h3>
      <div class="document-body rendered-document">
        <template v-for="(block, index) in resultBlocks" :key="`${block.type}-${index}`">
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
    </div>

    <el-collapse v-if="showRaw || resolvedResult.sourceJson">
      <el-collapse-item v-if="resolvedResult.sourceJson" title="原始 Result JSON" name="result-json">
        <pre class="code-block">{{ resolvedResult.sourceJson }}</pre>
      </el-collapse-item>
      <el-collapse-item v-if="showRaw" title="Provider 原始响应" name="provider-raw">
        <pre class="code-block">{{ formattedRaw }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Download } from '@element-plus/icons-vue'
import ResultInlineText from '@/components/ai/ResultInlineText.vue'
import { formatExecutionDuration, formatProviderName, formatTokenUsage } from '@/utils/aiProvider'
import {
  buildResultMarkdown,
  createResultDocumentFilename,
  resolveResultDocument
} from '@/utils/resultDocument'
import {
  cleanResultInlineText,
  parseResultMarkdown,
  type ResultBlock
} from '@/utils/resultMarkdown'

const props = withDefaults(
  defineProps<{
    summary: string
    result: string
    raw?: string
    provider?: string | null
    model?: string | null
    inputTokens?: number | null
    outputTokens?: number | null
    totalTokens?: number | null
    durationMs?: number | null
    compact?: boolean
    showRaw?: boolean
  }>(),
  {
    raw: '',
    provider: null,
    model: null,
    inputTokens: null,
    outputTokens: null,
    totalTokens: null,
    durationMs: null,
    compact: false,
    showRaw: true
  }
)

const providerLabel = computed(() => formatProviderName(props.provider))
const tokenUsageLabel = computed(() => formatTokenUsage(props.totalTokens))
const durationLabel = computed(() => formatExecutionDuration(props.durationMs))
const tokenUsageDetail = computed(() =>
  [
    props.inputTokens == null ? '' : `输入 ${formatTokenUsage(props.inputTokens)}`,
    props.outputTokens == null ? '' : `输出 ${formatTokenUsage(props.outputTokens)}`
  ]
    .filter(Boolean)
    .join(' · ')
)

const formattedRaw = computed(() => {
  if (!props.raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(props.raw), null, 2)
  } catch {
    return props.raw
  }
})

const resolvedResult = computed(() => resolveResultDocument(props.result || ''))
const resultBlocks = computed(() => parseResultMarkdown(resolvedResult.value.markdown))
const portableDocument = computed(() =>
  buildResultMarkdown(props.summary || '', resolvedResult.value.markdown)
)

const keyPoints = computed(() => {
  const listItems = resultBlocks.value
    .filter((block): block is Extract<ResultBlock, { type: 'list' }> => block.type === 'list')
    .flatMap((block) => block.items)
    .map(cleanResultInlineText)
    .filter((item) => item.length > 8)

  if (listItems.length) {
    return listItems.slice(0, 5)
  }

  return resultBlocks.value
    .filter(
      (block): block is Extract<ResultBlock, { type: 'paragraph' | 'heading' }> =>
        block.type === 'paragraph' || block.type === 'heading'
    )
    .map((block) => cleanResultInlineText(block.content))
    .filter((item) => item.length > 8)
    .slice(0, 5)
})

async function copyResult() {
  try {
    if (!navigator.clipboard?.writeText) {
      throw new Error('Clipboard API unavailable')
    }
    await navigator.clipboard.writeText(portableDocument.value)
    ElMessage.success('结果 Markdown 已复制')
  } catch {
    ElMessage.error('复制失败，请稍后重试')
  }
}

function downloadResult() {
  const blob = new Blob([portableDocument.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = createResultDocumentFilename(props.summary)
  link.hidden = true
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  ElMessage.success('结果 Markdown 已下载')
}

</script>
