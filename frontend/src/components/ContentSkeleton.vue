<script setup lang="ts">
import { computed } from 'vue'

export type SkeletonVariant = 'cards' | 'reading' | 'list' | 'flashcard'

const props = withDefaults(
  defineProps<{
    variant?: SkeletonVariant
    rows?: number
    label?: string
  }>(),
  {
    variant: 'cards',
    rows: 3,
    label: '正在加载内容…',
  },
)

const rowIndices = computed(() => Array.from({ length: Math.max(1, Math.min(8, props.rows)) }, (_, idx) => idx))
</script>

<template>
  <section
    class="content-skeleton"
    :class="`is-${variant}`"
    role="status"
    aria-busy="true"
    aria-live="polite"
    :aria-label="label"
    data-testid="content-skeleton"
  >
    <span class="skeleton-sr">{{ label }}</span>

    <div v-if="variant === 'reading'" class="skeleton-reading-layout">
      <div class="skeleton-panel skeleton-outline-col">
        <span class="skeleton-block sk-title"></span>
        <span v-for="idx in 5" :key="idx" class="skeleton-block sk-outline-item"></span>
      </div>
      <div class="skeleton-panel skeleton-article-col">
        <span class="skeleton-block sk-eyebrow"></span>
        <span class="skeleton-block sk-heading"></span>
        <div class="skeleton-callout">
          <span class="skeleton-block sk-line"></span>
          <span class="skeleton-block sk-line sk-short"></span>
        </div>
        <div class="skeleton-paragraphs">
          <span class="skeleton-block sk-line"></span>
          <span class="skeleton-block sk-line"></span>
          <span class="skeleton-block sk-line sk-medium"></span>
        </div>
      </div>
    </div>

    <div v-else-if="variant === 'flashcard'" class="skeleton-flashcard">
      <span class="skeleton-block sk-eyebrow"></span>
      <span class="skeleton-block sk-heading"></span>
      <span class="skeleton-block sk-pill"></span>
      <span class="skeleton-block sk-line sk-medium"></span>
      <span class="skeleton-block sk-pill sk-cta"></span>
    </div>

    <div v-else-if="variant === 'cards'" class="skeleton-cards-grid">
      <div v-for="idx in rowIndices" :key="idx" class="skeleton-card-item">
        <span class="skeleton-block sk-icon"></span>
        <div class="skeleton-copy">
          <span class="skeleton-block sk-title"></span>
          <span class="skeleton-block sk-line sk-medium"></span>
        </div>
      </div>
    </div>

    <div v-else class="skeleton-list">
      <div v-for="idx in rowIndices" :key="idx" class="skeleton-list-row">
        <span class="skeleton-block sk-dot"></span>
        <div class="skeleton-copy">
          <span class="skeleton-block sk-title"></span>
          <span class="skeleton-block sk-line sk-short"></span>
        </div>
        <span class="skeleton-block sk-pill"></span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.content-skeleton {
  min-width: 0;
}

.skeleton-sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.skeleton-block {
  display: block;
  border-radius: var(--radius-xs);
  background: linear-gradient(
    90deg,
    var(--surface-tint) 25%,
    color-mix(in srgb, var(--surface-soft) 55%, var(--surface)) 50%,
    var(--surface-tint) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.skeleton-reading-layout {
  display: grid;
  grid-template-columns: minmax(214px, 254px) minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}

.skeleton-panel {
  display: grid;
  gap: 12px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.skeleton-outline-col {
  padding: 16px;
}

.skeleton-article-col {
  max-width: var(--reading-width);
  padding: 24px clamp(20px, 3vw, 40px);
}

.skeleton-callout {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  background: var(--surface-soft);
  border-radius: var(--radius-sm);
}

.skeleton-paragraphs {
  display: grid;
  gap: 10px;
  margin-top: 6px;
}

.skeleton-flashcard {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 14px;
  min-height: 290px;
  padding: 28px 24px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.skeleton-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
}

.skeleton-card-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.skeleton-list {
  display: grid;
  gap: 8px;
}

.skeleton-list-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.skeleton-copy {
  display: grid;
  gap: 7px;
  min-width: 0;
  flex: 1;
}

.sk-eyebrow {
  width: 110px;
  height: 12px;
}

.sk-heading {
  width: min(300px, 78%);
  height: 28px;
}

.sk-title {
  width: 68%;
  height: 16px;
}

.sk-outline-item {
  width: 100%;
  height: 34px;
}

.sk-line {
  width: 100%;
  height: 13px;
}

.sk-medium {
  width: 78%;
}

.sk-short {
  width: 54%;
}

.sk-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  flex: none;
}

.sk-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
}

.sk-pill {
  width: 84px;
  height: 24px;
  border-radius: var(--radius-pill);
}

.sk-cta {
  width: 168px;
  height: 34px;
  margin-top: 6px;
}

@media (max-width: 760px) {
  .skeleton-reading-layout {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block {
    animation: none;
    background: var(--surface-tint);
  }
}
</style>
