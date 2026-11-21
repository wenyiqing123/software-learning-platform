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
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/admin/content', component: AdminContentCenterView },
    { path: '/admin/experiments', component: AdminExperimentCenterView },
  ] })
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
})
