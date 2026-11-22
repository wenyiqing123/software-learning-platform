function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;')
}

export type MarkdownBlock =
  | { kind: 'heading'; text: string }
  | { kind: 'paragraph' | 'list'; lines: string[] }
  | { kind: 'code'; text: string; language: string }

export function parseSimpleMarkdown(input: string): MarkdownBlock[] {
  const source = String(input || '').replaceAll('\r\n', '\n')
  const lines = source.split('\n')
  const output: MarkdownBlock[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let code: string[] | null = null
  let language = ''

  const flushParagraph = () => { if (paragraph.length) { output.push({ kind: 'paragraph', lines: paragraph }); paragraph = [] } }
  const flushList = () => { if (list.length) { output.push({ kind: 'list', lines: list }); list = [] } }
  const flushCode = () => { if (code) { output.push({ kind: 'code', language, text: code.join('\n') }); code = null; language = '' } }

  for (const line of lines) {
    const fence = line.match(/^\s*```(\w*)\s*$/)
    if (fence) {
      if (code) flushCode(); else { flushParagraph(); flushList(); code = []; language = fence[1] || 'text' }
      continue
    }
    if (code) { code.push(line); continue }
    if (!line.trim()) { flushParagraph(); flushList(); continue }
    const heading = line.match(/^\s*#{1,3}\s+(.+)$/)
    if (heading) { flushParagraph(); flushList(); output.push({ kind: 'heading', text: heading[1] }); continue }
    const item = line.match(/^\s*[-*]\s+(.+)$/)
    if (item) { flushParagraph(); list.push(item[1]); continue }
    flushList(); paragraph.push(line)
  }
  if (code) flushCode(); flushParagraph(); flushList()
  return output
}

export function renderSimpleMarkdown(input: string): string {
  return parseSimpleMarkdown(input).map(block => {
    if (block.kind === 'heading') return '<h3>' + escapeHtml(block.text) + '</h3>'
    if (block.kind === 'code') return '<pre><code class="language-' + escapeHtml(block.language) + '">' + escapeHtml(block.text) + '</code></pre>'
    if (block.kind === 'list') return '<ul>' + block.lines.map(item => '<li>' + escapeHtml(item) + '</li>').join('') + '</ul>'
    return '<p>' + block.lines.map(escapeHtml).join('<br>') + '</p>'
  }).join('')
}
