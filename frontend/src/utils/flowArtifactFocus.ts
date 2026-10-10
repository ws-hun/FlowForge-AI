import type { FlowNodeRunTrace, FlowRunTrace } from '@/types'

export type FlowArtifactFocusFailure =
  | 'missing-trace'
  | 'trace-run-mismatch'
  | 'missing-node'
  | 'missing-artifact'
  | 'artifact-node-mismatch'
  | 'artifact-not-inspectable'

export type FlowArtifactFocusResolution =
  | { valid: true; node: FlowNodeRunTrace; artifactKey: string }
  | { valid: false; reason: FlowArtifactFocusFailure }

export function resolveFlowArtifactFocus(
  trace: FlowRunTrace | null | undefined,
  runId: string,
  nodeId: string,
  artifactKey: string
): FlowArtifactFocusResolution {
  if (!trace) {
    return { valid: false, reason: 'missing-trace' }
  }
  if (!trace.runId || trace.runId !== runId) {
    return { valid: false, reason: 'trace-run-mismatch' }
  }

  const node = trace.nodes.find((item) => item.nodeId === nodeId)
  if (!node) {
    return { valid: false, reason: 'missing-node' }
  }

  const artifactNode = trace.nodes.find((item) => item.outputArtifact?.key === artifactKey)
  if (!artifactNode?.outputArtifact) {
    return { valid: false, reason: 'missing-artifact' }
  }
  if (artifactNode.nodeId !== nodeId) {
    return { valid: false, reason: 'artifact-node-mismatch' }
  }

  const artifact = artifactNode.outputArtifact
  const inspectable =
    artifact.storage === 'node-artifact'
    && (artifact.state === 'materialized' || (artifact.type === 'provider-result' && artifact.state === 'failed'))
  if (!inspectable) {
    return { valid: false, reason: 'artifact-not-inspectable' }
  }

  return { valid: true, node, artifactKey }
}
