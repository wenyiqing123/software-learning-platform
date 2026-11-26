import { api } from './http'

export interface FlashcardItem {
  knowledgePointId: number
  courseId: number
  courseName: string
  chapterName?: string | null
  sectionId?: number | null
  sectionTitle?: string | null
  objectives: string[]
  name: string
  graphEntity: string
  summary: string
  masteryScore: number
  recallRating?: number | null
  reviewCount: number
  intervalMinutes?: number | null
  due: boolean
  nextDueAt?: string | null
  lastReviewedAt?: string | null
}

export const flashcards = (params?: { courseId?: number; dueOnly?: boolean }) =>
  api.get<FlashcardItem[]>('/flashcards', { params }).then((r) => r.data)

export const reviewFlashcard = (knowledgePointId: number, recallRating: 0 | 1 | 2 | 3) =>
  api.post<FlashcardItem>(`/flashcards/${knowledgePointId}/review`, { recallRating }).then((r) => r.data)
