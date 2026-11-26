import type { LearningBookmarkItem, LearningNote } from '../api/types'

export interface WrongQuestionExportItem {
  id: number
  questionId: number
  question: string
  knowledgePoint?: string
  wrongCount: number
  status?: string
}

function formatDateTime(value?: string): string {
  if (!value) return new Date().toISOString().slice(0, 16).replace('T', ' ')
  return value.replace('T', ' ').slice(0, 16)
}

export function buildNotesMarkdown(
  notes: LearningNote[],
  bookmarks: LearningBookmarkItem[],
  filterLabel = '全部记录',
): string {
  const lines: string[] = [
    '# 知序学习笔记与收藏讲义',
    '',
    `- 导出范围：${filterLabel}`,
    `- 导出时间：${formatDateTime()}`,
    `- 笔记条目：${notes.length} 条 · 收藏内容：${bookmarks.length} 项`,
    '',
  ]

  lines.push('## 一、个人学习笔记', '')
  if (!notes.length) {
    lines.push('_当前筛选范围内暂无笔记。_', '')
  } else {
    notes.forEach((note, index) => {
      const title = (note.title || '').trim() || `笔记 #${index + 1}`
      const tags = (note.tags || []).map((t) => `#${t.trim()}`).filter(Boolean).join(' ')
      const time = formatDateTime(note.updatedAt || note.createdAt)
      lines.push(`### ${index + 1}. ${title}`, '')
      lines.push(`> 更新时间：${time}${tags ? ` ｜ 标签：${tags}` : ''}`, '')
      lines.push(note.content.trim(), '')
    })
  }

  lines.push('## 二、重点收藏清单', '')
  if (!bookmarks.length) {
    lines.push('_暂无收藏内容。_', '')
  } else {
    bookmarks.forEach((item, index) => {
      const kind = item.targetType === 'SECTION' ? '课程小节' : '知识点'
      const meta = [item.courseName, item.knowledgePointName].filter(Boolean).join(' · ')
      lines.push(`${index + 1}. **【${kind}】${item.title}**${meta ? `（${meta}）` : ''}`)
    })
    lines.push('')
  }

  return lines.join('\n')
}

export function buildWrongBookMarkdown(
  items: WrongQuestionExportItem[],
  filterLabel = '待复习题目',
): string {
  const lines: string[] = [
    '# 知序错题复习讲义',
    '',
    `- 筛选范围：${filterLabel}`,
    `- 导出时间：${formatDateTime()}`,
    `- 收录题目：${items.length} 道`,
    '',
  ]

  if (!items.length) {
    lines.push('_当前筛选条件下没有错题记录。_', '')
    return lines.join('\n')
  }

  items.forEach((item, index) => {
    const statusText = item.status === 'MASTERED' ? '已掌握' : '待复习'
    lines.push(`### ${index + 1}. ${item.question.trim()}`, '')
    lines.push(
      `- 关联知识点：${item.knowledgePoint || '未标注'} ｜ 历史错误：${item.wrongCount} 次 ｜ 状态：${statusText}`,
      '',
    )
  })

  return lines.join('\n')
}

export function triggerMarkdownDownload(filename: string, markdown: string) {
  if (typeof document === 'undefined' || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') {
    return
  }
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
