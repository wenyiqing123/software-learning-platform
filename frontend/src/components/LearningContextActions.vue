<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, BookOpen, BrainCircuit, GraduationCap, Network } from '@lucide/vue'
import { feynmanHref, graphHref, practiceHref, returnHref, tutorHref, type LearningContext } from '../domain/learningContext'

interface Props {
  context?: LearningContext | null
  compact?: boolean
  currentView?: 'course' | 'graph' | 'practice' | 'review' | 'tutor'
  showReturn?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  context: null,
  compact: false,
  currentView: undefined,
  showReturn: false,
})

const safeContext = computed<LearningContext>(() => props.context || {})

const breadcrumbs = computed(() => {
  const items: string[] = []
  if (!props.context) return items
  const c = props.context
  if (c.courseTitle) items.push(c.courseTitle)
  else if (c.courseId) items.push(`课程 #${c.courseId}`)

  if (c.sectionTitle) items.push(c.sectionTitle)
  else if (c.sectionId) items.push(`小节 #${c.sectionId}`)

  if (c.knowledgePointTitle) items.push(c.knowledgePointTitle)
  else if (c.entity) items.push(c.entity)
  else if (c.knowledgePointId) items.push(`知识点 #${c.knowledgePointId}`)

  return items
})

const resolvedReturnHref = computed(() => returnHref(props.context))

const shouldShowReturn = computed(() => {
  if (props.showReturn) return true
  if (props.currentView && props.currentView !== 'course') return true
  return false
})
</script>

<template>
  <nav class="context-actions" :class="{ compact }" aria-label="学习上下文快捷入口">
    <div v-if="breadcrumbs.length" class="context-breadcrumbs" aria-label="学习路径面包屑">
      <span v-for="(item, idx) in breadcrumbs" :key="idx" class="breadcrumb-item">
        <span v-if="idx > 0" class="breadcrumb-separator">/</span>
        {{ item }}
      </span>
    </div>
    <div class="context-links">
      <RouterLink
        v-if="shouldShowReturn"
        class="context-action context-return-link"
        :to="resolvedReturnHref"
      >
        <ArrowLeft :size="14" />返回此学习内容
      </RouterLink>
      <RouterLink
        v-if="currentView !== 'graph'"
        class="context-action context-graph-link"
        :to="graphHref(safeContext)"
      >
        <Network :size="14" />查看图谱关系
      </RouterLink>
      <RouterLink
        v-if="currentView !== 'tutor'"
        class="context-action context-tutor-link"
        :to="tutorHref(safeContext)"
      >
        <BrainCircuit :size="14" />向助教提问
      </RouterLink>
      <RouterLink
        v-if="currentView !== 'tutor'"
        class="context-action context-feynman-link"
        :to="feynmanHref(safeContext)"
      >
        <GraduationCap :size="14" />费曼自检
      </RouterLink>
      <RouterLink
        v-if="currentView !== 'practice'"
        class="context-action context-practice-link"
        :to="practiceHref(safeContext)"
      >
        <BookOpen :size="14" />练习本节题目
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.context-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.context-actions.compact {
  gap: 4px;
}

.context-breadcrumbs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.breadcrumb-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.breadcrumb-separator {
  color: var(--border-default);
}

.context-links {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.context-actions.compact .context-links {
  gap: 6px;
}

.context-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-tint);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.context-action:hover {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.context-actions.compact .context-action {
  font-size: 12px;
  padding: 2px 6px;
}

.context-return-link {
  color: var(--text-muted);
}

.context-return-link:hover {
  color: var(--text);
}

.context-graph-link {
  color: var(--accent);
}

.context-tutor-link {
  color: var(--accent);
}

.context-feynman-link {
  color: var(--accent);
}

.context-practice-link {
  color: var(--accent);
}
</style>
