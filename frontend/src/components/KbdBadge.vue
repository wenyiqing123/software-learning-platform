<script setup lang="ts">
withDefaults(
  defineProps<{
    keys?: string[]
    separator?: string
  }>(),
  {
    keys: undefined,
    separator: '+',
  },
)
</script>

<template>
  <span v-if="keys && keys.length" class="kbd-group">
    <template v-for="(keycap, idx) in keys" :key="`${keycap}-${idx}`">
      <span v-if="idx > 0" class="kbd-sep" aria-hidden="true">{{ separator }}</span>
      <kbd class="kbd-badge">{{ keycap }}</kbd>
    </template>
  </span>
  <kbd v-else class="kbd-badge">
    <slot />
  </kbd>
</template>

<style scoped>
.kbd-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.kbd-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-bottom-width: 2px;
  border-radius: var(--radius-xs);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.kbd-sep {
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 600;
}
</style>
