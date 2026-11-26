import type { GraphEdge, GraphNode, GraphResponse, MasteryMapEdge, MasteryMapNode, SpacedReviewCard } from '../api/types'

export function mergeGraphResponses(base: GraphResponse, incoming: GraphResponse): GraphResponse {
  const nodeMap = new Map<string, GraphNode>()
  for (const n of base.nodes || []) {
    if (n?.id) nodeMap.set(n.id, n)
  }
  for (const n of incoming.nodes || []) {
    if (n?.id && !nodeMap.has(n.id)) nodeMap.set(n.id, n)
  }

  const edgeMap = new Map<string, GraphEdge>()
  for (const e of [...(base.edges || []), ...(incoming.edges || [])]) {
    if (!e?.source || !e?.target) continue
    const key = `${e.source}::${e.label || ''}::${e.target}`
    if (!edgeMap.has(key)) edgeMap.set(key, e)
  }

  const evidenceMap = new Map<string, unknown>()
  for (const ev of [...(base.evidence || []), ...(incoming.evidence || [])]) {
    if (!ev || typeof ev !== 'object') continue
    const obj = ev as Record<string, unknown>
    const key = `${String(obj.subject || '')}::${String(obj.predicate || '')}::${String(obj.object || '')}`
    if (!evidenceMap.has(key)) evidenceMap.set(key, ev)
  }

  return {
    available: Boolean(base.available || incoming.available),
    nodes: Array.from(nodeMap.values()),
    edges: Array.from(edgeMap.values()),
    evidence: Array.from(evidenceMap.values()),
  }
}

export function computeNodeDegrees(nodes: ReadonlyArray<GraphNode>, edges: ReadonlyArray<GraphEdge>): Map<string, number> {
  const degrees = new Map<string, number>()
  for (const n of nodes) degrees.set(n.id, 0)
  for (const e of edges) {
    degrees.set(e.source, (degrees.get(e.source) || 0) + 1)
    degrees.set(e.target, (degrees.get(e.target) || 0) + 1)
  }
  return degrees
}

export interface BridgePathStep {
  from: string
  relation: string
  to: string
}

export interface BridgePathResult {
  found: boolean
  nodeIds: string[]
  steps: BridgePathStep[]
}

export function findEntityBridgePath(
  edges: ReadonlyArray<GraphEdge>,
  sourceEntity: string,
  targetEntity: string,
): BridgePathResult {
  const start = sourceEntity.trim()
  const goal = targetEntity.trim()
  if (!start || !goal) return { found: false, nodeIds: [], steps: [] }
  if (start === goal) return { found: true, nodeIds: [start], steps: [] }

  const adj = new Map<string, Array<{ neighbor: string; relation: string }>>()
  const pushAdj = (u: string, v: string, relation: string) => {
    const list = adj.get(u) || []
    list.push({ neighbor: v, relation })
    adj.set(u, list)
  }
  for (const e of edges) {
    if (!e.source || !e.target) continue
    pushAdj(e.source, e.target, `${e.label} →`)
    pushAdj(e.target, e.source, `← ${e.label}`)
  }

  const visited = new Set<string>([start])
  const parent = new Map<string, { prev: string; relation: string }>()
  const queue: string[] = [start]

  while (queue.length > 0) {
    const cur = queue.shift()!
    if (cur === goal) {
      const nodeIds: string[] = [goal]
      const steps: BridgePathStep[] = []
      let walker = goal
      while (walker !== start) {
        const info = parent.get(walker)
        if (!info) break
        steps.unshift({ from: info.prev, relation: info.relation, to: walker })
        nodeIds.unshift(info.prev)
        walker = info.prev
      }
      return { found: true, nodeIds, steps }
    }
    for (const edge of adj.get(cur) || []) {
      if (!visited.has(edge.neighbor)) {
        visited.add(edge.neighbor)
        parent.set(edge.neighbor, { prev: cur, relation: edge.relation })
        queue.push(edge.neighbor)
      }
    }
  }

  return { found: false, nodeIds: [], steps: [] }
}

export interface RoadmapStage {
  stageIndex: number
  title: string
  nodes: MasteryMapNode[]
}

export function buildPrerequisiteRoadmapStages(
  nodes: ReadonlyArray<MasteryMapNode>,
  edges: ReadonlyArray<MasteryMapEdge>,
): RoadmapStage[] {
  if (!nodes.length) return []

  const nodeById = new Map<number, MasteryMapNode>()
  const indegree = new Map<number, number>()
  const outgoing = new Map<number, number[]>()

  for (const n of nodes) {
    nodeById.set(n.id, n)
    indegree.set(n.id, 0)
    outgoing.set(n.id, [])
  }

  const seenEdge = new Set<string>()
  const addLink = (fromId: number, toId: number) => {
    if (fromId === toId || !nodeById.has(fromId) || !nodeById.has(toId)) return
    const key = `${fromId}->${toId}`
    if (seenEdge.has(key)) return
    seenEdge.add(key)
    outgoing.get(fromId)!.push(toId)
    indegree.set(toId, (indegree.get(toId) || 0) + 1)
  }

  for (const n of nodes) {
    for (const preId of n.prerequisiteIds || []) {
      addLink(preId, n.id)
    }
  }
  for (const e of edges) {
    addLink(e.source, e.target)
  }

  const remaining = new Set(nodes.map((n) => n.id))
  const stages: RoadmapStage[] = []

  while (remaining.size > 0) {
    let layerIds = Array.from(remaining).filter((id) => (indegree.get(id) || 0) === 0)
    if (!layerIds.length) {
      // 有环或异常先修时安全收拢全部剩余节点，防止死循环
      layerIds = Array.from(remaining)
    }
    const layerNodes = layerIds
      .map((id) => nodeById.get(id)!)
      .sort((a, b) => (a.chapterId ?? 0) - (b.chapterId ?? 0) || a.id - b.id)

    const idx = stages.length + 1
    stages.push({
      stageIndex: idx,
      title: idx === 1 ? `第 ${idx} 阶段 · 基础起点` : `第 ${idx} 阶段 · 进阶路径`,
      nodes: layerNodes,
    })

    for (const id of layerIds) {
      remaining.delete(id)
      for (const nextId of outgoing.get(id) || []) {
        if (remaining.has(nextId)) {
          indegree.set(nextId, Math.max(0, (indegree.get(nextId) || 0) - 1))
        }
      }
    }
  }

  return stages
}

export interface RatingIntervalPreview {
  rating: 0 | 1 | 2 | 3
  label: string
  correctMinutes: number
  correctText: string
  wrongText: string
}

const FIXED_INTERVAL_MINUTES: Record<0 | 1 | 2 | 3, number> = {
  0: 10,
  1: 1_440,
  2: 4_320,
  3: 10_080,
}

export function formatIntervalLabel(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟后`
  if (minutes < 1_440) return `${Math.round(minutes / 60)} 小时后`
  return `${Math.round(minutes / 1_440)} 天后`
}

export function getSpacedRatingPreviews(): RatingIntervalPreview[] {
  const labels: Record<0 | 1 | 2 | 3, string> = { 0: '忘记了', 1: '困难', 2: '一般', 3: '轻松' }
  return ([0, 1, 2, 3] as const).map((rating) => ({
    rating,
    label: labels[rating],
    correctMinutes: FIXED_INTERVAL_MINUTES[rating],
    correctText: formatIntervalLabel(FIXED_INTERVAL_MINUTES[rating]),
    wrongText: '10 分钟后',
  }))
}

export interface ForecastDayBucket {
  key: string
  label: string
  dueCount: number
}

export function buildSevenDayReviewForecast(
  dueCards: ReadonlyArray<SpacedReviewCard>,
  pausedCards: ReadonlyArray<SpacedReviewCard>,
  now = new Date(),
): { buckets: ForecastDayBucket[]; pausedCount: number } {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const dayMs = 86_400_000
  const buckets: ForecastDayBucket[] = Array.from({ length: 7 }, (_, offset) => {
    const d = new Date(startOfToday + offset * dayMs)
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return {
      key: `${d.getFullYear()}-${mm}-${dd}`,
      label: offset === 0 ? '今日/已到期' : offset === 1 ? '明天' : `${mm}/${dd}`,
      dueCount: 0,
    }
  })

  for (const card of dueCards) {
    const ts = card.dueAt ? Date.parse(card.dueAt) : NaN
    if (!Number.isFinite(ts) || ts <= startOfToday + dayMs) {
      buckets[0].dueCount += 1
      continue
    }
    const dayOffset = Math.floor((ts - startOfToday) / dayMs)
    if (dayOffset >= 1 && dayOffset < 7) {
      buckets[dayOffset].dueCount += 1
    }
  }

  return {
    buckets,
    pausedCount: pausedCards.length,
  }
}
