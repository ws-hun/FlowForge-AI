<template>
  <div class="workspace-frame">
    <TopBar />
    <main class="workspace-shell">
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import TopBar from '@/components/shell/TopBar.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()

onMounted(() => {
  workspace.bootstrap()
})
</script>
