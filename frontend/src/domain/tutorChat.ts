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

/** 获取指定消息所属提问轮次内已经展开过的分步提示层级集合 */
export function expandedHintLevelsInTurn(messages: TutorChatMessage[], messageIndex: number): Set<number> {
  const levels = new Set<number>()
  if (messageIndex < 0 || messageIndex >= messages.length) return levels

  let turnStart = 0
  for (let i = messageIndex; i >= 0; i--) {
    if (messages[i].role === 'USER') {
      turnStart = i + 1
      break
    }
  }

  let turnEnd = messages.length
  for (let i = messageIndex + 1; i < messages.length; i++) {
    if (messages[i].role === 'USER') {
      turnEnd = i
      break
    }
  }

  for (let i = turnStart; i < turnEnd; i++) {
    const level = messages[i]?.metadata?.hint?.level
    if (typeof level === 'number') {
      levels.add(level)
    }
  }
  return levels
}

/** 判断当前最新提问轮次是否已生成对应层级的分步提示 */
export function hasHintLevelInLatestTurn(
  messages: TutorChatMessage[],
  targetLevel: number,
  question?: string
): boolean {
  let turnStart = 0
  let turnQuestion = ''
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].role === 'USER') {
      turnStart = i + 1
      turnQuestion = messages[i].content.trim()
      break
    }
  }

  const normalizedTarget = (question || '').trim()
  for (let i = turnStart; i < messages.length; i++) {
    const hint = messages[i]?.metadata?.hint
    if (!hint || hint.level !== targetLevel) continue
    const hintQuestion = (hint.question || turnQuestion || '').trim()
    if (!normalizedTarget || !hintQuestion || hintQuestion === normalizedTarget) {
      return true
    }
  }
  return false
}
