<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionHeading from '../components/SectionHeading.vue'
import ExperimentsView from './ExperimentsView.vue'
import ExperimentTaskAdminView from './ExperimentTaskAdminView.vue'
import EvaluationAdminView from './EvaluationAdminView.vue'

const route = useRoute()
const router = useRouter()
const tabs = [
  { key: 'comparison', label: '问答对照实验', component: ExperimentsView },
  { key: 'tasks', label: '任务与分组', component: ExperimentTaskAdminView },
  { key: 'evaluation', label: '评价统计', component: EvaluationAdminView },
]
const activeKey = computed(() => tabs.some(tab => tab.key === route.query.tab) ? String(route.query.tab) : 'comparison')
const activeTab = computed(() => tabs.find(tab => tab.key === activeKey.value) || tabs[0])
function selectTab(key: string) { void router.replace({ path: '/admin/experiments', query: { ...route.query, tab: key } }) }
</script>

<template>
  <div class="page-stack admin-center-page">
    <SectionHeading eyebrow="管理员工作台 · 研究与评价" title="实验与评价中心" description="统一管理问答对照、实验任务分组和去标识化评价统计。" />
    <nav class="admin-center-tabs" role="tablist" aria-label="实验与评价中心">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        :data-testid="`tab-${tab.key}`"
        :aria-selected="activeKey === tab.key"
        :class="{ active: activeKey === tab.key }"
        @click="selectTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>
    <KeepAlive>
      <component :is="activeTab.component" embedded />
    </KeepAlive>
  </div>
</template>
