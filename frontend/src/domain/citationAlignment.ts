import type { TutorTriple } from './tutorChat'

export type RawCitationTriple =
  | TutorTriple
  | {
      source: string
      relation: string
      target: string
      score?: number
    }

export interface NormalizedCitationTriple {
  index: number
  subject: string
  predicate: string
  object: string
  score?: number
}

export interface CitationAlignmentReport {
  triples: NormalizedCitationTriple[]
  referencedIndices: number[]
  coveredTripleCount: number
  totalTripleCount: number
  coveredEntities: string[]
  totalUniqueEntities: number
  coverageText: string
}

const ASCII_TOKEN_RE = /^[a-z0-9]+$/i

function cleanTerm(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

/** 将不同字段形态的三元组统一规整为 1..N 编号结构，丢弃缺失主/客体的无效项 */
export function normalizeCitationTriples(rawTriples?: ReadonlyArray<RawCitationTriple> | null): NormalizedCitationTriple[] {
  if (!Array.isArray(rawTriples) || !rawTriples.length) return []
  const output: NormalizedCitationTriple[] = []
  for (const item of rawTriples) {
    if (!item || typeof item !== 'object') continue
    const subject = cleanTerm('subject' in item ? item.subject : item.source)
    const predicate = cleanTerm('predicate' in item ? item.predicate : item.relation)
    const object = cleanTerm('object' in item ? item.object : item.target)
    if (!subject || !object) continue
    output.push({
      index: output.length + 1,
      subject,
      predicate: predicate || '关联',
      object,
      ...(typeof item.score === 'number' ? { score: item.score } : {}),
    })
  }
  return output
}

/** 剥离行内代码、`[label](url)` 的链接 URL 部分与常见 Markdown 符号，得到可用于纯文本确定性匹配的文本 */
function stripInlineFormattingForMatch(line: string): string {
  return String(line || '')
    .replace(/`[^`\n]*`/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[*_~>#|]/g, ' ')
}

/** 判断某个术语是否在已预处理文本中发生确定性匹配（纯 ASCII 短词要求单词边界） */
function containsEntityTerm(haystackLower: string, term: string): boolean {
  const normalized = term.trim().toLowerCase()
  if (normalized.length < 2) return false
  if (ASCII_TOKEN_RE.test(normalized)) {
    let from = 0
    while (from <= haystackLower.length - normalized.length) {
      const idx = haystackLower.indexOf(normalized, from)
      if (idx < 0) return false
      const before = idx > 0 ? haystackLower[idx - 1] : ''
      const after = idx + normalized.length < haystackLower.length ? haystackLower[idx + normalized.length] : ''
      const beforeOk = !before || !/[a-z0-9]/.test(before)
      const afterOk = !after || !/[a-z0-9]/.test(after)
      if (beforeOk && afterOk) return true
      from = idx + 1
    }
    return false
  }
  return haystackLower.includes(normalized)
}

/**
 * 确定性判断单句/单段文本命中的三元组序号（1..N 升序去重）。
 * - 双端（subject + object）同时命中时最优先；
 * - 单端确定性命中该三元组核心实体（subject 或 object 完整出现）也记为命中；
 * - 严格基于真实出现的词汇，绝不编造不存在的三元组序号。
 */
export function alignSentenceCitations(
  sentence: string,
  triples: ReadonlyArray<NormalizedCitationTriple>
): number[] {
  if (!triples.length) return []
  const cleaned = stripInlineFormattingForMatch(sentence).trim()
  if (!cleaned) return []
  const lower = cleaned.toLowerCase()

  const exactBoth: number[] = []
  const singleSide: number[] = []

  for (const triple of triples) {
    const hitSubject = containsEntityTerm(lower, triple.subject)
    const hitObject = containsEntityTerm(lower, triple.object)
    if (hitSubject && hitObject) {
      exactBoth.push(triple.index)
    } else if (hitSubject || hitObject) {
      singleSide.push(triple.index)
    }
  }

  return exactBoth.length ? exactBoth : singleSide
}

/**
 * 在不切开行内 `code` 或 `**strong**` 成对标记的前提下，按自然句末标点将单行文本切分为独立句子，
 * 确保任意一句直接送入 parseInlineMarkdown 后都能保持行内语法完整。
 */
export function splitMarkdownLineIntoSentences(line: string): string[] {
  const source = String(line || '')
  if (!source.trim()) return []

  const result: string[] = []
  let start = 0
  let inBacktick = false
  let boldMarker: '**' | '__' | null = null

  for (let i = 0; i < source.length; i++) {
    const ch = source[i]
    if (ch === '`') {
      inBacktick = !inBacktick
      continue
    }
    if (inBacktick) continue

    if ((ch === '*' || ch === '_') && source[i + 1] === ch) {
      const marker = (ch + ch) as '**' | '__'
      if (boldMarker === null) boldMarker = marker
      else if (boldMarker === marker) boldMarker = null
      i++
      continue
    }
    if (boldMarker !== null) continue

    const isCjkSentenceEnd = ch === '。' || ch === '！' || ch === '？' || ch === '；'
    const prev = i > 0 ? source[i - 1] : ''
    const next = i + 1 < source.length ? source[i + 1] : ''
    const isAsciiSentenceEnd =
      ch === '.' && !/\d/.test(prev) && !/\d/.test(next) && (next === '' || /\s/.test(next))

    if (isCjkSentenceEnd || isAsciiSentenceEnd) {
      // 吸纳紧邻的右引号或右括号，保持中文排版完整
      let end = i + 1
      while (end < source.length && /[”’"')）】」』]/.test(source[end])) {
        end++
      }
      const part = source.slice(start, end)
      if (part.trim()) result.push(part)
      // 保留句间前导空格到下一句开头以保持原间距
      start = end
      i = end - 1
    }
  }

  if (start < source.length) {
    const tail = source.slice(start)
    if (tail.trim()) result.push(tail)
  }

  return result.length ? result : [source]
}

/** 从 Markdown 正文中提取全部非代码行（自动跳过 ``` 围栏代码块） */
function extractNonCodeTextLines(answer: string): string[] {
  const lines = String(answer || '').replaceAll('\r\n', '\n').split('\n')
  const plainLines: string[] = []
  let inCodeFence = false

  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      inCodeFence = !inCodeFence
      continue
    }
    if (inCodeFence) continue
    const trimmed = line.trim()
    if (!trimmed || /^(?:-{3,}|\*{3,})$/.test(trimmed)) continue
    plainLines.push(trimmed)
  }
  return plainLines
}

/** 计算整条 AI 回答与真实检索三元组之间的确定性引用对齐报告与实体覆盖概览 */
export function buildCitationAlignment(
  answer: string,
  rawTriples?: ReadonlyArray<RawCitationTriple> | null
): CitationAlignmentReport {
  const triples = normalizeCitationTriples(rawTriples)
  if (!triples.length) {
    return {
      triples: [],
      referencedIndices: [],
      coveredTripleCount: 0,
      totalTripleCount: 0,
      coveredEntities: [],
      totalUniqueEntities: 0,
      coverageText: '',
    }
  }

  const lines = extractNonCodeTextLines(answer)
  const referencedSet = new Set<number>()
  const entityPool = new Set<string>()
  const coveredEntitySet = new Set<string>()

  for (const triple of triples) {
    entityPool.add(triple.subject)
    entityPool.add(triple.object)
  }

  const combinedText = lines.map(stripInlineFormattingForMatch).join('\n').toLowerCase()
  for (const entity of entityPool) {
    if (containsEntityTerm(combinedText, entity)) {
      coveredEntitySet.add(entity)
    }
  }

  for (const line of lines) {
    for (const sentence of splitMarkdownLineIntoSentences(line)) {
      for (const index of alignSentenceCitations(sentence, triples)) {
        referencedSet.add(index)
      }
    }
  }

  const referencedIndices = [...referencedSet].sort((a, b) => a - b)
  const coveredTripleCount = referencedIndices.length
  const totalTripleCount = triples.length

  return {
    triples,
    referencedIndices,
    coveredTripleCount,
    totalTripleCount,
    coveredEntities: [...coveredEntitySet],
    totalUniqueEntities: entityPool.size,
    coverageText: `回复提及了 ${coveredTripleCount}/${totalTripleCount} 条已检索图谱证据中的实体`,
  }
}
