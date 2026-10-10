<template>
  <section
    v-if="visible"
    class="flow-editor-save-bar"
    :class="{ conflict, recovered }"
    role="status"
    aria-live="polite"
  >
    <span class="flow-editor-save-bar-dot"></span>
    <div class="flow-editor-save-bar-copy">
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
    <div class="flow-editor-save-bar-scope">
      <span v-if="flowChanged">Flow 目标</span>
      <span v-if="nodeChanged">节点内容</span>
    </div>
    <div class="flow-editor-save-bar-actions">
      <button type="button" class="ghost-button" :disabled="busy" @click="emit('discard')">
        放弃修改
      </button>
      <button type="button" class="secondary-button" :disabled="busy" @click="emit('save')">
        {{ busy ? '保存中...' : '保存全部' }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  flowChanged: boolean
  nodeChanged: boolean
  conflict?: boolean
  recovered?: boolean
  busy?: boolean
}>(), {
  conflict: false,
  recovered: false,
  busy: false
})

const emit = defineEmits<{
  save: []
  discard: []
}>()

const visible = computed(() => props.flowChanged || props.nodeChanged)
const title = computed(() => {
  if (props.conflict) return '本地编辑与最新 Flow 存在冲突'
  if (props.recovered) return '已恢复一份本地未保存编辑'
  return '有未保存的 Flow 编辑'
})
const description = computed(() => {
  if (props.conflict) return '保存会基于当前编辑重新提交；也可以先放弃本地修改并采用最新版本。'
  if (props.recovered) return '保存全部会一次提交当前 Flow 目标与节点修改，运行简报仍会独立自动保存。'
  return '保存全部会一次提交当前 Flow 目标与节点修改，避免只保存一部分。'
})
</script>
