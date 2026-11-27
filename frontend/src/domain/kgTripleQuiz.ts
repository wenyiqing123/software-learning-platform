import type { GraphEdge, GraphResponse } from '../api/client'

export interface GraphTripleFact {
  subject: string
  predicate: string
  object: string
  score?: number
}

export type KgQuizKind = 'object' | 'predicate'

export interface KgTripleQuizOption {
  key: string
  value: string
}

export interface KgTripleQuizItem {
  id: string
  kind: KgQuizKind
  subject: string
  predicate: string
  object: string
  promptParts: {
    left: string
    middle: string
    right: string
  }
  options: KgTripleQuizOption[]
  correctValue: string
  sourceTriple: GraphTripleFact
}

export interface KgTripleQuizBuildResult {
  ready: boolean
  reason: string
  tripleCount: number
  questions: KgTripleQuizItem[]
}

function deterministicHash(input: string): number {
  let hash = 2166136261
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

function normalizeFact(raw: unknown): GraphTripleFact | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Partial<GraphTripleFact>
  const subject = typeof record.subject === 'string' ? record.subject.trim() : ''
  const predicate = typeof record.predicate === 'string' ? record.predicate.trim() : ''
  const object = typeof record.object === 'string' ? record.object.trim() : ''
  if (!subject || !predicate || !object || subject === object) return null
  const score = typeof record.score === 'number' && Number.isFinite(record.score) ? record.score : undefined
  return { subject, predicate, object, score }
}

function factSignature(t: Pick<GraphTripleFact, 'subject' | 'predicate' | 'object'>): string {
  return `${t.subject}\u0000${t.predicate}\u0000${t.object}`
}

export type KgTripleQuizSourceGraph = Pick<GraphResponse, 'available'> &
  Partial<Pick<GraphResponse, 'nodes' | 'edges' | 'evidence'>>

/**
 * 从真实图谱响应中提取并去重有效的 (subject, predicate, object) 三元组。
 * 优先读取 evidence；当图谱可用且 evidence 为空时回退读取真实图边。图谱离线时始终返回空列表。
 */
export function extractValidGraphTriples(graph: KgTripleQuizSourceGraph | null | undefined): GraphTripleFact[] {
  if (!graph || !graph.available) return []
  const seen = new Set<string>()
  const result: GraphTripleFact[] = []

  const appendIfValid = (fact: GraphTripleFact | null) => {
    if (!fact) return
    const sig = factSignature(fact)
    if (seen.has(sig)) return
    seen.add(sig)
    result.push(fact)
  }

  if (Array.isArray(graph.evidence)) {
    for (const item of graph.evidence) {
      appendIfValid(normalizeFact(item))
    }
  }
  if (!result.length && Array.isArray(graph.edges)) {
    for (const edge of graph.edges as GraphEdge[]) {
      appendIfValid(
        normalizeFact({
          subject: edge.source,
          predicate: edge.label,
          object: edge.target,
          score: edge.score,
        }),
      )
    }
  }

  return result
}

function orderOptionsDeterministically(
  seed: string,
  correctValue: string,
  distractors: string[],
): KgTripleQuizOption[] {
  const uniqueValues = [correctValue, ...distractors].filter(
    (val, idx, arr) => arr.indexOf(val) === idx,
  )
  const ordered = [...uniqueValues].sort((a, b) => {
    const diff = deterministicHash(`${seed}|${a}`) - deterministicHash(`${seed}|${b}`)
    return diff !== 0 ? diff : a.localeCompare(b, 'zh-CN')
  })
  const keys = ['A', 'B', 'C', 'D']
  return ordered.slice(0, 4).map((value, idx) => ({
    key: keys[idx],
    value,
  }))
}

/**
 * 基于真实子图三元组集合构建 100% 确定性的概念关系辨析快测题。
 * 严格守则：
 * 1. 仅当服务可用且有效三元组 >= 4 条时生成；
 * 2. 所有干扰项全部来自同一真实子图，并严格校验不与真实三元组库冲突；
 * 3. 不使用 LLM 编题、不写库、离线或数据稀疏时诚实降级返回 ready: false。
 */
export function buildDeterministicKgTripleQuiz(
  graph: KgTripleQuizSourceGraph | null | undefined,
  maxQuestions = 5,
): KgTripleQuizBuildResult {
  if (!graph || !graph.available) {
    return {
      ready: false,
      reason: '知识图谱服务暂不可用，图谱离线时严禁虚构概念关系测验，请确认 Python Bridge 与 NebulaGraph 状态后再试。',
      tripleCount: 0,
      questions: [],
    }
  }

  const triples = extractValidGraphTriples(graph)
  if (triples.length < 4) {
    return {
      ready: false,
      reason: `当前子图仅包含 ${triples.length} 条可用真实三元组（至少需要 4 条），请尝试切换概念或点击「2 跳扩展」加载更丰富的图谱邻域后再开启快测。`,
      tripleCount: triples.length,
      questions: [],
    }
  }

  const exactSigSet = new Set(triples.map(factSignature))
  const allObjects = Array.from(new Set(triples.map((t) => t.object)))
  const allPredicates = Array.from(new Set(triples.map((t) => t.predicate)))

  const sortedTriples = [...triples].sort((a, b) => {
    const scoreDiff = (b.score ?? 0) - (a.score ?? 0)
    if (Math.abs(scoreDiff) > 1e-6) return scoreDiff
    return factSignature(a).localeCompare(factSignature(b), 'zh-CN')
  })

  const questions: KgTripleQuizItem[] = []
  const usedSignatures = new Set<string>()

  for (let i = 0; i < sortedTriples.length && questions.length < maxQuestions; i += 1) {
    const triple = sortedTriples[i]
    const sig = factSignature(triple)
    if (usedSignatures.has(sig)) continue

    const preferKind: KgQuizKind = i % 2 === 0 ? 'object' : 'predicate'
    const kindsToTry: KgQuizKind[] =
      preferKind === 'object' ? ['object', 'predicate'] : ['predicate', 'object']

    for (const kind of kindsToTry) {
      if (kind === 'object') {
        const distractors = allObjects
          .filter(
            (candidate) =>
              candidate !== triple.object &&
              candidate !== triple.subject &&
              !exactSigSet.has(factSignature({ subject: triple.subject, predicate: triple.predicate, object: candidate })),
          )
          .sort((a, b) => deterministicHash(`${sig}|obj|${a}`) - deterministicHash(`${sig}|obj|${b}`))
          .slice(0, 3)

        if (distractors.length >= 2) {
          usedSignatures.add(sig)
          questions.push({
            id: `kg-q-${questions.length + 1}`,
            kind: 'object',
            subject: triple.subject,
            predicate: triple.predicate,
            object: triple.object,
            promptParts: {
              left: triple.subject,
              middle: triple.predicate,
              right: '【 ? 选择缺失概念 】',
            },
            options: orderOptionsDeterministically(sig, triple.object, distractors),
            correctValue: triple.object,
            sourceTriple: triple,
          })
          break
        }
      } else {
        const distractors = allPredicates
          .filter(
            (candidate) =>
              candidate !== triple.predicate &&
              !exactSigSet.has(factSignature({ subject: triple.subject, predicate: candidate, object: triple.object })),
          )
          .sort((a, b) => deterministicHash(`${sig}|pred|${a}`) - deterministicHash(`${sig}|pred|${b}`))
          .slice(0, 3)

        if (distractors.length >= 2) {
          usedSignatures.add(sig)
          questions.push({
            id: `kg-q-${questions.length + 1}`,
            kind: 'predicate',
            subject: triple.subject,
            predicate: triple.predicate,
            object: triple.object,
            promptParts: {
              left: triple.subject,
              middle: '【 ? 选择真实关系 】',
              right: triple.object,
            },
            options: orderOptionsDeterministically(sig, triple.predicate, distractors),
            correctValue: triple.predicate,
            sourceTriple: triple,
          })
          break
        }
      }
    }
  }

  if (!questions.length) {
    return {
      ready: false,
      reason: '当前子图节点或关系种类过于单一，暂无足够互斥干扰项用于辨析快测，请扩展 2 跳邻域后再试。',
      tripleCount: triples.length,
      questions: [],
    }
  }

  return {
    ready: true,
    reason: '',
    tripleCount: triples.length,
    questions,
  }
}
