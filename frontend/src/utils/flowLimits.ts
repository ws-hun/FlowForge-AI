export const MAX_FLOW_NODES = 50

export function canAddFlowNode(currentNodeCount: number): boolean {
  return Number.isSafeInteger(currentNodeCount) && currentNodeCount >= 0 && currentNodeCount < MAX_FLOW_NODES
}
