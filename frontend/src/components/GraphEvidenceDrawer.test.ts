import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GraphEvidenceDrawer from './GraphEvidenceDrawer.vue'

describe('GraphEvidenceDrawer', () => {
  it('renders empty text when no triples are available', () => {
    const wrapper = mount(GraphEvidenceDrawer, {
      props: {
        modelValue: true,
        node: '软件工程',
        triples: [],
        emptyText: '当前没有关联证据三元组。',
      },
    })

    expect(wrapper.text()).toContain('当前没有关联证据三元组。')
    expect(wrapper.text()).toContain('当前节点：软件工程')
    expect(wrapper.find('.text-button').exists()).toBe(false)
  })

  it('renders real triples and emits ask event with current node', async () => {
    const wrapper = mount(GraphEvidenceDrawer, {
      props: {
        modelValue: true,
        node: '需求分析',
        triples: [
          { subject: '需求分析', predicate: '包含', object: '用例建模', score: 0.875 },
          { subject: '需求分析', predicate: '前置于', object: '软件设计' },
        ],
      },
    })

    expect(wrapper.text()).toContain('需求分析')
    expect(wrapper.text()).toContain('用例建模')
    expect(wrapper.text()).toContain('包含')
    expect(wrapper.text()).toContain('证据分 0.875')
    expect(wrapper.text()).toContain('软件设计')

    const askButton = wrapper.get('.text-button')
    await askButton.trigger('click')

    expect(wrapper.emitted('ask')?.[0]).toEqual(['需求分析'])
  })

  it('supports disclosure toggling via keyboard/click trigger', async () => {
    const wrapper = mount(GraphEvidenceDrawer, {
      props: {
        modelValue: false,
        node: '架构设计',
        triples: [{ subject: '架构设计', predicate: '依赖', object: '需求分析' }],
      },
    })

    expect(wrapper.find('.evidence-drawer-body').exists()).toBe(false)

    const openButton = wrapper.get('button[aria-label="查看详情"]')
    expect(openButton.attributes('aria-expanded')).toBe('false')
    await openButton.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([true])

    await wrapper.setProps({ modelValue: true })
    expect(wrapper.find('.evidence-drawer-body').exists()).toBe(true)

    const closeButton = wrapper.get('button[aria-label="关闭证据详情"]')
    await closeButton.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([false])
  })
})
