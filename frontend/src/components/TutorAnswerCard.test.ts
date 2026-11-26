import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TutorAnswerCard from './TutorAnswerCard.vue'

afterEach(() => vi.useRealTimers())

describe('TutorAnswerCard', () => {
  it('never renders backend system prompt or mode badges while keeping evidence collapsible', () => {
    const wrapper = mount(TutorAnswerCard, {
      props: {
        canRegenerate: true,
        item: {
          mode: 'RANKED',
          answer: '[RANKED] 加工命名使用动宾短语。',
          prompt: ['server', 'only', 'constraint'].join('-'),
          entities: [{ canonicalName: '数据流图' }],
          triples: [{ subject: '加工', predicate: '命名', object: '动宾短语', score: 0.9 }],
        },
      },
    })

    // 不渲染后台 prompt 与实验模式标签
    expect(wrapper.text()).not.toContain('server-only-constraint')
    expect(wrapper.text()).not.toContain('RANKED')
    expect(wrapper.text()).not.toContain('TRIPLES')
    expect(wrapper.text()).not.toContain('DIRECT')
    expect(wrapper.text()).toContain('加工命名使用动宾短语。')
    expect(wrapper.text()).not.toContain('正确率')
    expect(wrapper.text()).not.toContain('置信度')

    // 证据默认折叠为引用来源胶囊并附客观实体覆盖概览
    const details = wrapper.get('details.evidence-block')
    expect((details.element as HTMLDetailsElement).open).toBe(false)
    expect(details.text()).toContain('已参考 1 条课程图谱证据')
    expect(wrapper.get('[data-testid="evidence-coverage-pill"]').text()).toBe('回复提及了 1/1 条已检索图谱证据中的实体')
    expect(details.text()).toContain('加工')
    expect(details.text()).toContain('动宾短语')

    // 轻量操作条包含复制、重新生成、分步提示与前往图谱
    expect(wrapper.find('[data-testid="tutor-regenerate"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('复制')
    expect(wrapper.text()).toContain('分步提示')
    expect(wrapper.text()).toContain('前往图谱')
  })

  it('expands evidence drawer and highlights matching triple when inline citation [k] is clicked', async () => {
    vi.useFakeTimers()
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView

    const wrapper = mount(TutorAnswerCard, {
      props: {
        item: {
          answer: '需求分析阶段输出软件需求规格说明书。之后根据用例建模。',
          triples: [
            { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书' },
            { subject: '设计模式', predicate: '包含', object: '工厂模式' },
          ],
        },
      },
    })

    const details = wrapper.get('details.evidence-block')
    expect((details.element as HTMLDetailsElement).open).toBe(false)
    expect(wrapper.get('[data-testid="evidence-coverage-pill"]').text()).toBe('回复提及了 1/2 条已检索图谱证据中的实体')

    const citeBtn = wrapper.get('[data-testid="inline-citation-1"]')
    expect(wrapper.find('[data-testid="inline-citation-2"]').exists()).toBe(false)

    await citeBtn.trigger('click')
    await flushPromises()

    expect((details.element as HTMLDetailsElement).open).toBe(true)
    const highlightedRow = wrapper.get('[data-testid="evidence-triple-1"]')
    expect(highlightedRow.classes()).toContain('is-highlighted')
    expect(scrollIntoView).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2500)
    await flushPromises()
    expect(highlightedRow.classes()).not.toContain('is-highlighted')
  })

  it('never renders fake citations or coverage badge when triples are empty', () => {
    const wrapper = mount(TutorAnswerCard, {
      props: {
        item: {
          answer: '需求分析阶段输出软件需求规格说明书。',
          triples: [],
        },
      },
    })

    expect(wrapper.findAll('.inline-citation-btn')).toHaveLength(0)
    expect(wrapper.find('[data-testid="evidence-coverage-pill"]').exists()).toBe(false)
    expect(wrapper.find('details.evidence-block').exists()).toBe(false)
  })

  it('shows honest error text and hides action bar when failed', async () => {
    const wrapper = mount(TutorAnswerCard, {
      props: {
        item: {
          mode: 'RANKED',
          error: '知识服务不可用',
        },
      },
    })

    expect(wrapper.text()).toContain('知识服务不可用')
    expect(wrapper.find('.tutor-answer-actions').exists()).toBe(false)
  })

  it('emits copy, regenerate, hint, and open-graph events', async () => {
    const wrapper = mount(TutorAnswerCard, {
      props: {
        canRegenerate: true,
        item: {
          answer: '需求分析产生软件需求规格说明书。',
          entities: [{ canonicalName: '需求分析' }],
          triples: [{ subject: '需求分析', predicate: '交付文档', object: '软件需求规格说明书' }],
        },
      },
    })

    const actionButtons = wrapper.findAll('.tutor-action-btn')
    await actionButtons[0].trigger('click')
    await actionButtons[1].trigger('click')
    await actionButtons[2].trigger('click')
    await actionButtons[3].trigger('click')

    expect(wrapper.emitted('copy')?.[0]).toEqual(['需求分析产生软件需求规格说明书。'])
    expect(wrapper.emitted('regenerate')).toHaveLength(1)
    expect(wrapper.emitted('hint')).toHaveLength(1)
    expect(wrapper.emitted('open-graph')?.[0]).toEqual(['需求分析'])
  })

  it('opens graph navigation from citation hover popover link without leaking backend prompts', async () => {
    const wrapper = mount(TutorAnswerCard, {
      props: {
        item: {
          mode: 'RANKED',
          prompt: 'RANKED_SYSTEM_SECRET_CONSTRAINT',
          answer: '需求分析输出软件需求规格说明书。',
          triples: [
            { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书' },
          ],
        },
      },
    })

    const citeBtn = wrapper.get('[data-testid="inline-citation-1"]')
    await citeBtn.trigger('mouseenter')
    const popover = wrapper.get('[data-testid="citation-popover-1"]')
    expect(popover.classes()).toContain('is-open')
    expect(popover.text()).toContain('需求分析')
    expect(popover.text()).toContain('软件需求规格说明书')
    expect(popover.text()).not.toContain('RANKED_SYSTEM_SECRET_CONSTRAINT')

    await wrapper.get('[data-testid="citation-popover-graph-1"]').trigger('click')
    expect(wrapper.emitted('open-graph')?.[0]).toEqual(['需求分析'])
  })
})
