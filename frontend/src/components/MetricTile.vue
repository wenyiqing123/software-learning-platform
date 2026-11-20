<script setup lang="ts">
defineProps<{ label: string; value: string | number; detail: string; tone?: 'green' | 'orange' | 'blue' }>()
</script>

<template>
  <article class="metric-tile" :class="tone || 'green'">
    <p>{{ label }}</p>
    <strong>{{ value }}</strong>
    <span>{{ detail }}</span>
  </article>
</template>
