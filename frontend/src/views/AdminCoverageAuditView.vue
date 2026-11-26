<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, CheckCircle2, CircleAlert, ClipboardCheck, RotateCcw, Search } from '@lucide/vue'
import { adminCoverageAudit } from '../api/admin'
import { api, apiError } from '../api/http'
import type { Course, CoverageAuditGap, CoverageAuditItem, CoverageAuditReport, CoverageGapCode } from '../api/types'
import ContentSkeleton from '../components/ContentSkeleton.vue'
import SectionHeading from '../components/SectionHeading.vue'

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const courses = ref<Course[]>([])
const selectedCourseId = ref<number | undefined>(undefined)
const gapFilter = ref<'ALL' | CoverageGapCode>('ALL')
const search = ref('')
const loading = ref(true)
const error = ref('')
const report = ref<CoverageAuditReport | null>(null)

let requestSeq = 0
let active = true

const items = computed(() => report.value?.items ?? [])
const summary = computed(() => report.value?.summary ?? {
  totalPoints: 0,
  fullyCoveredPoints: 0,
  missingQuestionsCount: 0,
  missingDiagnosisCount: 0,
  missingMicroLessonCount: 0,
  missingFlashcardCount: 0,
  coveredQuestionsTotal: 0,
  coveredDiagnosisTotal: 0,
  publishedMicroLessonsTotal: 0,
  flashcardReviewRecordsTotal: 0,
})

const filteredItems = computed<CoverageAuditItem[]>(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return items.value.filter((item) => {
    const matchesGap = gapFilter.value === 'ALL' || item.gaps.some(gap => gap.code === gapFilter.value)
    if (!matchesGap) return false
    if (!query) return true
    return (
      item.knowledgePointName.toLocaleLowerCase().includes(query)
      || item.graphEntity.toLocaleLowerCase().includes(query)
      || (item.chapterName || '').toLocaleLowerCase().includes(query)
      || item.courseName.toLocaleLowerCase().includes(query)
    )
  })
})

const gapFilterOptions = computed<Array<{ key: 'ALL' | CoverageGapCode; label: string; count: number }>>(() => [
  { key: 'ALL', label: '全部知识点', count: summary.value.totalPoints },
  { key: 'NO_QUESTIONS', label: '缺练习题', count: summary.value.missingQuestionsCount },
  { key: 'NO_DIAGNOSIS', label: '缺错因诊断', count: summary.value.missingDiagnosisCount },
  { key: 'NO_MICRO_LESSON', label: '缺微课', count: summary.value.missingMicroLessonCount },
  { key: 'NO_FLASHCARD', label: '缺闪卡支撑', count: summary.value.missingFlashcardCount },
])

async function loadCourses() {
  try {
    const res = await api.get<Course[]>('/admin/courses')
    if (!active) return
    courses.value = Array.isArray(res.data) ? res.data : []
  } catch {
    if (active) courses.value = []
  }
}

async function loadAudit() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const data = await adminCoverageAudit(selectedCourseId.value)
    if (!active || seq !== requestSeq) return
    report.value = data
  } catch (cause) {
    if (!active || seq !== requestSeq) return
    report.value = null
    error.value = apiError(cause, '知识资产覆盖度体检暂时不可用，请确认权限并稍后重试。')
  } finally {
    if (active && seq === requestSeq) {
      loading.value = false
    }
  }
}

function jumpToFix(item: CoverageAuditItem, gap: CoverageAuditGap) {
  void router.push({
    path: '/admin/content',
    query: {
      tab: gap.targetTab,
      courseId: String(item.courseId),
    },
  })
}

watch(selectedCourseId, () => {
  void loadAudit()
})

onMounted(async () => {
  await loadCourses()
  if (active) await loadAudit()
})

onBeforeUnmount(() => {
  active = false
  requestSeq++
})
</script>

<template>
  <div class="page-stack coverage-audit-page" data-testid="coverage-audit-panel">
    <SectionHeading
      :embedded="props.embedded"
      eyebrow="内容质量体检"
      title="知识资产覆盖度体检"
      description="按课程知识点聚合启用练习题、干扰项错因诊断、已发布微课与概念闪卡关联情况，快速定位缺口并跳转补录。"
    >
      <span class="admin-badge">只读 SQL 审计</span>
    </SectionHeading>

    <div v-if="error" class="inline-notice" role="alert" data-testid="coverage-audit-error">
      <CircleAlert :size="16" />
      <span>{{ error }}</span>
      <button type="button" class="text-button" data-testid="coverage-retry-btn" @click="loadAudit">重试体检 <RotateCcw :size="13" /></button>
    </div>

    <section class="coverage-summary-grid" data-testid="coverage-summary-cards" aria-label="覆盖度统计概览">
      <article class="coverage-stat-card">
        <span class="coverage-stat-label">纳入体检知识点</span>
        <strong class="coverage-stat-value">{{ loading ? '—' : summary.totalPoints }}</strong>
        <span class="coverage-stat-sub">{{ report?.courseName || '全部课程' }}</span>
      </article>
      <article class="coverage-stat-card">
        <span class="coverage-stat-label">四维全覆盖</span>
        <strong class="coverage-stat-value coverage-stat-accent">{{ loading ? '—' : summary.fullyCoveredPoints }}</strong>
        <span class="coverage-stat-sub">缺题 {{ summary.missingQuestionsCount }} · 缺闪卡 {{ summary.missingFlashcardCount }}</span>
      </article>
      <article class="coverage-stat-card">
        <span class="coverage-stat-label">启用题库 / 错因</span>
        <strong class="coverage-stat-value">{{ loading ? '—' : `${summary.coveredQuestionsTotal} / ${summary.coveredDiagnosisTotal}` }}</strong>
        <span class="coverage-stat-sub">缺错因诊断 {{ summary.missingDiagnosisCount }} 个知识点</span>
      </article>
      <article class="coverage-stat-card">
        <span class="coverage-stat-label">已发布微课 / 闪卡复习</span>
        <strong class="coverage-stat-value">{{ loading ? '—' : `${summary.publishedMicroLessonsTotal} / ${summary.flashcardReviewRecordsTotal}` }}</strong>
        <span class="coverage-stat-sub">缺已发布微课 {{ summary.missingMicroLessonCount }} 个知识点</span>
      </article>
    </section>

    <section class="content-section coverage-table-section">
      <div class="coverage-toolbar">
        <div class="coverage-filter-pills" role="tablist" aria-label="按资源缺口筛选" data-testid="coverage-filter-pills">
          <button
            v-for="pill in gapFilterOptions"
            :key="pill.key"
            type="button"
            role="tab"
            class="coverage-filter-pill"
            :class="{ active: gapFilter === pill.key }"
            :aria-selected="gapFilter === pill.key"
            :data-testid="`coverage-filter-${pill.key}`"
            @click="gapFilter = pill.key"
          >
            <span>{{ pill.label }}</span>
            <span class="coverage-pill-count">{{ pill.count }}</span>
          </button>
        </div>

        <div class="coverage-controls">
          <label class="coverage-course-filter">
            <span>课程</span>
            <select v-model="selectedCourseId" aria-label="体检筛选课程" data-testid="coverage-course-select">
              <option :value="undefined">全部课程</option>
              <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }} · {{ c.term }}</option>
            </select>
          </label>
          <label class="search-field">
            <Search :size="16" />
            <input v-model="search" placeholder="筛选知识点、章节或实体" data-testid="coverage-search-input" />
          </label>
        </div>
      </div>

      <p v-if="report?.ruleDescription" class="coverage-rule-note" data-testid="coverage-rule-note">
        <ClipboardCheck :size="14" aria-hidden="true" />
        <span>{{ report.ruleDescription }}</span>
      </p>

      <ContentSkeleton v-if="loading" variant="list" :rows="4" label="正在执行只读覆盖度统计" />

      <div v-else-if="filteredItems.length" class="coverage-table-wrap">
        <table class="coverage-table" data-testid="coverage-audit-table">
          <thead>
            <tr>
              <th>知识点与所属章节</th>
              <th>小节 / 闪卡</th>
              <th>启用练习题</th>
              <th>错因诊断</th>
              <th>已发布微课</th>
              <th>确定性缺口与补录入口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.knowledgePointId" :data-testid="`coverage-row-${item.knowledgePointId}`">
              <td>
                <div class="coverage-point-cell">
                  <strong>{{ item.knowledgePointName }}</strong>
                  <span class="coverage-point-meta">{{ item.courseName }} · {{ item.chapterName || '未分章节' }} · {{ item.graphEntity }}</span>
                </div>
              </td>
              <td>
                <span :class="item.flashcardReady ? 'coverage-ok-text' : 'coverage-warn-text'">
                  {{ item.flashcardReady ? `就绪（复习 ${item.flashcardReviewCount} 次）` : '缺小节/摘要' }}
                </span>
              </td>
              <td class="coverage-num-cell">{{ item.questionCount }}</td>
              <td class="coverage-num-cell">{{ item.diagnosisCount }}</td>
              <td class="coverage-num-cell">
                <span>{{ item.publishedMicroLessonCount }}</span>
                <small v-if="item.draftMicroLessonCount > 0" class="coverage-draft-hint">（草稿 {{ item.draftMicroLessonCount }}）</small>
              </td>
              <td>
                <div v-if="item.gaps.length" class="coverage-gap-list">
                  <div v-for="gap in item.gaps" :key="gap.code" class="coverage-gap-item">
                    <span class="coverage-gap-tag">{{ gap.label }}</span>
                    <button
                      type="button"
                      class="coverage-fix-btn"
                      :data-testid="`coverage-fix-${item.knowledgePointId}-${gap.code}`"
                      @click="jumpToFix(item, gap)"
                    >
                      <span>{{ gap.actionLabel }}</span>
                      <ArrowRight :size="12" />
                    </button>
                  </div>
                </div>
                <span v-else class="coverage-ok-badge">
                  <CheckCircle2 :size="13" aria-hidden="true" />
                  <span>四维资源已覆盖</span>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state" data-testid="coverage-empty-state">
        <ClipboardCheck :size="24" />
        <strong>{{ items.length ? '当前筛选条件下没有匹配知识点' : '当前课程暂无可统计知识点' }}</strong>
        <span>{{ items.length ? '切换上方缺口标签或清空关键词以查看完整清单。' : '请先在「课程与知识点」中维护章节与知识点后重新体检。' }}</span>
        <button v-if="gapFilter !== 'ALL' || search" type="button" class="text-button" @click="gapFilter = 'ALL'; search = ''">重置筛选条件</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.coverage-audit-page {
  min-width: 0;
}

.admin-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.coverage-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.coverage-stat-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.coverage-stat-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.coverage-stat-value {
  color: var(--text);
  font-size: 22px;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.coverage-stat-accent {
  color: var(--accent-strong);
}

.coverage-stat-sub {
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.coverage-table-section {
  display: grid;
  gap: 12px;
}

.coverage-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 14px;
}

.coverage-filter-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.coverage-filter-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 4px 10px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.coverage-filter-pill.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--accent);
}

.coverage-pill-count {
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: inherit;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.coverage-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.coverage-course-filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12.5px;
  font-weight: 600;
}

.coverage-course-filter select {
  min-height: 36px;
  padding: 4px 10px;
  color: var(--text);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.coverage-rule-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 8px 12px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.coverage-table-wrap {
  width: 100%;
  overflow-x: auto;
}

.coverage-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.coverage-table th,
.coverage-table td {
  padding: 11px 10px;
  border-bottom: 1px solid var(--border-soft);
  text-align: left;
  vertical-align: top;
}

.coverage-table th {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.coverage-point-cell {
  display: grid;
  gap: 2px;
  min-width: 160px;
}

.coverage-point-cell strong {
  color: var(--text);
  font-size: 13.5px;
}

.coverage-point-meta {
  color: var(--text-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.coverage-num-cell {
  white-space: nowrap;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.coverage-draft-hint {
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 400;
}

.coverage-ok-text {
  color: var(--success);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.coverage-warn-text {
  color: var(--warning-strong);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.coverage-gap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.coverage-gap-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px 2px 8px;
  background: var(--warning-soft);
  border-radius: var(--radius-sm);
}

.coverage-gap-tag {
  color: var(--warning-strong);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.coverage-fix-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  color: var(--accent-strong);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.coverage-fix-btn:hover {
  border-color: var(--border-focus);
  background: var(--accent-soft);
}

.coverage-ok-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  color: var(--success);
  background: var(--success-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .coverage-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .coverage-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
