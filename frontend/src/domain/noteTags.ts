export const MAX_NOTE_TAGS = 10

export const SYSTEM_NOTE_TAGS = [
  '核心概念',
  '易错点',
  '待复习',
  '疑难疑问',
  '实战案例',
  '重点记忆',
] as const

/** 解析逗号/顿号分隔的笔记标签并去重截断 */
export function parseNoteTags(raw: string): string[] {
  return [...new Set(raw.split(/[,，、]/).map((tag) => tag.trim()).filter(Boolean))].slice(0, MAX_NOTE_TAGS)
}

/** 在当前标签输入串中切换指定系统标签（已存在则移除，未存在则追加） */
export function toggleNoteTag(raw: string, tag: string): string {
  const normalized = tag.trim()
  if (!normalized) return raw
  const current = parseNoteTags(raw)
  if (current.includes(normalized)) {
    return current.filter((item) => item !== normalized).join(', ')
  }
  if (current.length >= MAX_NOTE_TAGS) {
    return current.join(', ')
  }
  return [...current, normalized].join(', ')
}

/** 结合当前知识点名称与默认系统标签生成快捷选项 */
export function buildQuickNoteTags(contextTag?: string): string[] {
  const trimmed = contextTag?.trim() || ''
  if (trimmed && trimmed.length <= 24 && !(SYSTEM_NOTE_TAGS as readonly string[]).includes(trimmed)) {
    return [trimmed, ...SYSTEM_NOTE_TAGS]
  }
  return [...SYSTEM_NOTE_TAGS]
}
