import { api } from './http'
import type { LearningBookmark, LearningBookmarkItem, LearningNote } from './types'

export const notes = (params?: { sectionId?: number; knowledgePointId?: number }) =>
  api.get<LearningNote[]>('/learning/notes', { params }).then((r) => r.data)

export const createNote = (payload: { sectionId?: number; knowledgePointId?: number; title: string; content: string; tags: string[] }) =>
  api.post<LearningNote>('/learning/notes', payload).then((r) => r.data)

export const updateNote = (id: number, payload: { sectionId?: number; knowledgePointId?: number; title: string; content: string; tags: string[] }) =>
  api.put<LearningNote>('/learning/notes/' + id, payload).then((r) => r.data)

export const deleteNote = (id: number) =>
  api.delete<void>('/learning/notes/' + id).then((r) => r.data)

export const bookmarksList = () =>
  api.get<LearningBookmarkItem[]>('/learning/bookmarks/list').then((r) => r.data)

export const setBookmark = (payload: { sectionId?: number; knowledgePointId?: number; bookmarked: boolean }) =>
  api.put<LearningBookmark>('/learning/bookmarks', payload).then((r) => r.data)
