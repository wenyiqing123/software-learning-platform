import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import LearningContextActions from './LearningContextActions.vue'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>Home</div>' } },
      { path: '/courses', component: { template: '<div>Courses</div>' } },
      { path: '/knowledge', component: { template: '<div>Knowledge</div>' } },
      { path: '/tutor', component: { template: '<div>Tutor</div>' } },
      { path: '/practice', component: { template: '<div>Practice</div>' } },
    ],
  })
}

describe('LearningContextActions', () => {
  it('renders breadcrumbs path when context titles or ids are present', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const wrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: {
        context: {
          courseId: 10,
          courseTitle: '软件工程导论',
          sectionId: 2,
          sectionTitle: '需求工程',
          knowledgePointId: 5,
          knowledgePointTitle: '数据流图分析',
        },
      },
    })

    const breadcrumbs = wrapper.find('.context-breadcrumbs')
    expect(breadcrumbs.exists()).toBe(true)
    expect(breadcrumbs.text()).toContain('软件工程导论')
    expect(breadcrumbs.text()).toContain('需求工程')
    expect(breadcrumbs.text()).toContain('数据流图分析')
  })

  it('preserves query parameters across navigation links', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const wrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: {
        context: {
          courseId: 1,
          sectionId: 8,
          knowledgePointId: 12,
          entity: '数据流图DFD',
        },
      },
    })

    const graphLink = wrapper.find('.context-graph-link')
    expect(graphLink.exists()).toBe(true)
    expect(graphLink.attributes('href')).toContain('courseId=1')
    expect(graphLink.attributes('href')).toContain('sectionId=8')
    expect(graphLink.attributes('href')).toContain('knowledgePointId=12')
    expect(graphLink.attributes('href')).toContain('entity=' + encodeURIComponent('数据流图DFD'))

    const tutorLink = wrapper.find('.context-tutor-link')
    expect(tutorLink.exists()).toBe(true)
    expect(tutorLink.attributes('href')).toContain('courseId=1')
    expect(tutorLink.attributes('href')).toContain('entity=' + encodeURIComponent('数据流图DFD'))

    const feynmanLink = wrapper.find('.context-feynman-link')
    expect(feynmanLink.exists()).toBe(true)
    expect(feynmanLink.attributes('href')).toContain('activity=feynman')
    expect(feynmanLink.attributes('href')).toContain('entity=' + encodeURIComponent('数据流图DFD'))

    const practiceLink = wrapper.find('.context-practice-link')
    expect(practiceLink.exists()).toBe(true)
    expect(practiceLink.attributes('href')).toContain('courseId=1')
    expect(practiceLink.attributes('href')).toContain('sectionId=8')
  })

  it('safely falls back to /courses without error when context is missing', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const wrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: {
        context: null,
        showReturn: true,
      },
    })

    expect(wrapper.find('.context-breadcrumbs').exists()).toBe(false)
    const returnLink = wrapper.find('.context-return-link')
    expect(returnLink.exists()).toBe(true)
    expect(returnLink.attributes('href')).toBe('/courses')
  })

  it('deduplicates actions based on currentView prop', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const context = {
      courseId: 2,
      sectionId: 4,
      knowledgePointId: 6,
      entity: '用例规约',
    }

    // 在 graph 页面：隐藏 graph 链接，展示返回链接与其他链接
    const graphWrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: { context, currentView: 'graph' },
    })
    expect(graphWrapper.find('.context-graph-link').exists()).toBe(false)
    expect(graphWrapper.find('.context-return-link').exists()).toBe(true)
    expect(graphWrapper.find('.context-tutor-link').exists()).toBe(true)
    expect(graphWrapper.find('.context-practice-link').exists()).toBe(true)

    // 在 tutor 页面：隐藏 tutor 与 feynman 链接
    const tutorWrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: { context, currentView: 'tutor' },
    })
    expect(tutorWrapper.find('.context-tutor-link').exists()).toBe(false)
    expect(tutorWrapper.find('.context-feynman-link').exists()).toBe(false)
    expect(tutorWrapper.find('.context-graph-link').exists()).toBe(true)
    expect(tutorWrapper.find('.context-practice-link').exists()).toBe(true)

    // 在 practice 页面：隐藏 practice 链接
    const practiceWrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: { context, currentView: 'practice' },
    })
    expect(practiceWrapper.find('.context-practice-link').exists()).toBe(false)
    expect(practiceWrapper.find('.context-graph-link').exists()).toBe(true)
    expect(practiceWrapper.find('.context-tutor-link').exists()).toBe(true)

    // 在 course 页面：隐藏 return 链接（当前已在课程内）
    const courseWrapper = mount(LearningContextActions, {
      global: { plugins: [router] },
      props: { context, currentView: 'course' },
    })
    expect(courseWrapper.find('.context-return-link').exists()).toBe(false)
    expect(courseWrapper.find('.context-graph-link').exists()).toBe(true)
    expect(courseWrapper.find('.context-tutor-link').exists()).toBe(true)
    expect(courseWrapper.find('.context-practice-link').exists()).toBe(true)
  })
})
