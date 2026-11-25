import { describe, expect, it } from 'vitest'
import {
  daysSincePractice,
  effectiveMasteryPercent,
  HALF_LIFE_DAYS,
  normalizeReviewUrgency,
  peakMasteryPercent,
  RETENTION_RULE_SUMMARY,
  retentionAlertLabel,
} from './masteryRetention'

describe('masteryRetention helpers', () => {
  it('computes clamped effective and peak mastery with backward-compatible fallbacks', () => {
    expect(effectiveMasteryPercent({ mastery: 82.4 })).toBe(82)
    expect(effectiveMasteryPercent({ mastery: 82, effectiveMastery: 44.6 })).toBe(45)
    expect(peakMasteryPercent({ mastery: 55 })).toBe(55)
    expect(peakMasteryPercent({ mastery: 55, peakMastery: 88.2 })).toBe(88)
    expect(effectiveMasteryPercent({ mastery: 130, effectiveMastery: -5 })).toBe(0)
    expect(peakMasteryPercent({ mastery: 120, peakMastery: 95 })).toBe(100)
  })

  it('formats non-color urgency alert labels and exposes transparent 14-day rule copy', () => {
    expect(HALF_LIFE_DAYS).toBe(14)
    expect(RETENTION_RULE_SUMMARY).toContain('半衰期 14 天')
    expect(RETENTION_RULE_SUMMARY).not.toContain('机器学习')
    expect(retentionAlertLabel({ reviewUrgency: 'FRESH', daysSinceLastPractice: 1 })).toBeNull()
    expect(retentionAlertLabel({ reviewUrgency: 'FADING', daysSinceLastPractice: 6 })).toBe('待巩固 · 已隔 6 天')
    expect(retentionAlertLabel({ reviewUrgency: 'DUE', daysSinceLastPractice: 14 })).toBe('遗忘预警 · 已隔 14 天')
    expect(daysSincePractice({ daysSinceLastPractice: -3 })).toBe(0)
    expect(normalizeReviewUrgency({ reviewUrgency: 'DUE' })).toBe('DUE')
    expect(normalizeReviewUrgency({ reviewUrgency: 'UNKNOWN' })).toBe('FRESH')
  })
})
