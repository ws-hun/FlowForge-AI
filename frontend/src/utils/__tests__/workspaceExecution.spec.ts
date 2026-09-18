import { describe, expect, it } from 'vitest'
import { workspaceExecutionLabel, workspaceExecutionTarget } from '@/utils/workspaceExecution'

describe('workspace execution labels', () => {
  it('describes the active Provider action without losing its identity', () => {
    expect(workspaceExecutionLabel({ kind: 'task', sourceId: null })).toBe('AI 执行中')
    expect(workspaceExecutionLabel({ kind: 'flow', sourceId: 'flow-1' })).toBe('Flow 执行中')
    expect(workspaceExecutionLabel({ kind: 'rerun', sourceId: 'run-1' })).toBe('历史重跑中')
    expect(workspaceExecutionLabel({ kind: 'recovery', sourceId: 'run-2' })).toBe('运行恢复中')
    expect(workspaceExecutionLabel(null)).toBe('')
  })

  it('returns to the workspace that owns the active execution', () => {
    expect(workspaceExecutionTarget({ kind: 'task', sourceId: null })).toBe('/tasks')
    expect(workspaceExecutionTarget({ kind: 'flow', sourceId: 'flow/1' })).toBe('/workflows?flow=flow%2F1')
    expect(workspaceExecutionTarget({ kind: 'rerun', sourceId: 'run-1' })).toBe('/history?run=run-1')
    expect(workspaceExecutionTarget({ kind: 'recovery', sourceId: null })).toBe('/history')
    expect(workspaceExecutionTarget(null)).toBe('/api-keys')
  })
})
