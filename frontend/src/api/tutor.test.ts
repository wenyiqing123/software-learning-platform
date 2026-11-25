import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renameSession, tutorHint } from './tutor'

const http = vi.hoisted(() => ({ patch: vi.fn(), post: vi.fn() }))
vi.mock('./http', () => ({ api: http, refreshAccessToken: vi.fn() }))

describe('tutor API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('renames a tutor session through the owned session endpoint', async () => {
    http.patch.mockResolvedValue({ data: { id: 7, title: '数据流图复习' } })

    await expect(renameSession(7, '数据流图复习')).resolves.toEqual({ id: 7, title: '数据流图复习' })
    expect(http.patch).toHaveBeenCalledWith('/tutor/sessions/7', { title: '数据流图复习' })
  })

  it('forwards optional explicit entities when calling tutorHint', async () => {
    http.post.mockResolvedValue({ data: { level: 1, content: '提示' } })

    await tutorHint('如何命名？', 1, [], [], ['加工'])
    expect(http.post).toHaveBeenCalledWith('/ai/tutor/hint', {
      question: '如何命名？',
      level: 1,
      historyMessages: [],
      historyEntities: [],
      entities: ['加工'],
    })
  })
})
