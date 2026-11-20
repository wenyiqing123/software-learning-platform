export interface SseFrame { event: string; data: string }

/** Incremental event framing; CRLF may span transport chunks. */
export class SseDecoder {
  private buffer = ''
  private event = 'message'
  private data: string[] = []
  constructor(private readonly receive: (frame: SseFrame) => void) {}
  push(chunk: string) { this.buffer += chunk; this.consume(false) }
  finish() { this.consume(true); if (this.buffer) { this.line(this.buffer); this.buffer = '' }; this.line('') }
  private consume(final: boolean) {
    let offset = 0
    for (let index = 0; index < this.buffer.length; index++) {
      const char = this.buffer[index]
      if (char !== '\r' && char !== '\n') continue
      if (char === '\r' && index === this.buffer.length - 1 && !final) break
      this.line(this.buffer.slice(offset, index))
      if (char === '\r' && this.buffer[index + 1] === '\n') index++
      offset = index + 1
    }
    this.buffer = this.buffer.slice(offset)
  }
  private line(line: string) {
    if (!line) {
      if (this.data.length) this.receive({ event: this.event, data: this.data.join('\n') })
      this.event = 'message'; this.data = []; return
    }
    if (line.startsWith(':')) return
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') this.event = value
    else if (field === 'data') this.data.push(value)
  }
}
