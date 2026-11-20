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
})
