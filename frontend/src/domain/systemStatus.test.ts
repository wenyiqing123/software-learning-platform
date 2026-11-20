import { describe, expect, it } from 'vitest'
import { mergeAiDependency, statusLabel, statusTone, type SystemStatus } from './systemStatus'

const base: SystemStatus = {
  overallStatus: 'UP',
  checkedAt: '2026-11-20T03:00:00Z',
  dependencies: [
    { key: 'mysql', label: 'MySQL', status: 'UP', impact: '课程可用' },
    { key: 'redis', label: 'Redis', status: 'UP', impact: '推荐可用' },
    { key: 'rabbitmq', label: 'RabbitMQ', status: 'UP', impact: '异步更新可用' },
    { key: 'knowledge', label: 'Python Bridge / 知识图谱', status: 'UP', impact: '图谱可用' },
    { key: 'ai', label: '大模型服务', status: 'UP', impact: '问答可用' },
  ],
}

describe('system status presentation', () => {
  it('maps dependency states to stable labels and tones', () => {
    expect(statusLabel('UP')).toBe('正常')
    expect(statusLabel('DOWN')).toBe('不可用')
    expect(statusTone('DEGRADED')).toBe('warning')
    expect(statusTone('NOT_CHECKED')).toBe('muted')
  })

  it('marks the model dependency as degraded when the AI service is up but unconfigured', () => {
    const result = mergeAiDependency(base, { configured: false, pythonKnowledgeStatus: 'UP' })
    expect(result.dependencies.find(item => item.key === 'ai')?.status).toBe('DEGRADED')
    expect(result.overallStatus).toBe('DEGRADED')
  })
})
