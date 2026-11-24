import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = (relativePath: string) => readFileSync(resolve(process.cwd(), relativePath), 'utf8')

describe('thesis compliance guards', () => {
  it('keeps ranked fixed system prompt identifiers and constraint text out of browser sources', () => {
    const browserSource = [
      'src/views/TutorView.vue',
      'src/components/TutorAnswerCard.vue',
      'src/components/ExperimentResultGrid.vue',
      'src/views/ExperimentsView.vue',
    ].map(source).join('\n')
    const markers = [
      ['GROUNDED', '_ANSWER_', 'V', '3'].join(''),
      ['EVIDENCE', '_SELECTION_', 'V', '1'].join(''),
    ]
    expect(markers.every(marker => !browserSource.includes(marker))).toBe(true)
    expect(browserSource).not.toContain('每条答案必须明确包含对应三元组')
    expect(browserSource).not.toContain('只输出 JSON，不输出标题、Markdown 或分析')
  })

  it('keeps academic wording within the approved non-conclusive boundary', () => {
    const copy = [
      'src/views/DashboardView.vue',
      'src/views/RecommendationsView.vue',
      'src/views/EvaluationView.vue',
      'src/views/LoginView.vue',
      'src/views/AdminContentCenterView.vue',
      'src/views/AdminExperimentCenterView.vue',
      'src/views/TeachingDashboardView.vue',
      'src/views/EvaluationAdminView.vue',
      'src/views/MicroLessonAdminView.vue',
      'src/views/ExperimentTaskAdminView.vue',
    ].map(source).join('\n')
    for (const phrase of ['机器学习知识追踪', '采集生理信号', '已证明降低认知负荷', '系统已降低认知负荷']) {
      expect(copy).not.toContain(phrase)
    }
  })

  it('uses explicit graph dependency states and never seeds a fake graph on failure', () => {
    const graph = source('src/views/GraphView.vue')
    const mastery = source('src/views/MasteryMapView.vue')
    const viewport = source('src/components/VisualizationViewport.vue')
    expect(graph).toContain('available: false')
    expect(graph).toContain('知识服务暂时不可用')
    expect(graph).toContain('nodes: [], edges: [], evidence: [], available: false')
    expect(mastery).toContain('本视图不伪造 NebulaGraph 证据')
    expect(viewport).toContain('等待知识服务连接')
    expect(viewport).toContain('可视化数据暂时不可用')
  })

  it('keeps failed experiment answers out of successful result rendering', () => {
    const grid = source('src/components/ExperimentResultGrid.vue')
    expect(grid).toContain("result.error ? '失败' : '成功'")
    expect(grid).toContain('v-else class="compare-answer"')
    expect(grid).toContain("result.mode !== 'RANKED'")
  })
})
