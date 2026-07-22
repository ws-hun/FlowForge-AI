<template>
  <div class="result-doc surface" :class="{ compact }">
    <div class="result-doc-header">
      <div class="result-doc-meta">
        <span class="badge">AI Result</span>
        <span v-if="providerLabel || model || tokenUsageLabel || durationLabel" class="execution-source">
          <strong v-if="providerLabel">{{ providerLabel }}</strong>
          <code v-if="model">{{ model }}</code>
          <span v-if="tokenUsageLabel" :title="tokenUsageDetail">{{ tokenUsageLabel }}</span>
          <span v-if="durationLabel">{{ durationLabel }}</span>
        </span>
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
          <h4 v-if="block.type === 'heading'" :class="`level-${block.level}`">{{ block.content }}</h4>
          <p v-else-if="block.type === 'paragraph'">{{ block.content }}</p>
          <ul v-else-if="block.type === 'list' && !block.ordered" class="rendered-list">
            <li v-for="item in block.items" :key="item">{{ item }}</li>
          </ul>
          <ol v-else-if="block.type === 'list'" class="rendered-list">
            <li v-for="item in block.items" :key="item">{{ item }}</li>
          </ol>
          <div v-else-if="block.type === 'code'" class="rendered-code-wrap">
            <span v-if="block.language">{{ block.language }}</span>
            <pre class="rendered-code">{{ block.content }}</pre>
          </div>
        </template>
      </div>
    </div>

    <el-collapse v-if="showRaw">
      <el-collapse-item title="原始 JSON" name="raw">
        <pre class="code-block">{{ formattedRaw }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatExecutionDuration, formatProviderName, formatTokenUsage } from '@/utils/aiProvider'

type ResultBlock =
  | { type: 'heading'; level: number; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; language: string; content: string }

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

const resultBlocks = computed(() => parseResultDocument(props.result || ''))

const keyPoints = computed(() => {
  const listItems = resultBlocks.value
    .filter((block): block is Extract<ResultBlock, { type: 'list' }> => block.type === 'list')
    .flatMap((block) => block.items)
    .map(cleanInlineText)
    .filter((item) => item.length > 8)

  if (listItems.length) {
    return listItems.slice(0, 5)
  }

  return resultBlocks.value
    .filter(
      (block): block is Extract<ResultBlock, { type: 'paragraph' | 'heading' }> =>
        block.type === 'paragraph' || block.type === 'heading'
    )
    .map((block) => cleanInlineText(block.content))
    .filter((item) => item.length > 8)
    .slice(0, 5)
})

function parseResultDocument(text: string): ResultBlock[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ResultBlock[] = []
  let paragraph: string[] = []
  let listItems: string[] = []
  let orderedList = false
  let codeLines: string[] = []
  let codeLanguage = ''
  let inCode = false

  function flushParagraph() {
    if (!paragraph.length) return
    blocks.push({ type: 'paragraph', content: cleanInlineText(paragraph.join(' ')) })
    paragraph = []
  }

  function flushList() {
    if (!listItems.length) return
    blocks.push({ type: 'list', ordered: orderedList, items: listItems.map(cleanInlineText).filter(Boolean) })
    listItems = []
    orderedList = false
  }

  for (const rawLine of lines) {
    const trimmed = rawLine.trim()

    if (trimmed.startsWith('```')) {
      if (inCode) {
        blocks.push({ type: 'code', language: codeLanguage, content: codeLines.join('\n').trim() })
        codeLines = []
        codeLanguage = ''
        inCode = false
      } else {
        flushParagraph()
        flushList()
        codeLanguage = trimmed.replace(/^```/, '').trim()
        inCode = true
      }
      continue
    }

    if (inCode) {
      codeLines.push(rawLine)
      continue
    }

    if (!trimmed) {
      flushParagraph()
      flushList()
      continue
    }

    const heading = trimmed.match(/^(#{1,4})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      blocks.push({ type: 'heading', level: heading[1].length, content: cleanInlineText(heading[2]) })
      continue
    }

    const bullet = trimmed.match(/^[-*•]\s+(.+)$/)
    const numbered = trimmed.match(/^\d+[.)、]\s+(.+)$/)
    if (bullet || numbered) {
      flushParagraph()
      const isOrdered = Boolean(numbered)
      if (listItems.length && orderedList !== isOrdered) {
        flushList()
      }
      orderedList = isOrdered
      listItems.push((bullet?.[1] || numbered?.[1] || '').trim())
      continue
    }

    flushList()
    paragraph.push(trimmed)
  }

  if (inCode) {
    blocks.push({ type: 'code', language: codeLanguage, content: codeLines.join('\n').trim() })
  }
  flushParagraph()
  flushList()

  return blocks.length ? blocks : [{ type: 'paragraph', content: text }]
}

function cleanInlineText(text: string) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^#+\s*/, '')
    .trim()
}
</script>
