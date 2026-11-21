import { api, refreshAccessToken } from './http'
import { SseDecoder } from '../domain/sse'
import type { TutorHintResult } from './types'

export const ask = (question: string, mode: string) => api.post('/ai/ask', { question, mode }).then((r) => r.data)
export const tutorHint = (question: string, level: number, historyMessages: string[] = [], historyEntities: string[] = []) => api.post<TutorHintResult>('/ai/tutor/hint', { question, level, historyMessages, historyEntities }).then((r) => r.data)
export const sessions = () => api.get('/tutor/sessions').then(r => r.data)
export const createSession = (title: string) => api.post('/tutor/sessions', { title }).then(r => r.data)
export const sessionMessages = (id: number) => api.get('/tutor/sessions/' + id + '/messages').then(r => r.data)
export const saveMessage = (id: number, payload: { role: string; content: string; mode: string; metadata?: unknown }) => api.post('/tutor/sessions/' + id + '/messages', payload).then(r => r.data)
export const deleteSession = (id: number) => api.delete('/tutor/sessions/' + id).then(r => r.data)

export async function streamAsk(question: string, mode: string, onEvent: (name: string, payload: any) => void, signal?: AbortSignal, history?: { historyMessages: string[]; historyEntities: string[] }) {
  const request = () => fetch('/api/ai/ask/stream', {
    method: 'POST', signal,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + (localStorage.getItem('slp-access-token') || ''), Accept: 'text/event-stream' },
    body: JSON.stringify({ question, mode, ...history }),
  })
  let response = await request()
  if (response.status === 401) { await refreshAccessToken(); response = await request() }
  if (!response.ok || !response.body) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || error.detail || 'AI 服务暂不可用')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let completed: any = null
  const parser = new SseDecoder(({ event, data }) => {
    let payload: any
    try { payload = JSON.parse(data) } catch { payload = { content: data } }
    if (event === 'error') throw new Error(payload.message || 'AI 请求失败')
    if (event === 'done') completed = payload
    onEvent(event, payload)
  })
  try {
    while (true) {
      const { value, done } = await reader.read()
      parser.push(decoder.decode(value || new Uint8Array(), { stream: !done }))
      if (done) break
    }
    parser.finish()
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
  if (!completed) throw new Error('AI 流式响应未正常结束，请重试')
  return completed
}
