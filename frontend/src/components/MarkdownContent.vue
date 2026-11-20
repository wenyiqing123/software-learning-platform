<script setup lang="ts">
import { computed } from 'vue'
import { renderSimpleMarkdown } from '../domain/markdown'

const props = withDefaults(defineProps<{ content?: string }>(), { content: '' })
const html = computed(() => renderSimpleMarkdown(props.content))
</script>

<template><div class="markdown-content" v-html="html"></div></template>
