export interface LearningContext {
  courseId?: number
  sectionId?: number
  knowledgePointId?: number
  entity?: string
}

type QueryValue = string | string[] | null | undefined

function positiveId(value: QueryValue): number | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  if (!raw || !/^[1-9]\d*$/.test(raw)) return undefined
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) ? parsed : undefined
}

function cleanEntity(value: QueryValue): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  const entity = raw?.trim()
  return entity || undefined
}

export function contextFromQuery(query: Record<string, QueryValue>): LearningContext {
  return {
    ...(positiveId(query.courseId) ? { courseId: positiveId(query.courseId) } : {}),
    ...(positiveId(query.sectionId) ? { sectionId: positiveId(query.sectionId) } : {}),
    ...(positiveId(query.knowledgePointId) ? { knowledgePointId: positiveId(query.knowledgePointId) } : {}),
    ...(cleanEntity(query.entity) ? { entity: cleanEntity(query.entity) } : {}),
  }
}

function query(context: LearningContext, options: { entity?: boolean; section?: boolean; point?: boolean } = {}) {
  const result: Record<string, string> = {}
  if (context.courseId) result.courseId = String(context.courseId)
  if (options.section && context.sectionId) result.sectionId = String(context.sectionId)
  if (options.point && context.knowledgePointId) result.knowledgePointId = String(context.knowledgePointId)
  if (options.entity && context.entity) result.entity = context.entity
  return result
}

export function graphHref(context: LearningContext) {
  return { path: '/knowledge', query: query(context, { entity: true, section: true, point: true }) }
}

export function practiceHref(context: LearningContext) {
  return { path: '/practice', query: query(context, { section: true, point: true }) }
}

export function tutorHref(context: LearningContext) {
  return { path: '/tutor', query: query(context, { entity: true, section: true, point: true }) }
}

export function courseHref(context: LearningContext) {
  return { path: '/courses', query: query(context, { section: true, point: true }) }
}

export function rememberLearningContext(context: LearningContext) {
  try { localStorage.setItem('slp-last-learning-context', JSON.stringify(context)) } catch { /* Storage may be unavailable in private browsing. */ }
}

export function readLastLearningContext(): LearningContext | null {
  try {
    const raw = localStorage.getItem('slp-last-learning-context')
    if (!raw) return null
    const value = JSON.parse(raw) as Record<string, QueryValue>
    return contextFromQuery(value)
  } catch { return null }
}
