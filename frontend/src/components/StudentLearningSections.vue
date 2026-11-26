<script lang="ts">
export interface SectionItem {
  path: string
  label: string
}

export const STUDENT_SECTIONS: Record<string, SectionItem[]> = {
  overview: [
    { path: '/dashboard', label: '学习概览' },
    { path: '/courses', label: '课程阅读' },
    { path: '/notes', label: '笔记与收藏' },
  ],
  'graph-tutor': [
    { path: '/knowledge', label: '知识图谱' },
    { path: '/mastery-map', label: '掌握图谱' },
    { path: '/tutor', label: 'AI 学习助教' },
    { path: '/micro-lessons', label: '证据微课' },
  ],
  practice: [
    { path: '/practice', label: '课程练习' },
    { path: '/adaptive-practice', label: '专项练习' },
    { path: '/wrong-book', label: '错题整理' },
    { path: '/review', label: '到期复习' },
  ],
  plan: [
    { path: '/study-plan', label: '每日计划' },
    { path: '/recommendations', label: '为我推荐' },
  ],
  experiment: [
    { path: '/experiment-tasks', label: '实验任务' },
    { path: '/evaluation', label: '学习评估' },
    { path: '/learning-report', label: '学习报告' },
  ],
}

const COURSE_QUERY_KEYS = ['courseId', 'sectionId', 'knowledgePointId', 'entity']
const EXPERIMENT_QUERY_KEYS = ['taskId', 'assignmentId', 'conditionKey', 'taskKey', 'taskOrder']

export function resolveItemQuery(targetPath: string, rawQuery: Record<string, any> = {}): Record<string, any> | undefined {
  const clean: Record<string, any> = {}
  if (targetPath === '/courses') {
    for (const k of COURSE_QUERY_KEYS) {
      if (rawQuery[k] !== undefined && rawQuery[k] !== null && rawQuery[k] !== '') {
        clean[k] = rawQuery[k]
      }
    }
  } else if (['/evaluation', '/experiment-tasks', '/learning-report'].includes(targetPath)) {
    for (const k of EXPERIMENT_QUERY_KEYS) {
      if (rawQuery[k] !== undefined && rawQuery[k] !== null && rawQuery[k] !== '') {
        clean[k] = rawQuery[k]
      }
    }
  }
  return Object.keys(clean).length > 0 ? clean : undefined
}

export function findGroupByPath(targetPath: string): string | null {
  for (const [gid, items] of Object.entries(STUDENT_SECTIONS)) {
    if (items.some(item => item.path === targetPath)) {
      return gid
    }
  }
  return null
}
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = withDefaults(
  defineProps<{
    groupId: string
    path: string
    query?: Record<string, any>
  }>(),
  {
    query: () => ({}),
  }
)

const currentItems = computed(() => STUDENT_SECTIONS[props.groupId] || [])
</script>

<template>
  <nav v-if="currentItems.length" class="student-sections-strip" aria-label="学习子级导航">
    <RouterLink
      v-for="item in currentItems"
      :key="item.path"
      :to="{ path: item.path, query: resolveItemQuery(item.path, query) }"
      class="student-section-link"
      :class="{ active: path === item.path }"
      :aria-current="path === item.path ? 'page' : undefined"
    >
      {{ item.label }}
    </RouterLink>
  </nav>
</template>
