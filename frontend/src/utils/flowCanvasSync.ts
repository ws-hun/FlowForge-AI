export type FlowCanvasRestoreDecision = 'use-cloud' | 'save-local' | 'migrate-local' | 'empty'

export function resolveFlowCanvasRestoreDecision(input: {
  remoteSaved: boolean
  localSaved: boolean
  editedWhileLoading: boolean
}): FlowCanvasRestoreDecision {
  if (input.editedWhileLoading) return 'save-local'
  if (input.remoteSaved) return 'use-cloud'
  return input.localSaved ? 'migrate-local' : 'empty'
}

export function hasFlowCanvasRevisionConflict(input: {
  remoteSaved: boolean
  hasPendingLocalState: boolean
  knownRevision: number | undefined
  remoteRevision: number
}): boolean {
  return input.remoteSaved && input.hasPendingLocalState &&
    (input.knownRevision === undefined || input.knownRevision !== input.remoteRevision)
}
