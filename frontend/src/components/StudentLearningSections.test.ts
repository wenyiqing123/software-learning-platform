import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import StudentLearningSections, {
  STUDENT_SECTIONS,
  findGroupByPath,
  resolveItemQuery,
} from './StudentLearningSections.vue'

describe('StudentLearningSections', () => {
  const routes = [
    { path: '/', component: { template: '<div />' } },
    { path: '/dashboard', component: { template: '<div />' } },
    { path: '/courses', component: { template: '<div />' } },
    { path: '/knowledge', component: { template: '<div />' } },
    { path: '/mastery-map', component: { template: '<div />' } },
    { path: '/tutor', component: { template: '<div />' } },
    { path: '/micro-lessons', component: { template: '<div />' } },
    { path: '/practice', component: { template: '<div />' } },
    { path: '/adaptive-practice', component: { template: '<div />' } },
    { path: '/wrong-book', component: { template: '<div />' } },
    { path: '/review', component: { template: '<div />' } },
    { path: '/study-plan', component: { template: '<div />' } },
    { path: '/recommendations', component: { template: '<div />' } },
    { path: '/experiment-tasks', component: { template: '<div />' } },
    { path: '/evaluation', component: { template: '<div />' } },
    { path: '/learning-report', component: { template: '<div />' } },
  ]

  function createTestRouter() {
    return createRouter({
      history: createMemoryHistory(),
      routes,
    })
  }

  it('renders all sections for overview group and highlights active path', async () => {
    const router = createTestRouter()
    const wrapper = mount(StudentLearningSections, {
      props: {
        groupId: 'overview',
        path: '/courses',
      },
      global: { plugins: [router] },
    })

    const links = wrapper.findAll('.student-section-link')
    expect(links).toHaveLength(2)
    expect(links[0].text()).toBe('学习概览')
    expect(links[1].text()).toBe('课程阅读')
    expect(links[1].classes()).toContain('active')
    expect(links[1].attributes('aria-current')).toBe('page')
    expect(links[0].classes()).not.toContain('active')
  })

  it('renders sections for each group correctly', async () => {
    const router = createTestRouter()
    for (const [groupId, expectedItems] of Object.entries(STUDENT_SECTIONS)) {
      const wrapper = mount(StudentLearningSections, {
        props: {
          groupId,
          path: expectedItems[0].path,
        },
        global: { plugins: [router] },
      })

      const links = wrapper.findAll('.student-section-link')
      expect(links).toHaveLength(expectedItems.length)
      expectedItems.forEach((item, index) => {
        expect(links[index].text()).toBe(item.label)
      })
    }
  })

  it('filters course queries to whitelist only', () => {
    const rawQuery = {
      courseId: '101',
      sectionId: '202',
      knowledgePointId: '303',
      entity: 'Stack',
      unrelated: 'ignored',
      token: 'secret',
    }
    const clean = resolveItemQuery('/courses', rawQuery)
    expect(clean).toEqual({
      courseId: '101',
      sectionId: '202',
      knowledgePointId: '303',
      entity: 'Stack',
    })
  })

  it('filters experiment and evaluation queries to whitelist only', () => {
    const rawQuery = {
      taskId: 't-1',
      assignmentId: 'a-1',
      conditionKey: 'cond-A',
      taskKey: 'task-read',
      taskOrder: '1',
      foo: 'bar',
      userId: '123',
    }
    const cleanEval = resolveItemQuery('/evaluation', rawQuery)
    expect(cleanEval).toEqual({
      taskId: 't-1',
      assignmentId: 'a-1',
      conditionKey: 'cond-A',
      taskKey: 'task-read',
      taskOrder: '1',
    })

    const cleanTask = resolveItemQuery('/experiment-tasks', rawQuery)
    expect(cleanTask).toEqual({
      taskId: 't-1',
      assignmentId: 'a-1',
      conditionKey: 'cond-A',
      taskKey: 'task-read',
      taskOrder: '1',
    })
  })

  it('drops all irrelevant queries for non-whitelisted target routes', () => {
    const rawQuery = { foo: 'bar', taskId: '1', courseId: '2' }
    expect(resolveItemQuery('/dashboard', rawQuery)).toBeUndefined()
    expect(resolveItemQuery('/practice', rawQuery)).toBeUndefined()
  })

  it('maps all 15 student canonical paths to correct group', () => {
    const pathGroupMap: Record<string, string> = {
      '/dashboard': 'overview',
      '/courses': 'overview',
      '/knowledge': 'graph-tutor',
      '/mastery-map': 'graph-tutor',
      '/tutor': 'graph-tutor',
      '/micro-lessons': 'graph-tutor',
      '/practice': 'practice',
      '/adaptive-practice': 'practice',
      '/wrong-book': 'practice',
      '/review': 'practice',
      '/study-plan': 'plan',
      '/recommendations': 'plan',
      '/experiment-tasks': 'experiment',
      '/evaluation': 'experiment',
      '/learning-report': 'experiment',
    }

    for (const [path, expectedGroup] of Object.entries(pathGroupMap)) {
      expect(findGroupByPath(path)).toBe(expectedGroup)
    }
    expect(findGroupByPath('/teaching-dashboard')).toBeNull()
  })
})
