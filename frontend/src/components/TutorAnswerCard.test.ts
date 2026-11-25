import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorAnswerCard from './TutorAnswerCard.vue'

vi.mock('./MarkdownContent.vue', () => ({ default: { props: ['content'], template: '<div class="markdown">{{ content }}</div>' } }))

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

    // 证据默认折叠为引用来源胶囊
    const details = wrapper.get('details.evidence-block')
    expect((details.element as HTMLDetailsElement).open).toBe(false)
    expect(details.text()).toContain('已参考 1 条课程图谱证据')
    expect(details.text()).toContain('加工')
    expect(details.text()).toContain('动宾短语')

    // 轻量操作条包含复制、重新生成、分步提示与前往图谱
    expect(wrapper.find('[data-testid="tutor-regenerate"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('复制')
    expect(wrapper.text()).toContain('分步提示')
    expect(wrapper.text()).toContain('前往图谱')
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
})
