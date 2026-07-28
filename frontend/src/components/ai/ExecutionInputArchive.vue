<template>
  <details class="execution-input-archive" :class="{ compact }">
    <summary>
      <span>
        <strong>{{ title }}</strong>
        <small>{{ inputStats }}</small>
      </span>
      <Document class="execution-input-archive-icon" />
    </summary>
    <div class="execution-input-archive-body">
      <div class="execution-input-archive-toolbar">
        <span>Server input</span>
        <div class="execution-input-archive-actions">
          <button v-if="canCreateVariant" type="button" class="text-button" @click="emit('createVariant')">
            <EditPen class="execution-input-archive-action-icon" />
            创建输入变体
          </button>
          <button type="button" class="text-button" @click="copyInput">
            <CopyDocument class="execution-input-archive-action-icon" />
            复制输入
          </button>
        </div>
      </div>
      <pre>{{ input }}</pre>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Document, EditPen } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    input: string
    title?: string
    compact?: boolean
    canCreateVariant?: boolean
  }>(),
  {
    title: '固定执行输入',
    compact: false,
    canCreateVariant: false
  }
)

const emit = defineEmits<{
  createVariant: []
}>()

const inputStats = computed(() => {
  const normalizedInput = props.input || ''
  const lineCount = normalizedInput ? normalizedInput.split(/\r?\n/).length : 0
  return `${lineCount} 行 · ${normalizedInput.length} 字符`
})

async function copyInput() {
  try {
    await navigator.clipboard.writeText(props.input)
    ElMessage.success('固定执行输入已复制')
  } catch {
    ElMessage.error('复制失败，请展开后手动复制')
  }
}
</script>
