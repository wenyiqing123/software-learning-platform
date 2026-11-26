import type { GraphResponse } from '../api/types'
import type { KnowledgeTriple } from './knowledgeGraph'

export interface ComparisonPreset {
  id: string
  label: string
  left: string
  right: string
}

export const CONCEPT_COMPARISON_PRESETS: ComparisonPreset[] = [
  { id: 'sa-ooa', label: '结构化分析SA vs 面向对象分析OOA', left: '结构化分析SA', right: '面向对象分析OOA' },
  { id: 'req-design', label: '需求分析 vs 系统设计', left: '需求分析', right: '系统设计' },
  { id: 'dfd-usecase', label: '数据流图DFD vs 用例图', left: '数据流图DFD', right: '用例图' },
]

export interface ComparisonDirectEdge {
  subject: string
  predicate: string
  object: string
  direction: 'left-to-right' | 'right-to-left'
}

export interface SharedConceptNeighbor {
  entity: string
  leftPredicates: string[]
  rightPredicates: string[]
}

export interface ExclusiveConceptRelation {
  subject: string
  predicate: string
  object: string
  counterpart: string
  direction: 'outgoing' | 'incoming'
  predicateLabel: string
}

export interface ConceptComparisonResult {
  leftEntity: string
  rightEntity: string
  sameEntity: boolean
  leftAvailable: boolean
  rightAvailable: boolean
  directLinks: ComparisonDirectEdge[]
  sharedNeighbors: SharedConceptNeighbor[]
  leftExclusive: ExclusiveConceptRelation[]
  rightExclusive: ExclusiveConceptRelation[]
  leftTripleCount: number
  rightTripleCount: number
}

function addTripleIfValid(
  target: Map<string, KnowledgeTriple>,
  subject: unknown,
  predicate: unknown,
  object: unknown,
  score?: unknown,
) {
  const s = typeof subject === 'string' ? subject.trim() : ''
  const p = typeof predicate === 'string' ? predicate.trim() : ''
  const o = typeof object === 'string' ? object.trim() : ''
  if (!s || !p || !o) return
  const key = `${s}\0${p}\0${o}`
  const numericScore = typeof score === 'number' && Number.isFinite(score) ? score : undefined
  const existing = target.get(key)
  if (!existing || (numericScore !== undefined && (existing.score === undefined || numericScore > existing.score))) {
    target.set(key, { subject: s, predicate: p, object: o, ...(numericScore !== undefined ? { score: numericScore } : {}) })
  }
}

export function extractConceptTriples(response: GraphResponse | null | undefined): KnowledgeTriple[] {
  if (!response) return []
  const map = new Map<string, KnowledgeTriple>()
  for (const edge of response.edges || []) {
    addTripleIfValid(map, edge?.source, edge?.label, edge?.target, edge?.score)
  }
  for (const ev of response.evidence || []) {
    if (!ev || typeof ev !== 'object') continue
    const item = ev as Record<string, unknown>
    addTripleIfValid(map, item.subject, item.predicate, item.object, item.score)
  }
  return Array.from(map.values())
}

interface IncidentRelation {
  subject: string
  predicate: string
  object: string
  counterpart: string
  direction: 'outgoing' | 'incoming'
  predicateLabel: string
}

function collectIncidentRelations(entity: string, triples: KnowledgeTriple[]): Map<string, IncidentRelation[]> {
  const byCounterpart = new Map<string, IncidentRelation[]>()
  const seen = new Set<string>()
  for (const triple of triples) {
    if (triple.subject !== entity && triple.object !== entity) continue
    const outgoing = triple.subject === entity
    const counterpart = outgoing ? triple.object : triple.subject
    if (!counterpart || counterpart === entity) continue
    const predicateLabel = outgoing ? `${triple.predicate} →` : `← ${triple.predicate}`
    const dedupKey = `${triple.subject}\0${triple.predicate}\0${triple.object}`
    if (seen.has(dedupKey)) continue
    seen.add(dedupKey)
    const list = byCounterpart.get(counterpart) || []
    list.push({
      subject: triple.subject,
      predicate: triple.predicate,
      object: triple.object,
      counterpart,
      direction: outgoing ? 'outgoing' : 'incoming',
      predicateLabel,
    })
    byCounterpart.set(counterpart, list)
  }
  return byCounterpart
}

export function compareConcepts(
  leftEntity: string,
  leftResponse: GraphResponse | null | undefined,
  rightEntity: string,
  rightResponse: GraphResponse | null | undefined,
): ConceptComparisonResult {
  const leftName = leftEntity.trim()
  const rightName = rightEntity.trim()
  const leftAvailable = Boolean(leftResponse?.available)
  const rightAvailable = Boolean(rightResponse?.available)

  if (!leftName || !rightName || leftName === rightName) {
    return {
      leftEntity: leftName,
      rightEntity: rightName,
      sameEntity: true,
      leftAvailable,
      rightAvailable,
      directLinks: [],
      sharedNeighbors: [],
      leftExclusive: [],
      rightExclusive: [],
      leftTripleCount: 0,
      rightTripleCount: 0,
    }
  }

  const leftTriples = leftAvailable ? extractConceptTriples(leftResponse) : []
  const rightTriples = rightAvailable ? extractConceptTriples(rightResponse) : []

  const directMap = new Map<string, ComparisonDirectEdge>()
  for (const triple of [...leftTriples, ...rightTriples]) {
    if (triple.subject === leftName && triple.object === rightName) {
      const key = `${triple.subject}\0${triple.predicate}\0${triple.object}`
      if (!directMap.has(key)) {
        directMap.set(key, {
          subject: triple.subject,
          predicate: triple.predicate,
          object: triple.object,
          direction: 'left-to-right',
        })
      }
    } else if (triple.subject === rightName && triple.object === leftName) {
      const key = `${triple.subject}\0${triple.predicate}\0${triple.object}`
      if (!directMap.has(key)) {
        directMap.set(key, {
          subject: triple.subject,
          predicate: triple.predicate,
          object: triple.object,
          direction: 'right-to-left',
        })
      }
    }
  }
  const directLinks = Array.from(directMap.values()).sort((a, b) => a.predicate.localeCompare(b.predicate, 'zh-CN'))

  const leftByCounterpart = collectIncidentRelations(leftName, leftTriples)
  const rightByCounterpart = collectIncidentRelations(rightName, rightTriples)

  const sharedSet = new Set<string>()
  const sharedNeighbors: SharedConceptNeighbor[] = []
  for (const [counterpart, leftRels] of leftByCounterpart.entries()) {
    if (counterpart === rightName) continue
    const rightRels = rightByCounterpart.get(counterpart)
    if (!rightRels?.length) continue
    sharedSet.add(counterpart)
    sharedNeighbors.push({
      entity: counterpart,
      leftPredicates: Array.from(new Set(leftRels.map(item => item.predicateLabel))).sort((a, b) => a.localeCompare(b, 'zh-CN')),
      rightPredicates: Array.from(new Set(rightRels.map(item => item.predicateLabel))).sort((a, b) => a.localeCompare(b, 'zh-CN')),
    })
  }
  sharedNeighbors.sort((a, b) =>
    (b.leftPredicates.length + b.rightPredicates.length) - (a.leftPredicates.length + a.rightPredicates.length)
    || a.entity.localeCompare(b.entity, 'zh-CN'),
  )

  const sortExclusive = (a: ExclusiveConceptRelation, b: ExclusiveConceptRelation) =>
    a.counterpart.localeCompare(b.counterpart, 'zh-CN') || a.predicate.localeCompare(b.predicate, 'zh-CN')

  const leftExclusive: ExclusiveConceptRelation[] = []
  for (const [counterpart, rels] of leftByCounterpart.entries()) {
    if (counterpart === rightName || sharedSet.has(counterpart)) continue
    leftExclusive.push(...rels)
  }
  leftExclusive.sort(sortExclusive)

  const rightExclusive: ExclusiveConceptRelation[] = []
  for (const [counterpart, rels] of rightByCounterpart.entries()) {
    if (counterpart === leftName || sharedSet.has(counterpart)) continue
    rightExclusive.push(...rels)
  }
  rightExclusive.sort(sortExclusive)

  return {
    leftEntity: leftName,
    rightEntity: rightName,
    sameEntity: false,
    leftAvailable,
    rightAvailable,
    directLinks,
    sharedNeighbors,
    leftExclusive,
    rightExclusive,
    leftTripleCount: leftTriples.length,
    rightTripleCount: rightTriples.length,
  }
}

export function buildConceptCompareTutorPrompt(comparison: ConceptComparisonResult): string {
  const summaryParts = [
    `直接关联 ${comparison.directLinks.length} 条`,
    `共同关联概念 ${comparison.sharedNeighbors.length} 个`,
    `各自独有邻接关系 ${comparison.leftExclusive.length} / ${comparison.rightExclusive.length} 条`,
  ]
  return `请结合软件工程课程知识图谱，对比辨析「${comparison.leftEntity}」与「${comparison.rightEntity}」的核心定义、适用场景与主要区别（图谱检索概况：${summaryParts.join('，')}）。`
}
