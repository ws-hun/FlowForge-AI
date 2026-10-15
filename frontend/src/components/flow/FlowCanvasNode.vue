<template>
  <div class="flow-canvas-node" :class="[data.flowNode.type, { selected, 'is-incomplete': data.incomplete }]">
    <Handle id="target" type="target" :position="Position.Left" />
    <div class="flow-canvas-node-topline">
      <span class="flow-canvas-node-kind">{{ data.kindLabel }}</span>
      <span class="flow-canvas-node-state">{{ data.stateLabel }}</span>
    </div>
    <span class="flow-canvas-node-operation">{{ data.operationLabel }}</span>
    <strong>{{ data.flowNode.title }}</strong>
    <p>{{ data.flowNode.description }}</p>
    <Handle id="source" type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import type { FlowCanvasNodeData } from '@/utils/flowCanvasState'

defineProps<NodeProps<FlowCanvasNodeData>>()
</script>
