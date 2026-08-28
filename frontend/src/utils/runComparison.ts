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
