export type FlowExecutionErrorKind =
  | 'node'
  | 'variables'
  | 'conflict'
  | 'provider'
  | 'missing'
  | 'unknown'

export interface FlowExecutionErrorPresentation {
  kind: FlowExecutionErrorKind
  title: string
  detail: string
  actionLabel: string
}

export function presentFlowExecutionError(
  status: number | null | undefined,
  message: string | null | undefined
): FlowExecutionErrorPresentation {
  const rawMessage = message?.trim() || '服务端暂时没有返回具体原因。'
  const detail = readableFlowExecutionError(rawMessage)

  if (/节点标题|节点说明|节点内容|节点不能为空|node/i.test(rawMessage)) {
    return {
      kind: 'node',
      title: '节点信息需要补充',
      detail,
      actionLabel: '重试预览'
    }
  }

  if (/变量|运行简报|variable/i.test(rawMessage)) {
    return {
      kind: 'variables',
      title: '运行上下文还未完成',
      detail,
      actionLabel: '重试预览'
    }
  }

  if (status === 409 || /版本|其他窗口|冲突|最新 Flow/.test(rawMessage)) {
    return {
      kind: 'conflict',
      title: 'Flow 已在其他位置更新',
      detail,
      actionLabel: '重新读取并预览'
    }
  }

  if (status === 502 || /Provider|模型|API Key|密钥|网关/.test(rawMessage)) {
    return {
      kind: 'provider',
      title: 'Provider 暂时无法执行',
      detail,
      actionLabel: '重试预览'
    }
  }

  if (status === 404 || /不存在|找不到|not found/i.test(rawMessage)) {
    return {
      kind: 'missing',
      title: 'Flow 已不可用',
      detail,
      actionLabel: '重新读取'
    }
  }

  return {
    kind: 'unknown',
    title: '执行预览暂时不可用',
    detail,
    actionLabel: '重试预览'
  }
}

function readableFlowExecutionError(message: string) {
  const translations: Array<[RegExp, string | ((match: RegExpMatchArray) => string)]> = [
    [/^Flow not found$/i, '找不到这个 Flow，请重新读取工作区。'],
    [/^Unsupported Flow node type:\s*(.+)$/i, (match) => `不支持的 Flow 节点类型：${match[1]}`],
    [/^No active AI API key configured$/i, '还没有配置已激活的 AI Provider。'],
    [/^AI Provider request timed out$/i, 'AI Provider 请求超时，请稍后重试。'],
    [/^AI Provider connection failed$/i, 'AI Provider 连接失败，请检查网络或服务地址。'],
    [/^AI Provider returned HTTP \d+$/i, 'AI Provider 暂时无法执行，请稍后重试。'],
    [/^Task run not found$/i, '找不到这次运行记录，请重新读取历史。']
  ]

  for (const [pattern, translation] of translations) {
    const match = message.match(pattern)
    if (match) {
      return typeof translation === 'function' ? translation(match) : translation
    }
  }

  return message
}
