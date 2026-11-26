<script setup lang="ts">
import { CircleAlert, Inbox, LoaderCircle } from '@lucide/vue'
import ContentSkeleton, { type SkeletonVariant } from './ContentSkeleton.vue'

withDefaults(defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  skeleton?: SkeletonVariant
  skeletonRows?: number
  loadingTitle?: string
  loadingText?: string
  errorTitle?: string
  emptyTitle?: string
  emptyText?: string
  retryLabel?: string
  emptyActionLabel?: string
}>(), {
  loading: false,
  error: '',
  empty: false,
  skeleton: undefined,
  skeletonRows: 3,
  loadingTitle: '正在加载',
  loadingText: '正在读取真实学习数据，请稍候。',
  errorTitle: '暂时无法完成请求',
  emptyTitle: '这里还没有内容',
  emptyText: '完成一次学习操作后，这里会显示相关记录。',
  retryLabel: '重试',
  emptyActionLabel: '',
})

const emit = defineEmits<{ retry: []; action: [] }>()
</script>

<template>
  <ContentSkeleton v-if="loading && skeleton" :variant="skeleton" :rows="skeletonRows" :label="loadingTitle" />
  <section v-else-if="loading" class="feedback-state feedback-loading" role="status" aria-live="polite">
    <LoaderCircle class="feedback-icon feedback-spin" :size="24" />
    <strong>{{ loadingTitle }}</strong>
    <span>{{ loadingText }}</span>
  </section>
  <section v-else-if="error" class="feedback-state feedback-error" role="alert">
    <CircleAlert class="feedback-icon" :size="24" />
    <strong>{{ errorTitle }}</strong>
    <span>{{ error }}</span>
    <button class="button button-outline button-small" type="button" @click="emit('retry')">{{ retryLabel }}</button>
  </section>
  <section v-else-if="empty" class="feedback-state feedback-empty" role="status">
    <Inbox class="feedback-icon" :size="24" />
    <strong>{{ emptyTitle }}</strong>
    <span>{{ emptyText }}</span>
    <button v-if="emptyActionLabel" class="button button-outline button-small" type="button" @click="emit('action')">{{ emptyActionLabel }}</button>
  </section>
  <slot v-else />
</template>
