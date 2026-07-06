<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">AI Command Workspace</p>
      <h1>写下任务，然后执行。</h1>
      <p>这不是聊天窗口，而是一个面向 AI 工作流的命令空间。</p>
    </header>

    <div class="two-column command-layout">
      <section class="command-input">
        <textarea
          v-model="workspace.taskInput"
          class="workspace-input"
          placeholder="描述你希望 AI 完成的任务..."
        ></textarea>
        <div v-if="workspace.taskSourcePromptTitle || workspace.taskSourceFlowTitle" class="source-note">
          <span>{{ workspace.taskSourceFlowTitle ? '来自 Flow' : '来自 Prompt' }}</span>
          <strong>{{ workspace.taskSourceFlowTitle || workspace.taskSourcePromptTitle }}</strong>
        </div>
        <div class="composer-footer">
          <span>{{ workspace.activeProvider?.provider || '请先配置 Provider' }}</span>
          <button class="primary-button" :disabled="workspace.running" @click="workspace.executeTask">
            {{ workspace.running ? '执行中...' : '执行任务' }}
          </button>
        </div>
      </section>

      <section class="result-document">
        <div v-if="workspace.latestResult" class="result-doc surface">
          <div class="result-doc-header">
            <span class="badge">AI Result</span>
            <p class="page-kicker">摘要</p>
            <h2>{{ workspace.latestResult.summary }}</h2>
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

          <el-collapse>
            <el-collapse-item title="原始 JSON" name="raw">
              <pre class="code-block">{{ formattedRaw }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div v-else class="empty-state">结果会以文档形式显示在这里。</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'

type ResultBlock =
  | { type: 'heading'; level: number; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; language: string; content: string }

const workspace = useWorkspaceStore()

const formattedRaw = computed(() => {
  if (!workspace.latestResult?.raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(workspace.latestResult.raw), null, 2)
  } catch {
    return workspace.latestResult.raw
  }
})

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
    .filter((block): block is Extract<ResultBlock, { type: 'paragraph' | 'heading' }> => block.type === 'paragraph' || block.type === 'heading')
    .map((block) => cleanInlineText(block.content))
    .filter((item) => item.length > 8)
    .slice(0, 5)
})

const resultBlocks = computed(() => parseResultDocument(workspace.latestResult?.result || ''))

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
    const line = rawLine.trimEnd()
    const trimmed = line.trim()

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
