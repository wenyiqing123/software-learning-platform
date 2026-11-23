<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; description?: string; embedded?: boolean }>()
</script>

<template>
  <header class="section-heading" :class="{ 'is-subheading': embedded }">
    <div class="section-heading-copy">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <component :is="embedded ? 'h2' : 'h1'">{{ title }}</component>
      <p v-if="description" class="section-description">{{ description }}</p>
    </div>
    <slot />
  </header>
</template>
