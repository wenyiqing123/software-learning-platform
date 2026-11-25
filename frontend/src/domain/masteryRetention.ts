export type ReviewUrgency = 'FRESH' | 'FADING' | 'DUE'

export interface MasteryRetentionFields {
  mastery?: number | null
  peakMastery?: number | null
  effectiveMastery?: number | null
  daysSinceLastPractice?: number | null
  reviewUrgency?: ReviewUrgency | string | null
}

export const HALF_LIFE_DAYS = 14

export const RETENTION_RULE_SUMMARY =
  '基于距上次有效学习天数的可解释遗忘衰减规则：间隔 ≤ 1 天不衰减，超过 1 天按常数半衰期 14 天估算当前保持度；先修解锁门槛（60%）严格以历史峰值为准，不因久未复习而回退锁死。'

export function clampMasteryPercent(value: number | null | undefined): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null
  return Math.round(Math.max(0, Math.min(100, value)))
}

export function effectiveMasteryPercent(item: MasteryRetentionFields | null | undefined): number {
  if (!item) return 0
  return clampMasteryPercent(item.effectiveMastery) ?? clampMasteryPercent(item.mastery) ?? 0
}

export function peakMasteryPercent(item: MasteryRetentionFields | null | undefined): number {
  if (!item) return 0
  const peak = clampMasteryPercent(item.peakMastery) ?? 0
  const current = clampMasteryPercent(item.mastery) ?? 0
  return Math.max(peak, current)
}

export function daysSincePractice(item: MasteryRetentionFields | null | undefined): number {
  if (!item || typeof item.daysSinceLastPractice !== 'number' || !Number.isFinite(item.daysSinceLastPractice)) {
    return 0
  }
  return Math.max(0, Math.floor(item.daysSinceLastPractice))
}

export function normalizeReviewUrgency(item: MasteryRetentionFields | null | undefined): ReviewUrgency {
  const raw = item?.reviewUrgency
  if (raw === 'DUE') return 'DUE'
  if (raw === 'FADING') return 'FADING'
  return 'FRESH'
}

export function retentionAlertLabel(item: MasteryRetentionFields | null | undefined): string | null {
  const urgency = normalizeReviewUrgency(item)
  if (urgency === 'FRESH') return null
  const days = daysSincePractice(item)
  const prefix = urgency === 'DUE' ? '遗忘预警' : '待巩固'
  return days > 0 ? `${prefix} · 已隔 ${days} 天` : prefix
}
