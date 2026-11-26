import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildNotesMarkdown, buildWrongBookMarkdown, triggerMarkdownDownload } from './studyExport'

describe('studyExport', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('builds structured markdown handout from notes and bookmarks', () => {
    const md = buildNotesMarkdown(
      [
        {
          id: 1,
          sectionId: 5,
          title: 'DFD 平衡原则',
          content: '父图与子图数据流必须一致。',
          tags: ['数据流图', '高频'],
          createdAt: '2026-11-26T08:00:00',
          updatedAt: '2026-11-26T10:30:00',
        },
      ],
      [
        {
          targetType: 'SECTION',
          targetId: 5,
          title: '2.1 数据流图建模',
          courseName: '软件工程',
          knowledgePointName: '数据流图',
        },
      ],
      '全部笔记',
    )

    expect(md).toContain('# 知序学习笔记与收藏讲义')
    expect(md).toContain('导出范围：全部笔记')
    expect(md).toContain('### 1. DFD 平衡原则')
    expect(md).toContain('#数据流图 #高频')
    expect(md).toContain('父图与子图数据流必须一致。')
    expect(md).toContain('**【课程小节】2.1 数据流图建模**（软件工程 · 数据流图）')
  })

  it('builds wrong-book markdown handout and triggers blob download cleanup', () => {
    const md = buildWrongBookMarkdown(
      [
        {
          id: 21,
          questionId: 101,
          question: '黑盒测试如何划分等价类？',
          knowledgePoint: '黑盒测试',
          wrongCount: 3,
          status: 'UNMASTERED',
        },
      ],
      '待复习',
    )
    expect(md).toContain('# 知序错题复习讲义')
    expect(md).toContain('关联知识点：黑盒测试 ｜ 历史错误：3 次 ｜ 状态：待复习')

    const createObjectURL = vi.fn(() => 'blob:mock-md')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    triggerMarkdownDownload('notes-handout.md', md)
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-md')
  })
})
