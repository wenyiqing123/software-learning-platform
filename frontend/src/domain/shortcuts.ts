export interface ShortcutEntry {
  keys: string[]
  separator?: string
  label: string
}

export interface ShortcutGroup {
  id: string
  title: string
  items: ShortcutEntry[]
}

const EDITABLE_SELECTOR = 'input, textarea, select, [contenteditable], .el-input__inner, .el-textarea__inner'

function isNodeEditable(node: unknown): boolean {
  if (!node || typeof Element === 'undefined' || !(node instanceof Element)) return false
  if ((node as HTMLElement).isContentEditable) return true
  if (node.matches(EDITABLE_SELECTOR)) return true
  return Boolean(node.closest('input, textarea, select, [contenteditable="true"], [contenteditable=""]'))
}

/** 严格判断当前键盘事件是否发生于可编辑控件或输入法组合态，避免输入 `?` 误触快捷键弹窗 */
export function isEditableShortcutContext(event: KeyboardEvent): boolean {
  if (event.isComposing || event.keyCode === 229) return true
  if (event.ctrlKey || event.metaKey || event.altKey) return true
  if (isNodeEditable(event.target)) return true
  if (typeof document !== 'undefined' && isNodeEditable(document.activeElement)) return true
  return false
}

export function getShortcutGroups(role?: string): ShortcutGroup[] {
  const isAdmin = role === 'ADMIN'
  const groups: ShortcutGroup[] = [
    {
      id: 'global',
      title: '全局导航与界面控制',
      items: [
        { keys: ['Ctrl', 'K'], label: '打开全局快速检索与命令面板（macOS 为 ⌘ + K）' },
        { keys: ['?'], label: '打开或关闭当前快捷键速查浮层' },
        { keys: ['Esc'], label: '关闭当前弹窗、抽屉、浮层或退出全屏画布' },
      ],
    },
  ]

  if (!isAdmin) {
    groups.push(
      {
        id: 'tutor',
        title: 'AI 学习助教',
        items: [
          { keys: ['Enter'], label: '发送当前提问（输入法选词期间自动拦截）' },
          { keys: ['Shift', 'Enter'], label: '在多行提问框内插入换行' },
        ],
      },
      {
        id: 'flashcards',
        title: '概念闪卡主动回忆',
        items: [
          { keys: ['Space'], label: '翻转卡片查看参考要点与学习目标' },
          { keys: ['←', '→'], separator: '/', label: '切换上一张或下一张概念闪卡' },
          { keys: ['1', '4'], separator: '~', label: '翻面后按重记 / 较难 / 记得 / 熟练直接评分' },
        ],
      },
      {
        id: 'study',
        title: '课程阅读与知识图谱',
        items: [
          { keys: ['Ctrl', 'K'], label: '跨课程快速检索知识点、个人笔记或助教历史会话' },
          { keys: ['Esc'], label: '关闭记笔记弹窗或退出图谱点阵全屏视图' },
        ],
      },
    )
  }

  return groups
}
