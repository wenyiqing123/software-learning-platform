function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export type InlineSpan =
  | { kind: 'text'; text: string }
  | { kind: 'strong'; text: string }
  | { kind: 'em'; text: string }
  | { kind: 'code'; text: string }

export type MarkdownBlock =
  | { kind: 'heading'; level: 3 | 4; text: string }
  | { kind: 'paragraph'; lines: string[] }
  | { kind: 'list'; ordered: boolean; lines: string[] }
  | { kind: 'blockquote'; lines: string[] }
  | { kind: 'table'; headers: string[]; rows: string[][] }
  | { kind: 'hr' }
  | { kind: 'code'; text: string; language: string }

const INLINE_TOKEN_RE = /(\*\*[^*\n]+?\*\*|__[^_\n]+?__|\*[^*\n]+?\*|`[^`\n]+?`)/g
const ORDERED_ITEM_RE = /^\s*\d+[.、)]\s+(.+)$/
const UNORDERED_ITEM_RE = /^\s*[-*•]\s+(.+)$/
const TABLE_DIVIDER_RE = /^\s*\|?(?:\s*:?-{2,}:?\s*\|)+\s*(?:\s*:?-{2,}:?\s*)?\|?\s*$/

/** 将单行文本安全拆分为行内样式片段（无 HTML 注入风险） */
export function parseInlineMarkdown(input: string): InlineSpan[] {
  const source = String(input || '')
  if (!source) return []
  const spans: InlineSpan[] = []
  let lastIndex = 0
  INLINE_TOKEN_RE.lastIndex = 0

  for (const match of source.matchAll(INLINE_TOKEN_RE)) {
    const idx = match.index ?? 0
    if (idx > lastIndex) {
      spans.push({ kind: 'text', text: source.slice(lastIndex, idx) })
    }
    const token = match[0]
    if ((token.startsWith('**') && token.endsWith('**')) || (token.startsWith('__') && token.endsWith('__'))) {
      spans.push({ kind: 'strong', text: token.slice(2, -2) })
    } else if (token.startsWith('`') && token.endsWith('`')) {
      spans.push({ kind: 'code', text: token.slice(1, -1) })
    } else if (token.startsWith('*') && token.endsWith('*')) {
      spans.push({ kind: 'em', text: token.slice(1, -1) })
    } else {
      spans.push({ kind: 'text', text: token })
    }
    lastIndex = idx + token.length
  }

  if (lastIndex < source.length) {
    spans.push({ kind: 'text', text: source.slice(lastIndex) })
  }
  return spans
}

function splitTableRow(line: string): string[] {
  let trimmed = line.trim()
  if (trimmed.startsWith('|')) trimmed = trimmed.slice(1)
  if (trimmed.endsWith('|')) trimmed = trimmed.slice(0, -1)
  return trimmed.split('|').map(cell => cell.trim())
}

function isTableRowCandidate(line: string): boolean {
  const trimmed = line.trim()
  return trimmed.includes('|') && splitTableRow(trimmed).length >= 2
}

export function parseSimpleMarkdown(input: string): MarkdownBlock[] {
  const source = String(input || '').replaceAll('\r\n', '\n')
  const lines = source.split('\n')
  const output: MarkdownBlock[] = []

  let paragraph: string[] = []
  let list: { ordered: boolean; lines: string[] } | null = null
  let quote: string[] = []
  let code: { language: string; lines: string[] } | null = null

  const flushParagraph = () => {
    if (paragraph.length) {
      output.push({ kind: 'paragraph', lines: paragraph })
      paragraph = []
    }
  }
  const flushList = () => {
    if (list && list.lines.length) {
      output.push({ kind: 'list', ordered: list.ordered, lines: list.lines })
      list = null
    }
  }
  const flushQuote = () => {
    if (quote.length) {
      output.push({ kind: 'blockquote', lines: quote })
      quote = []
    }
  }
  const flushNonCode = () => {
    flushParagraph()
    flushList()
    flushQuote()
  }
  const flushCode = () => {
    if (code) {
      output.push({ kind: 'code', language: code.language, text: code.lines.join('\n') })
      code = null
    }
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const fence = line.match(/^\s*```(\w*)\s*$/)
    if (fence) {
      if (code) {
        flushCode()
      } else {
        flushNonCode()
        code = { language: fence[1] || 'text', lines: [] }
      }
      continue
    }
    if (code) {
      code.lines.push(line)
      continue
    }

    if (!line.trim()) {
      flushNonCode()
      continue
    }

    if (/^\s*(?:-{3,}|\*{3,})\s*$/.test(line)) {
      flushNonCode()
      output.push({ kind: 'hr' })
      continue
    }

    const heading = line.match(/^\s*(#{1,6})\s+(.+)$/)
    if (heading) {
      flushNonCode()
      const level = heading[1].length >= 3 ? 4 : 3
      output.push({ kind: 'heading', level, text: heading[2].trim() })
      continue
    }

    // Markdown 管道表格解析（表头行 + 分隔行 + 数据行）
    if (i + 1 < lines.length && isTableRowCandidate(line) && TABLE_DIVIDER_RE.test(lines[i + 1])) {
      flushNonCode()
      const headers = splitTableRow(line)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && isTableRowCandidate(lines[i]) && !TABLE_DIVIDER_RE.test(lines[i])) {
        const cells = splitTableRow(lines[i])
        while (cells.length < headers.length) cells.push('')
        rows.push(cells.slice(0, headers.length))
        i++
      }
      i--
      output.push({ kind: 'table', headers, rows })
      continue
    }

    const quoteMatch = line.match(/^\s*>\s?(.*)$/)
    if (quoteMatch) {
      flushParagraph()
      flushList()
      quote.push(quoteMatch[1])
      continue
    }
    flushQuote()

    const orderedMatch = line.match(ORDERED_ITEM_RE)
    if (orderedMatch) {
      flushParagraph()
      if (list && !list.ordered) flushList()
      if (!list) list = { ordered: true, lines: [] }
      list.lines.push(orderedMatch[1])
      continue
    }

    const unorderedMatch = line.match(UNORDERED_ITEM_RE)
    if (unorderedMatch) {
      flushParagraph()
      if (list && list.ordered) flushList()
      if (!list) list = { ordered: false, lines: [] }
      list.lines.push(unorderedMatch[1])
      continue
    }

    flushList()
    paragraph.push(line)
  }

  flushCode()
  flushNonCode()
  return output
}

function renderInlineHtml(input: string): string {
  return parseInlineMarkdown(input).map(span => {
    const escaped = escapeHtml(span.text)
    if (span.kind === 'strong') return `<strong>${escaped}</strong>`
    if (span.kind === 'em') return `<em>${escaped}</em>`
    if (span.kind === 'code') return `<code>${escaped}</code>`
    return escaped
  }).join('')
}

export function renderSimpleMarkdown(input: string): string {
  return parseSimpleMarkdown(input).map(block => {
    if (block.kind === 'heading') {
      const tag = `h${block.level}`
      return `<${tag}>${renderInlineHtml(block.text)}</${tag}>`
    }
    if (block.kind === 'code') {
      return `<pre><code class="language-${escapeHtml(block.language)}">${escapeHtml(block.text)}</code></pre>`
    }
    if (block.kind === 'hr') {
      return '<hr>'
    }
    if (block.kind === 'blockquote') {
      return `<blockquote><p>${block.lines.map(renderInlineHtml).join('<br>')}</p></blockquote>`
    }
    if (block.kind === 'table') {
      const head = block.headers.map(h => `<th>${renderInlineHtml(h)}</th>`).join('')
      const body = block.rows.map(r => `<tr>${r.map(c => `<td>${renderInlineHtml(c)}</td>`).join('')}</tr>`).join('')
      return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
    }
    if (block.kind === 'list') {
      const tag = block.ordered ? 'ol' : 'ul'
      return `<${tag}>${block.lines.map(item => `<li>${renderInlineHtml(item)}</li>`).join('')}</${tag}>`
    }
    return `<p>${block.lines.map(renderInlineHtml).join('<br>')}</p>`
  }).join('')
}
