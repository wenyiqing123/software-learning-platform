import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MermaidDiagram from './MermaidDiagram.vue'

const mermaidApi = vi.hoisted(() => ({
  initialize: vi.fn(),
  parse: vi.fn(),
  render: vi.fn(),
}))

vi.mock('mermaid', () => ({
  default: mermaidApi,
}))

describe('MermaidDiagram', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mermaidApi.parse.mockResolvedValue(true)
    mermaidApi.render.mockResolvedValue({
      svg: '<svg xmlns="http://www.w3.org/2000/svg" width="240" height="80"><script>alert(1)</script><g onclick="alert(2)"><rect width="100" height="40" /></g><a href="javascript:alert(3)"><text>Bad</text></a></svg>',
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('lazy-loads mermaid in strict security mode, sanitizes svg, and renders diagram stage', async () => {
    const wrapper = mount(MermaidDiagram, {
      props: {
        code: 'flowchart LR\n  A[需求分析] --> B[规格说明]',
      },
    })

    await flushPromises()

    expect(mermaidApi.initialize).toHaveBeenCalledWith(
      expect.objectContaining({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: 'base',
      }),
    )
    expect(mermaidApi.parse).toHaveBeenCalledWith('flowchart LR\n  A[需求分析] --> B[规格说明]')
    expect(mermaidApi.render).toHaveBeenCalledTimes(1)

    const stageHtml = wrapper.get('[data-testid="mermaid-svg-stage"]').html()
    expect(stageHtml).toContain('<svg')
    expect(stageHtml).not.toContain('<script')
    expect(stageHtml).not.toContain('onclick')
    expect(stageHtml).not.toContain('javascript:')
    expect(wrapper.find('[data-testid="mermaid-syntax-fallback"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('supports switching between diagram view and source view', async () => {
    const wrapper = mount(MermaidDiagram, {
      props: {
        code: 'sequenceDiagram\n  User->>System: Request',
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="mermaid-svg-stage"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mermaid-source-view"]').exists()).toBe(false)

    await wrapper.get('[data-testid="mermaid-tab-source"]').trigger('click')
    expect(wrapper.get('[data-testid="mermaid-source-view"]').text()).toContain('sequenceDiagram')
    expect(wrapper.find('[data-testid="mermaid-svg-stage"]').exists()).toBe(false)

    await wrapper.get('[data-testid="mermaid-tab-diagram"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="mermaid-svg-stage"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('copies mermaid source with status feedback and handles clipboard failures', async () => {
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('denied'))
    vi.spyOn(window, 'navigator', 'get').mockReturnValue({
      clipboard: { writeText },
    } as unknown as Navigator)

    const wrapper = mount(MermaidDiagram, {
      props: { code: 'graph TD\n  A-->B' },
    })
    await flushPromises()

    await wrapper.get('[data-testid="mermaid-copy-btn"]').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('graph TD\n  A-->B')
    expect(wrapper.get('[role="status"]').text()).toBe('已复制源码')

    await wrapper.get('[data-testid="mermaid-copy-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toBe('复制失败')
    wrapper.unmount()
  })

  it('supports zoom modal controls, escape close, and svg download', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock-svg')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const wrapper = mount(MermaidDiagram, {
      props: { code: 'flowchart LR\n  A --> B' },
      attachTo: document.body,
    })
    await flushPromises()

    await wrapper.get('[data-testid="mermaid-zoom-btn"]').trigger('click')
    await flushPromises()

    const zoomModal = document.body.querySelector('[data-testid="mermaid-zoom-modal"]')
    expect(zoomModal).not.toBeNull()
    expect(zoomModal?.textContent).toContain('100%')

    const zoomInBtn = document.body.querySelector('[data-testid="mermaid-zoom-in"]') as HTMLButtonElement
    zoomInBtn.click()
    await flushPromises()
    expect(zoomModal?.textContent).toContain('120%')

    const resetBtn = document.body.querySelector('[data-testid="mermaid-zoom-reset"]') as HTMLButtonElement
    resetBtn.click()
    await flushPromises()
    expect(zoomModal?.textContent).toContain('100%')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(document.body.querySelector('[data-testid="mermaid-zoom-modal"]')).toBeNull()

    await wrapper.get('[data-testid="mermaid-download-btn"]').trigger('click')
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-svg')

    wrapper.unmount()
  })

  it('degrades gracefully to source view with honest notice on invalid mermaid syntax', async () => {
    mermaidApi.parse.mockRejectedValueOnce(new Error('Syntax error at line 1'))

    const wrapper = mount(MermaidDiagram, {
      props: {
        code: 'invalid ===>>> mermaid',
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="mermaid-syntax-fallback"]').text()).toContain('图表语法暂无法渲染，已切换为源码视图')
    expect(wrapper.get('[data-testid="mermaid-source-view"]').text()).toContain('invalid ===>>> mermaid')
    expect(wrapper.get('[data-testid="mermaid-tab-diagram"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="mermaid-zoom-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="mermaid-download-btn"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
