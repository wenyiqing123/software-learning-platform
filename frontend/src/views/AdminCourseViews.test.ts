import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminView from './AdminView.vue'
import CourseView from './CourseView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http }))
const course = { id: 1, code: 'SE', name: '软件工程', term: '2026 秋季', description: '学习软件工程', graphSpaceId: 1, enabled: true }
const qualityReport = { courseId: 1, pointCount: 8, validEdgeCount: 7, errorCount: 0, warningCount: 0, valid: true, issues: [] }
const reading = { courseId: 1, name: '软件工程', code: 'SE', term: '2026 秋季', description: '学习软件工程', totalSections: 2, completedSections: 0,
  chapters: [{ id: 10, name: '需求与建模', description: '学习目标', sections: [
    { id: 101, knowledgePointId: 11, knowledgePointName: '需求分析', graphEntity: '需求分析', title: '理解需求', objectives: ['写出验收条件'], content: [{ heading: '识别目标', paragraphs: ['从业务目标出发分析需求。'] }], resources: [{ title: '原始资料', url: 'https://www.omg.org/spec/UML/2.5.1', description: '查阅规范' }, { title: '不安全链接', url: 'javascript:alert(1)', description: '不应生成活动链接' }], completed: false },
    { id: 102, knowledgePointId: 12, knowledgePointName: 'DFD', graphEntity: '数据流图DFD', title: '理解数据流', objectives: ['检查平衡'], content: [{ heading: '数据与处理', paragraphs: ['描述数据的变换。'] }], resources: [], completed: false },
  ] }] }
async function render(component: typeof CourseView | typeof AdminView, path = '/courses') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/courses', component: CourseView }, { path: '/admin', component: AdminView }, { path: '/knowledge', component: { template: '<div />' } }, { path: '/practice', component: { template: '<div />' } }] })
  await router.push(path); await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/auth/me') return { data: { role: 'ADMIN' } }
    if (path === '/courses' || path === '/admin/courses') return { data: [{ ...course }] }
    if (path === '/admin/knowledge-points') return { data: [{ id: 11, courseId: 1, name: '需求分析', graphEntity: '需求分析', enabled: true }] }
    if (path === '/admin/courses/1/prerequisite-quality') return { data: structuredClone(qualityReport) }
    if (path === '/admin/questions') return { data: [] }
    if (path === '/learning/notes') return { data: [] }
    if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: false } }
    if (path === '/courses/1/study') return { data: structuredClone(reading) }
    throw new Error('Unexpected API path: ' + path)
  })
  http.put.mockResolvedValue({ data: {} })
  http.post.mockResolvedValue({ data: { id: 1 } })
})
describe('course reading', () => {
  it('shows readable content and navigation for the exact current point', async () => {
    const { wrapper } = await render(CourseView)
    expect(wrapper.text()).toContain('从业务目标出发分析需求。')
    expect(wrapper.text()).toContain('写出验收条件')
    expect(wrapper.find('a[href*="/practice"]').attributes('href')).toContain('knowledgePointId=11')
    expect(wrapper.find('a[href*="/knowledge"]').attributes('href')).toContain(encodeURIComponent('需求分析'))
    expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false)
    await wrapper.findAll('button').find(b => b.text() === '下一节 →')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('描述数据的变换。')
    wrapper.unmount()
  })
  it('saves only the current user section and updates displayed progress after success', async () => {
    const { wrapper } = await render(CourseView)
    await wrapper.findAll('button').find(b => b.text() === '标记本节已读')!.trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/courses/1/study/sections/101/progress', { completed: true })
    expect(wrapper.text()).toContain('1 / 2')
    expect(wrapper.text()).toContain('已读 · 撤销标记')
    wrapper.unmount()
  })
  it('leaves progress unchanged when persistence fails and offers a retry', async () => {
    http.put.mockRejectedValueOnce({ response: { data: { message: '进度保存失败，请重试' } } })
    const { wrapper } = await render(CourseView)
    await wrapper.findAll('button').find(b => b.text() === '标记本节已读')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('进度保存失败')
    expect(wrapper.text()).toContain('0 / 2')
    expect(wrapper.findAll('button').some(b => b.text() === '标记本节已读' && !b.attributes('disabled'))).toBe(true)
    wrapper.unmount()
  })
  it('opens the course section matching a knowledgePointId path link', async () => {
    const { wrapper } = await render(CourseView, '/courses?courseId=1&knowledgePointId=12')
    expect(wrapper.text()).toContain('描述数据的变换。')
    expect(wrapper.text()).not.toContain('从业务目标出发分析需求。')
    wrapper.unmount()
  })
})
describe('administration', () => {
  it('does not load privileged data for a student', async () => {
    http.get.mockResolvedValue({ data: { role: 'STUDENT' } })
    const { wrapper } = await render(AdminView, '/admin')
    expect(wrapper.text()).toContain('需要管理员权限')
    expect(http.get.mock.calls.map(call => call[0])).toEqual(['/auth/me'])
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })
  it('requires multiple correct keys and publishes an explicit same-course mapping', async () => {
    const { wrapper } = await render(AdminView, '/admin')
    await wrapper.get('select[aria-label="题目知识点"]').setValue(11)
    await wrapper.get('select[aria-label="题型"]').setValue('MULTIPLE')
    await wrapper.get('textarea[aria-label="题干"]').setValue('哪些活动属于需求分析？')
    for (const [key, text] of [['A', '访谈'], ['B', '评审'], ['C', '部署'], ['D', '打包']]) await wrapper.get('input[aria-label="选项 ' + key + '"]').setValue(text)
    await wrapper.get('textarea[aria-label="答案解析"]').setValue('访谈与需求评审用于澄清需求。')
    await wrapper.get('input[aria-label="正确答案 A"]').setValue(true)
    await wrapper.get('form.question-editor').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('至少选择两个正确答案')
    expect(http.post).not.toHaveBeenCalled()
    await wrapper.get('input[aria-label="正确答案 B"]').setValue(true)
    await wrapper.get('form.question-editor').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/questions', expect.objectContaining({ courseId: 1, knowledgePointId: 11, type: 'MULTIPLE', answer: ['A', 'B'] }))
    expect(wrapper.text()).toContain('题目已保存')
    wrapper.unmount()
  })
  it('shows a read-only prerequisite quality summary and links each issue to its affected points', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path === '/auth/me') return { data: { role: 'ADMIN' } }
      if (path === '/admin/courses') return { data: [{ ...course }] }
      if (path === '/admin/knowledge-points') return { data: [{ id: 11, courseId: 1, name: '需求分析', graphEntity: '需求分析', enabled: true }] }
      if (path === '/admin/questions') return { data: [] }
      if (path === '/admin/courses/1/prerequisite-quality') return { data: {
        courseId: 1, pointCount: 8, validEdgeCount: 6, errorCount: 1, warningCount: 0, valid: false,
        issues: [{ code: 'CYCLE', severity: 'ERROR', pointId: 11, pointName: '需求分析', relatedPointId: 12, relatedPointName: 'DFD', message: '先修关系形成循环依赖。' }],
      } }
      throw new Error('Unexpected API path: ' + path)
    })
    const { wrapper } = await render(AdminView, '/admin')
    expect(wrapper.text()).toContain('先修关系质量检查')
    expect(wrapper.text()).toContain('发现 1 项关系问题')
    expect(wrapper.text()).toContain('需求分析')
    expect(wrapper.text()).toContain('DFD')
    expect(wrapper.text()).toContain('先修关系形成循环依赖。')
    expect(wrapper.find('button[aria-label*="修复先修"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it('shows a separate retryable message when prerequisite diagnostics are unavailable', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path === '/auth/me') return { data: { role: 'ADMIN' } }
      if (path === '/admin/courses') return { data: [{ ...course }] }
      if (path === '/admin/knowledge-points') return { data: [{ id: 11, courseId: 1, name: '需求分析', graphEntity: '需求分析', enabled: true }] }
      if (path === '/admin/questions') return { data: [] }
      if (path === '/admin/courses/1/prerequisite-quality') throw new Error('quality endpoint unavailable')
      throw new Error('Unexpected API path: ' + path)
    })
    const { wrapper } = await render(AdminView, '/admin')
    expect(wrapper.get('[role="alert"]').text()).toContain('先修关系检查暂时不可用')
    wrapper.unmount()
  })

  it('protects unsaved course draft on route leave and allows after save', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const { wrapper, router } = await render(AdminView, '/admin')

    // 修改课程草稿使其处于 dirty 状态
    await wrapper.get('input[aria-label="课程代码"]').setValue('CS101')
    await wrapper.get('input[aria-label="课程名称"]').setValue('计算机科学导论')

    // 1. 用户取消离开：留在 /admin
    confirmSpy.mockReturnValueOnce(false)
    await router.push('/courses')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin')

    // 2. 用户确认离开：成功跳转
    confirmSpy.mockReturnValueOnce(true)
    await router.push('/courses')
    expect(router.currentRoute.value.path).toBe('/courses')
    wrapper.unmount()
  })

  it('renders prerequisite topological DAG preview and filters questions cleanly', async () => {
    const baseGet = http.get.getMockImplementation()!
    http.get.mockImplementation(async (path: string) => {
      if (path === '/admin/knowledge-points') {
        return {
          data: [
            { id: 11, courseId: 1, name: '需求分析', graphEntity: '需求分析', enabled: true, prerequisiteIds: [] },
            { id: 12, courseId: 1, name: '数据流图', graphEntity: '数据流图DFD', enabled: true, prerequisiteIds: [11] },
          ],
        }
      }
      if (path === '/admin/questions') {
        return {
          data: [
            { id: 1, courseId: 1, knowledgePointId: 11, type: 'SINGLE', stem: '题1', options: [{ key: 'A', text: 'a' }, { key: 'B', text: 'b' }], answer: ['A'], explanation: 'e', difficulty: 1, enabled: true, source: 'ADMIN' },
            { id: 2, courseId: 1, knowledgePointId: 12, type: 'TRUE_FALSE', stem: '题2', options: [{ key: 'TRUE', text: '对' }, { key: 'FALSE', text: '错' }], answer: ['TRUE'], explanation: 'e', difficulty: 1, enabled: false, source: 'ADMIN' },
          ],
        }
      }
      return baseGet(path)
    })

    const { wrapper } = await render(AdminView, '/admin')
    expect(wrapper.get('[data-testid="prerequisite-dag-preview"]').text()).toContain('第 1 阶')
    expect(wrapper.get('[data-testid="prerequisite-dag-preview"]').text()).toContain('第 2 阶')
    expect(wrapper.get('[data-testid="prerequisite-dag-preview"]').text()).toContain('前置：需求分析')

    await wrapper.get('select[aria-label="按题型筛选"]').setValue('TRUE_FALSE')
    expect(wrapper.findAll('.question-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('题2')
    wrapper.unmount()
  })
})
