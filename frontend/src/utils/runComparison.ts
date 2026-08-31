import type { FlowArtifactContract, TaskHistoryItem } from '@/types'

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
