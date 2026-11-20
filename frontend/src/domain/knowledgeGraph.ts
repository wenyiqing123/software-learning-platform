export interface KnowledgeTriple {
  subject: string
  predicate: string
  object: string
  score?: number
}

export interface KnowledgeGraphNode {
  id: string
  label: string
}

export interface KnowledgeGraphEdge {
  source: string
  target: string
  label: string
  score?: number
}

export function mapTriplesToGraph(triples: KnowledgeTriple[]) {
  const nodes = new Map<string, KnowledgeGraphNode>()
  const edges = new Map<string, KnowledgeGraphEdge>()

  for (const triple of triples) {
    const subject = triple.subject?.trim()
    const predicate = triple.predicate?.trim()
    const object = triple.object?.trim()
    if (!subject || !predicate || !object) continue

    nodes.set(subject, { id: subject, label: subject })
    nodes.set(object, { id: object, label: object })
    const edgeKey = JSON.stringify([subject, predicate, object])
    if (!edges.has(edgeKey)) {
      edges.set(edgeKey, {
        source: subject,
        target: object,
        label: predicate,
        ...(typeof triple.score === 'number' ? { score: triple.score } : {}),
      })
    }
  }

  return { nodes: [...nodes.values()], edges: [...edges.values()] }
}
