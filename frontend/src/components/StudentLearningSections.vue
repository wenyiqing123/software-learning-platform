<script lang="ts">
import {
  STUDENT_SECTIONS,
  findGroupByPath,
  resolveItemQuery,
  type SectionItem,
} from '../domain/navigation'

export { STUDENT_SECTIONS, findGroupByPath, resolveItemQuery, type SectionItem }
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
