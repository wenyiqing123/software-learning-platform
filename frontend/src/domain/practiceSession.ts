export interface PracticeQuestionLike {
  id: number
  stem: string
  knowledgePoint?: string
  options?: Array<{ key: string; text: string }>
}

export interface PracticeQuestionOutcome {
  questionId: number
  stem: string
  knowledgePoint?: string
  selected: string[]
  correct: boolean
  correctAnswer: string[]
  explanation?: string
}

export type NavigatorQuestionStatus = 'unanswered' | 'draft' | 'correct' | 'wrong'
export type PracticeReviewFilter = 'all' | 'wrong' | 'flagged'

export interface NavigatorSlot {
  index: number
  numberLabel: string
  questionId: number
  status: NavigatorQuestionStatus
  flagged: boolean
  isCurrent: boolean
}

export interface PracticeReviewEntry {
  index: number
  numberLabel: string
  questionId: number
  stem: string
  knowledgePoint?: string
  flagged: boolean
  outcome?: PracticeQuestionOutcome
}

export interface PracticeSessionSummary {
  total: number
  answeredCount: number
  correctCount: number
  wrongCount: number
  flaggedCount: number
  accuracyLabel: string
  missedPoints: string[]
}

/**
 * 构建练习答题卡题号槽位状态。
 * 严格遵循考前保密边界：未提交题目仅区分为 unanswered / draft（本地暂存选项），绝不包含任何答案信息。
 */
export function buildNavigatorSlots(
  questions: ReadonlyArray<PracticeQuestionLike>,
  state: {
    currentIndex: number
    draftsById: Readonly<Record<number, string[]>>
    outcomesById: Readonly<Record<number, PracticeQuestionOutcome>>
    flaggedIds: ReadonlySet<number> | ReadonlyArray<number>
  },
): NavigatorSlot[] {
  const flaggedSet = state.flaggedIds instanceof Set ? state.flaggedIds : new Set(state.flaggedIds)
  return questions.map((q, idx) => {
    const outcome = state.outcomesById[q.id]
    const draft = state.draftsById[q.id]
    let status: NavigatorQuestionStatus = 'unanswered'
    if (outcome) {
      status = outcome.correct ? 'correct' : 'wrong'
    } else if (Array.isArray(draft) && draft.length > 0) {
      status = 'draft'
    }
    return {
      index: idx,
      numberLabel: String(idx + 1).padStart(2, '0'),
      questionId: q.id,
      status,
      flagged: flaggedSet.has(q.id),
      isCurrent: idx === state.currentIndex,
    }
  })
}

/**
 * 生成本轮练习汇总统计（全部基于已提交判分的确定性统计）。
 */
export function computeSessionSummary(
  questions: ReadonlyArray<PracticeQuestionLike>,
  outcomesById: Readonly<Record<number, PracticeQuestionOutcome>>,
  flaggedIds: ReadonlySet<number> | ReadonlyArray<number>,
): PracticeSessionSummary {
  const flaggedSet = flaggedIds instanceof Set ? flaggedIds : new Set(flaggedIds)
  const outcomes = questions
    .map((q) => outcomesById[q.id])
    .filter((item): item is PracticeQuestionOutcome => Boolean(item))
  const answeredCount = outcomes.length
  const correctCount = outcomes.filter((item) => item.correct).length
  const wrongCount = answeredCount - correctCount
  const missedPoints = Array.from(
    new Set(
      outcomes
        .filter((item) => !item.correct && item.knowledgePoint?.trim())
        .map((item) => item.knowledgePoint!.trim()),
    ),
  )

  return {
    total: questions.length,
    answeredCount,
    correctCount,
    wrongCount,
    flaggedCount: questions.filter((q) => flaggedSet.has(q.id)).length,
    accuracyLabel: answeredCount > 0 ? `${Math.round((correctCount / answeredCount) * 100)}%` : '—',
    missedPoints,
  }
}

/**
 * 按筛选维度输出逐题复盘清单；未提交题目的 outcome 保持 undefined，严防提前泄露答案。
 */
export function computeReviewMatrix(
  questions: ReadonlyArray<PracticeQuestionLike>,
  outcomesById: Readonly<Record<number, PracticeQuestionOutcome>>,
  flaggedIds: ReadonlySet<number> | ReadonlyArray<number>,
  filter: PracticeReviewFilter = 'all',
): PracticeReviewEntry[] {
  const flaggedSet = flaggedIds instanceof Set ? flaggedIds : new Set(flaggedIds)
  return questions
    .map((q, idx) => ({
      index: idx,
      numberLabel: String(idx + 1).padStart(2, '0'),
      questionId: q.id,
      stem: q.stem,
      knowledgePoint: q.knowledgePoint,
      flagged: flaggedSet.has(q.id),
      outcome: outcomesById[q.id],
    }))
    .filter((entry) => {
      if (filter === 'wrong') return Boolean(entry.outcome && !entry.outcome.correct)
      if (filter === 'flagged') return entry.flagged
      return true
    })
}
