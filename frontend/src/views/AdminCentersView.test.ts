import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminContentCenterView from './AdminContentCenterView.vue'
import AdminExperimentCenterView from './AdminExperimentCenterView.vue'

vi.mock('./AdminView.vue', () => ({ default: { template: '<div data-testid="course-center" />' } }))
vi.mock('./QuestionBankView.vue', () => ({ default: { template: '<div data-testid="question-center" />' } }))
vi.mock('./AdminDiagnosisView.vue', () => ({ default: { template: '<div data-testid="diagnosis-center" />' } }))
vi.mock('./MicroLessonAdminView.vue', () => ({ default: { template: '<div data-testid="micro-center" />' } }))
vi.mock('./ExperimentsView.vue', () => ({ default: { template: '<div data-testid="experiment-center" />' } }))
vi.mock('./ExperimentTaskAdminView.vue', () => ({ default: { template: '<div data-testid="task-center" />' } }))
vi.mock('./EvaluationAdminView.vue', () => ({ default: { template: '<div data-testid="evaluation-center" />' } }))

async function mountWith(component: any, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/content', component: AdminContentCenterView },
      { path: '/admin/experiments', component: AdminExperimentCenterView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('administrator workbench centers', () => {
  it('switches content and question tabs without changing the outer route', async () => {
    const { wrapper, router } = await mountWith(AdminContentCenterView, '/admin/content?tab=course')
    expect(wrapper.find('[data-testid="course-center"]').exists()).toBe(true)
    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content')
    expect(router.currentRoute.value.query.tab).toBe('question-bank')
    expect(wrapper.find('[data-testid="question-center"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('switches experiment, task and evaluation tabs', async () => {
    const { wrapper, router } = await mountWith(AdminExperimentCenterView, '/admin/experiments')
    expect(wrapper.find('[data-testid="experiment-center"]').exists()).toBe(true)
    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tab).toBe('tasks')
    expect(wrapper.find('[data-testid="task-center"]').exists()).toBe(true)
    await wrapper.findAll('[role="tab"]')[2].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tab).toBe('evaluation')
    expect(wrapper.find('[data-testid="evaluation-center"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('mounts all 4 content center tabs correctly via direct deep link', async () => {
    const cases = [
      { tab: 'course', testId: 'course-center', tabTestId: 'tab-course' },
      { tab: 'question-bank', testId: 'question-center', tabTestId: 'tab-question-bank' },
      { tab: 'diagnosis', testId: 'diagnosis-center', tabTestId: 'tab-diagnosis' },
      { tab: 'micro-lessons', testId: 'micro-center', tabTestId: 'tab-micro-lessons' },
    ]

    for (const c of cases) {
      const { wrapper } = await mountWith(AdminContentCenterView, `/admin/content?tab=${c.tab}`)
      expect(wrapper.find(`[data-testid="${c.testId}"]`).exists()).toBe(true)
      const tabBtn = wrapper.find(`[data-testid="${c.tabTestId}"]`)
      expect(tabBtn.exists()).toBe(true)
      expect(tabBtn.attributes('aria-selected')).toBe('true')
      expect(tabBtn.classes()).toContain('active')
      wrapper.unmount()
    }
  })

  it('mounts all 3 experiment center tabs correctly via direct deep link', async () => {
    const cases = [
      { tab: 'comparison', testId: 'experiment-center', tabTestId: 'tab-comparison' },
      { tab: 'tasks', testId: 'task-center', tabTestId: 'tab-tasks' },
      { tab: 'evaluation', testId: 'evaluation-center', tabTestId: 'tab-evaluation' },
    ]

    for (const c of cases) {
      const { wrapper } = await mountWith(AdminExperimentCenterView, `/admin/experiments?tab=${c.tab}`)
      expect(wrapper.find(`[data-testid="${c.testId}"]`).exists()).toBe(true)
      const tabBtn = wrapper.find(`[data-testid="${c.tabTestId}"]`)
      expect(tabBtn.exists()).toBe(true)
      expect(tabBtn.attributes('aria-selected')).toBe('true')
      expect(tabBtn.classes()).toContain('active')
      wrapper.unmount()
    }
  })

  it('renders exactly one top-level H1 per admin center page', async () => {
    const content = await mountWith(AdminContentCenterView, '/admin/content')
    expect(content.wrapper.findAll('h1')).toHaveLength(1)
    expect(content.wrapper.find('h1').text()).toBe('内容与题库中心')
    content.wrapper.unmount()

    const experiment = await mountWith(AdminExperimentCenterView, '/admin/experiments')
    expect(experiment.wrapper.findAll('h1')).toHaveLength(1)
    expect(experiment.wrapper.find('h1').text()).toBe('实验与评价中心')
    experiment.wrapper.unmount()
  })

  it('complies with ARIA tablist/tab specifications', async () => {
    const { wrapper } = await mountWith(AdminContentCenterView, '/admin/content?tab=diagnosis')
    const tablist = wrapper.find('[role="tablist"]')
    expect(tablist.exists()).toBe(true)
    expect(tablist.attributes('aria-label')).toBe('内容与题库中心')

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(4)
    expect(tabs[0].attributes('aria-selected')).toBe('false')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(tabs[2].attributes('aria-selected')).toBe('true')
    expect(tabs[3].attributes('aria-selected')).toBe('false')
    wrapper.unmount()
  })

  it('restores the selected tab on query refresh', async () => {
    const { wrapper, router } = await mountWith(AdminExperimentCenterView, '/admin/experiments?tab=tasks')
    expect(wrapper.find('[data-testid="task-center"]').exists()).toBe(true)

    // 模拟刷新 / 切换 query
    await router.replace({ path: '/admin/experiments', query: { tab: 'evaluation' } })
    await flushPromises()
    expect(wrapper.find('[data-testid="evaluation-center"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tab-evaluation"]').attributes('aria-selected')).toBe('true')
    wrapper.unmount()
  })
})
