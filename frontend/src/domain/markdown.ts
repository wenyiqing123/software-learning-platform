function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;')
}

export function renderSimpleMarkdown(input: string): string {
  const source = String(input || '').replaceAll('\r\n', '\n')
  const lines = source.split('\n')
  const output: string[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let code: string[] | null = null
  let language = ''

  const flushParagraph = () => { if (paragraph.length) { output.push('<p>' + paragraph.map(escapeHtml).join('<br>') + '</p>'); paragraph = [] } }
  const flushList = () => { if (list.length) { output.push('<ul>' + list.map(item => '<li>' + escapeHtml(item) + '</li>').join('') + '</ul>'); list = [] } }
  const flushCode = () => { if (code) { output.push('<pre><code class="language-' + escapeHtml(language) + '">' + escapeHtml(code.join('\n')) + '</code></pre>'); code = null; language = '' } }

  for (const line of lines) {
    const fence = line.match(/^\s*```(\w*)\s*$/)
    if (fence) {
      if (code) flushCode(); else { flushParagraph(); flushList(); code = []; language = fence[1] || 'text' }
      continue
    }
    if (code) { code.push(line); continue }
    if (!line.trim()) { flushParagraph(); flushList(); continue }
    const heading = line.match(/^\s*#{1,3}\s+(.+)$/)
    if (heading) { flushParagraph(); flushList(); output.push('<h3>' + escapeHtml(heading[1]) + '</h3>'); continue }
    const item = line.match(/^\s*[-*]\s+(.+)$/)
    if (item) { flushParagraph(); list.push(item[1]); continue }
    flushList(); paragraph.push(line)
  }
  if (code) flushCode(); flushParagraph(); flushList()
  return output.join('')
}
