import type { TutorHintResult } from '../api/types'

/** 助教页面在后台静默锁定的知识图谱增强模式 */
export const LOCKED_TUTOR_MODE = 'RANKED' as const

export interface TutorTriple {
  subject: string
  predicate: string
  object: string
  score?: number
}

export interface TutorAnswerResult {
  mode?: string
  answer?: string
  content?: string
  error?: string
  entities?: { canonicalName: string }[]
  triples?: TutorTriple[]
  latencyMs?: number
  prompt?: string
}

export interface TutorChatMessage {
  id?: number
  role: string
  content: string
  mode?: string
  metadata?: {
    results?: TutorAnswerResult[]
    hint?: TutorHintResult
    incomplete?: boolean
    [key: string]: unknown
  }
}

export interface TutorContextChip {
  label: string
  value: string
}

export interface TutorQuickPrompt {
  tag: string
  title: string
  prompt: string
}

const LEGACY_MODE_PREFIX_RE = /^\s*\[(?:DIRECT|TRIPLES|RANKED|COMPARE)\]\s*/i

/** 清洗历史消息正文开头的旧模式标签前缀，避免实验术语透出到日常助教对话 */
export function stripLegacyModePrefix(content: string): string {
  return String(content || '').replace(LEGACY_MODE_PREFIX_RE, '')
}
