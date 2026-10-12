import { ref } from 'vue'
import type { FlowNodeArtifactDetail } from '@/types'

export function createFlowArtifactLoader(
  fetchDetail: (taskId: string, artifactKey: string) => Promise<FlowNodeArtifactDetail>,
  onFailure: () => void
) {
  const details = ref<Record<string, FlowNodeArtifactDetail>>({})
  const loading = ref<Record<string, boolean>>({})
  const requests = new Map<string, Promise<FlowNodeArtifactDetail | null>>()
  let generation = 0
  let currentTaskId = ''
  let currentFlowId = ''

  function reset(taskId: string, flowId: string) {
    generation += 1
    currentTaskId = taskId
    currentFlowId = flowId
    details.value = {}
    loading.value = {}
    requests.clear()
  }

  function load(taskId: string, artifactKey: string): Promise<FlowNodeArtifactDetail | null> {
    if (!taskId || taskId !== currentTaskId || !artifactKey) return Promise.resolve(null)
    if (details.value[artifactKey]) return Promise.resolve(details.value[artifactKey])
    const existing = requests.get(artifactKey)
    if (existing) return existing

    const requestGeneration = generation
    const flowId = currentFlowId
    loading.value[artifactKey] = true
    // Defer transport until the promise is registered, including synchronous failures.
    const request = Promise.resolve().then(() => fetchDetail(taskId, artifactKey)).then((detail) => {
      if (requestGeneration !== generation) return null
      if (detail.taskId !== taskId || detail.flowId !== flowId || detail.artifactKey !== artifactKey) {
        throw new Error('Artifact identity mismatch')
      }
      details.value[artifactKey] = detail
      return detail
    }).catch(() => {
      if (requestGeneration === generation) onFailure()
      return null
    }).finally(() => {
      if (requestGeneration === generation) {
        delete loading.value[artifactKey]
        requests.delete(artifactKey)
      }
    })
    requests.set(artifactKey, request)
    return request
  }

  return { details, loading, load, reset }
}
