export type WorkspaceExecutionKind = 'task' | 'flow' | 'rerun' | 'recovery'

export interface WorkspaceExecution {
  kind: WorkspaceExecutionKind
  sourceId: string | null
}

export function workspaceExecutionLabel(execution: WorkspaceExecution | null | undefined) {
  if (!execution) return ''
  const labels: Record<WorkspaceExecutionKind, string> = {
    task: 'AI 执行中',
    flow: 'Flow 执行中',
    rerun: '历史重跑中',
    recovery: '运行恢复中'
  }
  return labels[execution.kind]
}
