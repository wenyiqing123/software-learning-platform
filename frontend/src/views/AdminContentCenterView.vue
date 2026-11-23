<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SectionHeading from '../components/SectionHeading.vue'
import AdminView from './AdminView.vue'
import QuestionBankView from './QuestionBankView.vue'
import AdminDiagnosisView from './AdminDiagnosisView.vue'
import MicroLessonAdminView from './MicroLessonAdminView.vue'

const route = useRoute()
const router = useRouter()
const tabs = [
  { key: 'course', label: '课程与知识点', component: AdminView },
  { key: 'question-bank', label: '题库与版本', component: QuestionBankView },
  { key: 'diagnosis', label: '错因诊断', component: AdminDiagnosisView },
  { key: 'micro-lessons', label: '微课管理', component: MicroLessonAdminView },
]
const activeKey = computed(() => tabs.some(tab => tab.key === route.query.tab) ? String(route.query.tab) : 'course')
const activeTab = computed(() => tabs.find(tab => tab.key === activeKey.value) || tabs[0])
function selectTab(key: string) { void router.replace({ path: '/admin/content', query: { tab: key } }) }
</script>

<template>
  <div class="page-stack admin-center-page">
    <SectionHeading eyebrow="管理员工作台 · 内容资产" title="内容与题库中心" description="在同一个工作台维护课程、知识点、题库版本、错因诊断和已发布微课。" />
    <nav class="admin-center-tabs" role="tablist" aria-label="内容与题库中心">
      <button v-for="tab in tabs" :key="tab.key" type="button" role="tab" :aria-selected="activeKey === tab.key" :class="{ active: activeKey === tab.key }" @click="selectTab(tab.key)">{{ tab.label }}</button>
    </nav>
    <component :is="activeTab.component" embedded />
  </div>
</template>
