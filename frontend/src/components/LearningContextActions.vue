<script setup lang="ts">
import { BookOpen, BrainCircuit, Network } from '@lucide/vue'
import { graphHref, practiceHref, tutorHref, type LearningContext } from '../domain/learningContext'

const props = withDefaults(defineProps<{ context: LearningContext; compact?: boolean }>(), { compact: false })
</script>

<template>
  <nav class="context-actions" :class="{ compact }" aria-label="学习上下文快捷入口">
    <RouterLink class="context-action" :to="graphHref(props.context)"><Network :size="14" />查看图谱关系</RouterLink>
    <RouterLink class="context-action" :to="tutorHref(props.context)"><BrainCircuit :size="14" />向助教提问</RouterLink>
    <RouterLink class="context-action" :to="practiceHref(props.context)"><BookOpen :size="14" />练习本节题目</RouterLink>
  </nav>
</template>
