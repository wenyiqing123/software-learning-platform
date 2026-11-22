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
    [key: string]: unknown
  }
}

export interface TutorContextChip {
  label: string
  value: string
}
