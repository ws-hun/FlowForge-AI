import type { FlowArtifactContract, FlowNode, FlowRunSnapshot, TaskHistoryItem } from '@/types'

export type RunInputComparison = {
  relation: 'same' | 'different'
  verification: 'fingerprint' | 'stored-text'
}

export type RunProviderInputComparison = {
  relation: 'same' | 'different' | 'unavailable'
  verification: 'saved-execution-plan'
  sourceInputCount: number | null
  targetInputCount: number | null
}

export type RunFlowOriginKind = 'result' | 'prompt' | 'flow'

export type RunFlowOriginEvidence = {
  flowId: string
  flowTitle: string
  originKind: RunFlowOriginKind | null
  originId: string | null
  originTitle: string | null
  intermediatePromptId: string | null
  sourceFlowVersionId: string | null
  sourceFlowVersionNumber: number | null
}

export type RunFlowOriginDifference =
  | 'flow-asset'
  | 'origin-kind'
  | 'origin-identity'
  | 'origin-prompt'
  | 'origin-version'

export type RunFlowOriginComparison = {
  relation: 'same' | 'different' | 'unavailable'
  flowRelation: 'same' | 'different' | 'unavailable'
  originRelation: 'same' | 'different' | 'unavailable'
  verification: 'saved-flow-snapshot'
  source: RunFlowOriginEvidence | null
  target: RunFlowOriginEvidence | null
  differences: RunFlowOriginDifference[]
}

export type RunFlowSnapshotChangeKind =
  | 'title'
  | 'description'
  | 'runtime-context'
  | 'node-added'
  | 'node-removed'
  | 'node-updated'
  | 'node-reordered'
  | 'variable-added'
  | 'variable-removed'
  | 'variable-updated'

export type RunFlowSnapshotChange = {
  key: string
  kind: RunFlowSnapshotChangeKind
  title: string
}

export type RunFlowSnapshotComparison = {
  relation: 'same' | 'different' | 'unavailable'
  verification: 'saved-flow-snapshot'
  sourceNodeCount: number | null
  targetNodeCount: number | null
  changes: RunFlowSnapshotChange[]
}

export type RunProviderExecutionComparison = {
  relation: 'same' | 'different' | 'unavailable'
  verification: 'flow-runtime-contract' | 'task-metadata' | 'unavailable'
  source: RunProviderExecutionEvidence
  target: RunProviderExecutionEvidence
  differences: RunProviderExecutionDifference[]
  unknown: RunProviderExecutionDifference[]
}

export type RunProviderExecutionEvidence = {
  provider: string | null
  model: string | null
  status: TaskHistoryItem['status']
  providerCallCount: number | null
  attemptCount: number | null
}

export type RunProviderExecutionDifference =
  | 'provider'
  | 'model'
  | 'status'
  | 'provider-call-count'
  | 'attempt-count'

export function compareRunExecutionInputs(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem
): RunInputComparison {
  const sourceFingerprint = sourceRun.flowRunTrace?.executionInputFingerprint
  const targetFingerprint = targetRun.flowRunTrace?.executionInputFingerprint

  if (sourceFingerprint && targetFingerprint) {
    return {
      relation: sourceFingerprint === targetFingerprint ? 'same' : 'different',
      verification: 'fingerprint'
    }
  }

  return {
    relation: sourceRun.input === targetRun.input ? 'same' : 'different',
    verification: 'stored-text'
  }
}

export function compareRunProviderInputDeclarations(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem
): RunProviderInputComparison {
  const sourceInputs = savedProviderInputs(sourceRun)
  const targetInputs = savedProviderInputs(targetRun)

  if (!sourceInputs || !targetInputs) {
    return {
      relation: 'unavailable',
      verification: 'saved-execution-plan',
      sourceInputCount: sourceInputs?.length ?? null,
      targetInputCount: targetInputs?.length ?? null
    }
  }

  return {
    relation: sameArtifactContracts(sourceInputs, targetInputs) ? 'same' : 'different',
    verification: 'saved-execution-plan',
    sourceInputCount: sourceInputs.length,
    targetInputCount: targetInputs.length
  }
}

export function compareRunFlowOrigins(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem
): RunFlowOriginComparison {
  const source = flowOriginEvidence(sourceRun)
  const target = flowOriginEvidence(targetRun)
  if (!source || !target) {
    return {
      relation: 'unavailable',
      flowRelation: 'unavailable',
      originRelation: 'unavailable',
      verification: 'saved-flow-snapshot',
      source,
      target,
      differences: []
    }
  }

  const differences: RunFlowOriginDifference[] = []
  const flowRelation = source.flowId === target.flowId ? 'same' : 'different'
  if (flowRelation === 'different') differences.push('flow-asset')

  let originRelation: RunFlowOriginComparison['originRelation'] = 'same'
  if (!completeFlowOrigin(source) || !completeFlowOrigin(target)) {
    originRelation = 'unavailable'
  } else {
    compareFlowOriginField(source.originKind, target.originKind, 'origin-kind', differences)
    compareFlowOriginField(source.originId, target.originId, 'origin-identity', differences)
    compareFlowOriginField(
      source.intermediatePromptId,
      target.intermediatePromptId,
      'origin-prompt',
      differences
    )
    if (
      source.sourceFlowVersionId !== target.sourceFlowVersionId
      || source.sourceFlowVersionNumber !== target.sourceFlowVersionNumber
    ) {
      differences.push('origin-version')
    }
    originRelation = differences.some((difference) => difference !== 'flow-asset')
      ? 'different'
      : 'same'
  }

  return {
    relation: differences.length
      ? 'different'
      : originRelation === 'unavailable' ? 'unavailable' : 'same',
    flowRelation,
    originRelation,
    verification: 'saved-flow-snapshot',
    source,
    target,
    differences
  }
}

export function compareRunFlowSnapshots(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem
): RunFlowSnapshotComparison {
  const source = sourceRun.flowRunSnapshot
  const target = targetRun.flowRunSnapshot
  if (!source || !target) {
    return {
      relation: 'unavailable',
      verification: 'saved-flow-snapshot',
      sourceNodeCount: source?.nodes.length ?? null,
      targetNodeCount: target?.nodes.length ?? null,
      changes: []
    }
  }

  const changes: RunFlowSnapshotChange[] = []
  if (source.title !== target.title) {
    changes.push({ key: 'title', kind: 'title', title: 'Flow 名称' })
  }
  if (source.description !== target.description) {
    changes.push({ key: 'description', kind: 'description', title: 'Flow 目标' })
  }
  if (source.runtimeContext !== target.runtimeContext) {
    changes.push({ key: 'runtime-context', kind: 'runtime-context', title: '运行说明' })
  }
  changes.push(...compareSnapshotNodes(source.nodes, target.nodes))
  changes.push(...compareSnapshotVariables(source.variableValues, target.variableValues))

  return {
    relation: changes.length ? 'different' : 'same',
    verification: 'saved-flow-snapshot',
    sourceNodeCount: source.nodes.length,
    targetNodeCount: target.nodes.length,
    changes
  }
}

export function compareRunProviderExecution(
  sourceRun: TaskHistoryItem,
  targetRun: TaskHistoryItem
): RunProviderExecutionComparison {
  const source = providerExecutionEvidence(sourceRun)
  const target = providerExecutionEvidence(targetRun)
  const verification = source.verification === 'unavailable' || target.verification === 'unavailable'
    ? 'unavailable'
    : source.verification === 'flow-runtime-contract' && target.verification === 'flow-runtime-contract'
      ? 'flow-runtime-contract'
      : 'task-metadata'

  const differences: RunProviderExecutionDifference[] = []
  const unknown: RunProviderExecutionDifference[] = []
  compareEvidenceField(source.evidence.provider, target.evidence.provider, 'provider', differences, unknown)
  compareEvidenceField(source.evidence.model, target.evidence.model, 'model', differences, unknown)
  compareEvidenceField(source.evidence.status, target.evidence.status, 'status', differences, unknown)
  compareEvidenceField(
    source.evidence.providerCallCount,
    target.evidence.providerCallCount,
    'provider-call-count',
    differences,
    unknown,
    true
  )
  compareEvidenceField(
    source.evidence.attemptCount,
    target.evidence.attemptCount,
    'attempt-count',
    differences,
    unknown,
    true
  )

  return {
    relation: verification === 'unavailable' || unknown.length
      ? differences.length ? 'different' : 'unavailable'
      : differences.length ? 'different' : 'same',
    verification,
    source: source.evidence,
    target: target.evidence,
    differences,
    unknown
  }
}

function compareEvidenceField<T>(
  source: T | null,
  target: T | null,
  difference: RunProviderExecutionDifference,
  differences: RunProviderExecutionDifference[],
  unknown: RunProviderExecutionDifference[],
  unknownWhenBothMissing = false
) {
  if (source === null || target === null) {
    if (source !== target || unknownWhenBothMissing) unknown.push(difference)
    return
  }
  if (source !== target) differences.push(difference)
}

function compareFlowOriginField<T>(
  source: T | null,
  target: T | null,
  difference: RunFlowOriginDifference,
  differences: RunFlowOriginDifference[]
) {
  if (source !== target) differences.push(difference)
}

function compareSnapshotNodes(sourceNodes: FlowNode[], targetNodes: FlowNode[]) {
  const sourceById = new Map(sourceNodes.map((node) => [node.id, node]))
  const targetById = new Map(targetNodes.map((node) => [node.id, node]))
  const sourceSharedOrder = sourceNodes.filter((node) => targetById.has(node.id)).map((node) => node.id)
  const targetSharedOrder = targetNodes.filter((node) => sourceById.has(node.id)).map((node) => node.id)
  const sourceIndexes = new Map(sourceSharedOrder.map((id, index) => [id, index]))
  const targetIndexes = new Map(targetSharedOrder.map((id, index) => [id, index]))
  const changes: RunFlowSnapshotChange[] = []

  sourceNodes.forEach((sourceNode) => {
    const targetNode = targetById.get(sourceNode.id)
    if (!targetNode) {
      changes.push({
        key: `node-removed:${sourceNode.id}`,
        kind: 'node-removed',
        title: sourceNode.title
      })
      return
    }
    if (!sameFlowNode(sourceNode, targetNode)) {
      changes.push({
        key: `node-updated:${sourceNode.id}`,
        kind: 'node-updated',
        title: targetNode.title
      })
      return
    }
    if (sourceIndexes.get(sourceNode.id) !== targetIndexes.get(sourceNode.id)) {
      changes.push({
        key: `node-reordered:${sourceNode.id}`,
        kind: 'node-reordered',
        title: targetNode.title
      })
    }
  })

  targetNodes.forEach((targetNode) => {
    if (!sourceById.has(targetNode.id)) {
      changes.push({
        key: `node-added:${targetNode.id}`,
        kind: 'node-added',
        title: targetNode.title
      })
    }
  })
  return changes
}

function compareSnapshotVariables(
  sourceVariables: FlowRunSnapshot['variableValues'],
  targetVariables: FlowRunSnapshot['variableValues']
) {
  const source = sourceVariables || {}
  const target = targetVariables || {}
  const names = new Set([...Object.keys(source), ...Object.keys(target)])
  return Array.from(names).flatMap<RunFlowSnapshotChange>((name) => {
    const sourceHasValue = Object.prototype.hasOwnProperty.call(source, name)
    const targetHasValue = Object.prototype.hasOwnProperty.call(target, name)
    if (!sourceHasValue) {
      return [{ key: `variable-added:${name}`, kind: 'variable-added', title: `{${name}}` }]
    }
    if (!targetHasValue) {
      return [{ key: `variable-removed:${name}`, kind: 'variable-removed', title: `{${name}}` }]
    }
    return source[name] === target[name]
      ? []
      : [{ key: `variable-updated:${name}`, kind: 'variable-updated', title: `{${name}}` }]
  })
}

function sameFlowNode(source: FlowNode, target: FlowNode) {
  return source.type === target.type
    && source.title === target.title
    && source.description === target.description
    && source.content === target.content
    && source.promptId === target.promptId
    && source.promptTitle === target.promptTitle
}

function completeFlowOrigin(evidence: RunFlowOriginEvidence) {
  if (!evidence.originKind || !evidence.originId) {
    return false
  }
  return evidence.originKind !== 'result' || Boolean(evidence.intermediatePromptId)
}

function flowOriginEvidence(run: TaskHistoryItem): RunFlowOriginEvidence | null {
  const snapshot = run.flowRunSnapshot
  if (!snapshot) {
    return null
  }
  const originKind: RunFlowOriginKind | null = snapshot.sourceTaskId
    ? 'result'
    : snapshot.sourcePromptId
      ? 'prompt'
      : snapshot.sourceFlowId
        ? 'flow'
        : null
  const originId = originKind === 'result'
    ? snapshot.sourceTaskId ?? null
    : originKind === 'prompt'
      ? snapshot.sourcePromptId ?? null
      : originKind === 'flow'
        ? snapshot.sourceFlowId ?? null
        : null
  const originTitle = originKind === 'result'
    ? snapshot.sourceTaskSummary ?? snapshot.sourcePromptTitle ?? null
    : originKind === 'prompt'
      ? snapshot.sourcePromptTitle ?? null
      : originKind === 'flow'
        ? snapshot.sourceFlowTitle ?? null
        : null

  return {
    flowId: snapshot.flowId,
    flowTitle: snapshot.title,
    originKind,
    originId,
    originTitle,
    intermediatePromptId: originKind === 'result' ? snapshot.sourcePromptId ?? null : null,
    sourceFlowVersionId: originKind === 'flow' ? snapshot.sourceFlowVersionId ?? null : null,
    sourceFlowVersionNumber: originKind === 'flow' ? snapshot.sourceFlowVersionNumber ?? null : null
  }
}

function savedProviderInputs(run: TaskHistoryItem): FlowArtifactContract[] | null {
  const plan = run.flowRunTrace?.executionPlan
  if (plan?.version !== 'flow-plan-v5') {
    return null
  }
  const providerSteps = plan.steps.filter((step) => step.providerBoundary)
  const inputs = providerSteps.length === 1 ? providerSteps[0].providerInputArtifacts : null
  return inputs?.length ? inputs : null
}

function sameArtifactContracts(
  sourceInputs: FlowArtifactContract[],
  targetInputs: FlowArtifactContract[]
) {
  return sourceInputs.length === targetInputs.length && sourceInputs.every((source, index) => {
    const target = targetInputs[index]
    return source.key === target.key
      && source.type === target.type
      && source.storage === target.storage
  })
}

function providerExecutionEvidence(run: TaskHistoryItem) {
  const trace = run.flowRunTrace
  const modernFlow = trace?.executionPlan?.version === 'flow-plan-v5'
  const hasTaskProviderMetadata = Boolean(run.provider || run.model || run.status)
  const verification: RunProviderExecutionComparison['verification'] = modernFlow
    ? 'flow-runtime-contract'
    : hasTaskProviderMetadata
      ? 'task-metadata'
      : 'unavailable'

  return {
    verification,
    evidence: {
      provider: run.provider ?? null,
      model: run.model ?? null,
      status: run.status ?? null,
      providerCallCount: modernFlow ? trace?.providerCallCount ?? null : null,
      // Current flow-plan-v5 guarantees one persisted initial attempt per run.
      // Older traces must remain unknown until their attempt history is queried.
      attemptCount: modernFlow && trace?.providerCallCount === 1 ? 1 : null
    }
  }
}
