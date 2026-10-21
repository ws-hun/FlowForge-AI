import { describe, expect, it } from 'vitest'
import {
  hasFlowCanvasRevisionConflict,
  resolveFlowCanvasRestoreDecision
} from '@/utils/flowCanvasSync'

describe('flow canvas synchronization policy', () => {
  it('prefers saved cloud state when the user did not edit during loading', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: true,
      localSaved: true,
      editedWhileLoading: false
    })).toBe('use-cloud')
  })

  it('migrates a local canvas when cloud state does not exist', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: false,
      localSaved: true,
      editedWhileLoading: false
    })).toBe('migrate-local')
  })

  it('keeps edits made while loading and saves them to the server', () => {
    expect(resolveFlowCanvasRestoreDecision({
      remoteSaved: true,
      localSaved: false,
      editedWhileLoading: true
    })).toBe('save-local')
  })

  it('recognizes a server change when pending local edits exist', () => {
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: true,
      hasPendingLocalState: true,
      knownRevision: 2,
      remoteRevision: 3
    })).toBe(true)
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: true,
      hasPendingLocalState: true,
      knownRevision: 3,
      remoteRevision: 3
    })).toBe(false)
    expect(hasFlowCanvasRevisionConflict({
      remoteSaved: false,
      hasPendingLocalState: true,
      knownRevision: undefined,
      remoteRevision: 0
    })).toBe(false)
  })
})
