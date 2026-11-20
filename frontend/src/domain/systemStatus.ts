export type DependencyState = 'UP' | 'DOWN' | 'DEGRADED' | 'NOT_CHECKED'
export type StatusTone = 'success' | 'warning' | 'error' | 'muted'

export interface DependencyStatus {
  key: string
  label: string
  status: DependencyState
  impact: string
}

export interface SystemStatus {
  overallStatus: DependencyState
  checkedAt: string
  dependencies: DependencyStatus[]
}

export interface AiDependencySummary {
  configured?: boolean
  pythonKnowledgeStatus?: string
}

export function statusLabel(status: DependencyState): string {
  return { UP: '正常', DOWN: '不可用', DEGRADED: '部分可用', NOT_CHECKED: '未检查' }[status]
}

export function statusTone(status: DependencyState): StatusTone {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'error'
  if (status === 'DEGRADED') return 'warning'
  return 'muted'
}

export function mergeAiDependency(status: SystemStatus, ai: AiDependencySummary | null | undefined): SystemStatus {
  if (!ai) return status
  const dependencies = status.dependencies.map(item => {
    if (item.key === 'knowledge' && ai.pythonKnowledgeStatus === 'DOWN') {
      return { ...item, status: 'DOWN' as const }
    }
    if (item.key === 'ai' && ai.configured === false && item.status === 'UP') {
      return { ...item, status: 'DEGRADED' as const, impact: 'AI 服务已启动但模型未完成配置，课程和题库仍可使用' }
    }
    return item
  })
  const overallStatus: DependencyState = dependencies.some(item => item.key === 'mysql' && item.status !== 'UP')
    ? 'DOWN'
    : dependencies.some(item => item.status !== 'UP') ? 'DEGRADED' : 'UP'
  return { ...status, overallStatus, dependencies }
}
