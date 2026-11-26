import { describe, expect, it } from 'vitest'
import { buildQuickNoteTags, MAX_NOTE_TAGS, parseNoteTags, SYSTEM_NOTE_TAGS, toggleNoteTag } from './noteTags'

describe('noteTags domain helpers', () => {
  it('parses mixed separators, trims whitespace, deduplicates, and enforces max limit', () => {
    expect(parseNoteTags(' 核心概念， 易错点、核心概念 , 待复习 ')).toEqual(['核心概念', '易错点', '待复习'])
    const twelve = Array.from({ length: 12 }, (_, idx) => `标签${idx + 1}`).join(',')
    expect(parseNoteTags(twelve)).toHaveLength(MAX_NOTE_TAGS)
  })

  it('toggles preset tags cleanly while respecting max tag capacity', () => {
    expect(toggleNoteTag('', '核心概念')).toBe('核心概念')
    expect(toggleNoteTag('核心概念', '易错点')).toBe('核心概念, 易错点')
    expect(toggleNoteTag('核心概念，易错点', '核心概念')).toBe('易错点')

    const ten = Array.from({ length: MAX_NOTE_TAGS }, (_, idx) => `t${idx + 1}`).join(', ')
    expect(toggleNoteTag(ten, '重点记忆')).toBe(ten)
    expect(toggleNoteTag(ten, 't3').split(', ')).not.toContain('t3')
  })

  it('builds quick preset tags with optional context knowledge point tag without duplication', () => {
    expect(buildQuickNoteTags()).toEqual([...SYSTEM_NOTE_TAGS])
    expect(buildQuickNoteTags('数据流图')).toEqual(['数据流图', ...SYSTEM_NOTE_TAGS])
    expect(buildQuickNoteTags('核心概念')).toEqual([...SYSTEM_NOTE_TAGS])
  })
})
