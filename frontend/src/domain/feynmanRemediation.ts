import type { FeynmanTripleEvidence, KnowledgePoint } from '../api/client'

export interface RouteTarget {
  path: string
  query?: Record<string, string>
}

export interface TripleRemediationPlan {
  triple: FeynmanTripleEvidence
  graphTarget: string
  matchedPoint: KnowledgePoint | null
  practiceTarget: RouteTarget | null
  courseTarget: RouteTarget | null
  flashcardsTarget: RouteTarget
}

/**
 * 规范化概念名词，用于三元组实体与课程知识点之间纯确定性名称对齐。
 */
export function normalizeConceptTerm(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[\s\-_·—()（）【】[\]《》“”"'`.,，。:：;；/]+/g, '')
}

/**
 * 按候选概念先后顺序对课程真实知识点进行确定性匹配（先精确匹配，再长度>=2包含匹配）。
 */
export function resolveMatchedKnowledgePoint(
  candidates: ReadonlyArray<string | undefined>,
  points: ReadonlyArray<KnowledgePoint>,
): KnowledgePoint | null {
  if (!points.length) return null
  const normalizedCandidates = candidates
    .map((c) => normalizeConceptTerm(c || ''))
    .filter((c) => c.length > 0)
  if (!normalizedCandidates.length) return null

  const indexed = points.map((point) => ({
    point,
    entityKey: normalizeConceptTerm(point.graphEntity || ''),
    nameKey: normalizeConceptTerm(point.name || ''),
  }))

  for (const term of normalizedCandidates) {
    const exact = indexed.find((item) => item.entityKey === term || item.nameKey === term)
    if (exact) return exact.point
  }

  for (const term of normalizedCandidates) {
    if (term.length < 2) continue
    const partial = indexed.find(
      (item) =>
        (item.entityKey.length >= 2 && (item.entityKey.includes(term) || term.includes(item.entityKey))) ||
        (item.nameKey.length >= 2 && (item.nameKey.includes(term) || term.includes(item.nameKey))),
    )
    if (partial) return partial.point
  }

  return null
}

/**
 * 为费曼自检返回的遗漏三元组构造定向补强直达动作（练习 / 讲义小节 / 主动回忆闪卡）。
 */
export function buildMissingTripleRemediations(
  missingTriples: ReadonlyArray<FeynmanTripleEvidence>,
  fallbackTopic: string,
  points: ReadonlyArray<KnowledgePoint>,
  defaultCourseId?: number,
): TripleRemediationPlan[] {
  return missingTriples.map((triple) => {
    const matchedPoint = resolveMatchedKnowledgePoint(
      [triple.object, triple.subject, fallbackTopic],
      points,
    )
    const courseId = matchedPoint?.courseId ?? defaultCourseId
    return {
      triple,
      graphTarget: triple.subject || triple.object || fallbackTopic,
      matchedPoint,
      practiceTarget: matchedPoint
        ? {
            path: '/practice',
            query: { knowledgePointId: String(matchedPoint.id) },
          }
        : null,
      courseTarget: matchedPoint
        ? {
            path: '/courses',
            query: {
              courseId: String(matchedPoint.courseId),
              knowledgePointId: String(matchedPoint.id),
            },
          }
        : null,
      flashcardsTarget: courseId
        ? { path: '/flashcards', query: { courseId: String(courseId) } }
        : { path: '/flashcards' },
    }
  })
}
