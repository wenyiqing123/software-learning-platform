import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VisualizationViewport from './VisualizationViewport.vue'

describe('VisualizationViewport', () => {
  it('provides a stable viewport with 2D/3D controls and retry state', async () => {
    const wrapper = mount(VisualizationViewport, {
      props: { title: '个人掌握图谱', mode: '2d', error: '图谱暂不可用' },
      slots: { default: '<div data-testid="canvas">canvas</div>' },
    })

    expect(wrapper.text()).toContain('个人掌握图谱')
    expect(wrapper.find('[data-testid="canvas"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="3D 图谱"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('图谱暂不可用')

    await wrapper.get('button[aria-label="重试图谱"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    await wrapper.get('button[aria-label="3D 图谱"]').trigger('click')
    expect(wrapper.emitted('mode-change')?.[0]).toEqual(['3d'])
  })

  it('toggles fullscreen mode with overlay slot and supports Escape exit fallback', async () => {
    const wrapper = mount(VisualizationViewport, {
      props: { title: '个人掌握关系', mode: '2d' },
      slots: {
        default: '<div data-testid="canvas">canvas</div>',
        footer: '<div class="legend">掌握程度</div>',
        overlay: '<div data-testid="fs-overlay">节点学习卡片</div>',
      },
    })

    expect(wrapper.classes()).not.toContain('is-fullscreen')
    expect(wrapper.find('[data-testid="fs-overlay"]').exists()).toBe(false)

    await wrapper.get('button[aria-label="全屏图谱"]').trigger('click')
    expect(wrapper.classes()).toContain('is-fullscreen')
    expect(wrapper.emitted('fullscreen-change')?.[0]).toEqual([true])
    expect(wrapper.find('[data-testid="fs-overlay"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="退出全屏"]').exists()).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.classes()).not.toContain('is-fullscreen')
    expect(wrapper.emitted('fullscreen-change')?.[1]).toEqual([false])
    wrapper.unmount()
  })
})
