<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{ courses: any[]; selectedCourse: any; courseId: number | null; courseLoading: boolean; saving: boolean; points: any[]; questions: any[]; filteredQuestions: any[]; pointNames: Map<number, string>; types: Record<string, string>; search: string; editQuestionId: number | null; questionDraft: any }>()
const emit = defineEmits<{ selectCourse: [value: number]; updateSearch: [value: string]; editQuestion: [question: any]; toggleQuestion: [question: any]; resetQuestion: []; changeType: []; toggleAnswer: [key: string]; removeOption: [key: string]; addOption: []; saveQuestion: [] }>()

const typeFilter = ref<'ALL' | 'SINGLE' | 'MULTIPLE' | 'TRUE_FALSE'>('ALL')
const pointFilter = ref<number | 'ALL'>('ALL')
const statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')
const currentPage = ref(1)
const pageSize = 10

const refinedQuestions = computed(() =>
  props.filteredQuestions.filter((q) => {
    if (typeFilter.value !== 'ALL' && q.type !== typeFilter.value) return false
    if (pointFilter.value !== 'ALL' && q.knowledgePointId !== pointFilter.value) return false
    if (statusFilter.value === 'ENABLED' && !q.enabled) return false
    if (statusFilter.value === 'DISABLED' && q.enabled) return false
    return true
  }),
)

const totalPages = computed(() => Math.max(1, Math.ceil(refinedQuestions.value.length / pageSize)))
const pagedQuestions = computed(() => {
  const safePage = Math.min(currentPage.value, totalPages.value)
  const start = (safePage - 1) * pageSize
  return refinedQuestions.value.slice(start, start + pageSize)
})

watch([() => props.courseId, () => props.search, typeFilter, pointFilter, statusFilter], () => {
  currentPage.value = 1
})
watch(() => props.courseId, () => {
  pointFilter.value = 'ALL'
})
</script>

<template>
  <section class="question-maintenance-panel"><div class="panel-heading"><div><p class="eyebrow">02 / 客观题</p><h2>题库维护</h2></div><label class="course-filter">所属课程<select :value="courseId" aria-label="题库课程" :disabled="saving || courseLoading" @change="emit('selectCourse', Number(($event.target as HTMLSelectElement).value))"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}{{ course.enabled ? '' : '（已停用）' }}</option></select></label></div>
    <div v-if="!selectedCourse" class="admin-empty">请先创建课程。</div><template v-else><p v-if="!selectedCourse.enabled" class="admin-message error">该课程已停用，请先启用课程再新增、编辑或启用题目。</p><div v-if="courseLoading" class="admin-empty" role="status">正在加载课程题库…</div><div v-else class="admin-columns"><div><label class="search-label">搜索题干<input :value="search" aria-label="搜索题干" placeholder="输入题干关键词" @input="emit('updateSearch', ($event.target as HTMLInputElement).value)" /></label>
      <div class="question-filter-bar" aria-label="题目多维筛选">
        <select v-model="typeFilter" aria-label="按题型筛选"><option value="ALL">全部题型</option><option value="SINGLE">单选题</option><option value="MULTIPLE">多选题</option><option value="TRUE_FALSE">判断题</option></select>
        <select v-model="pointFilter" aria-label="按知识点筛选"><option value="ALL">全部知识点</option><option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option></select>
        <select v-model="statusFilter" aria-label="按状态筛选"><option value="ALL">全部状态</option><option value="ENABLED">仅已启用</option><option value="DISABLED">仅已停用</option></select>
      </div>
      <p class="form-hint">共 {{ questions.length }} 题 · 筛选命中 {{ refinedQuestions.length }} 题 · {{ points.filter(p => p.enabled).length }} 个可用知识点</p><div v-if="!pagedQuestions.length" class="admin-empty">{{ search || typeFilter !== 'ALL' || pointFilter !== 'ALL' || statusFilter !== 'ALL' ? '未找到匹配题目。' : '当前课程暂无题目。' }}</div><article v-for="question in pagedQuestions" :key="question.id" class="question-row"><div class="question-meta"><span>{{ types[question.type] || question.type }}</span><span>难度 {{ question.difficulty }}</span><span>{{ pointNames.get(question.knowledgePointId) || '未映射知识点' }}</span><span class="status-label" :class="{ disabled: !question.enabled }">{{ question.enabled ? '启用' : '停用' }}</span></div><h3>{{ question.stem }}</h3><p>答案：{{ question.answer.join('、') }}</p><div class="row-actions"><button class="text-button" :disabled="saving || !selectedCourse.enabled" @click="emit('editQuestion', question)">编辑题目</button><button class="text-button" :disabled="saving || (!question.enabled && !selectedCourse.enabled)" @click="emit('toggleQuestion', question)">{{ question.enabled ? '停用' : '启用' }}</button></div></article>
      <div v-if="totalPages > 1" class="question-pagination" data-testid="question-pagination">
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button type="button" class="button button-outline button-small" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
        <button type="button" class="button button-outline button-small" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
      </div></div>
      <form class="admin-form question-editor" @submit.prevent="emit('saveQuestion')"><div class="panel-heading"><h3>{{ editQuestionId ? '编辑题目 #' + editQuestionId : '新增题目' }}</h3><button type="button" class="text-button" :disabled="saving" @click="emit('resetQuestion')">清空 / 新增</button></div><fieldset :disabled="saving || !selectedCourse.enabled"><label>知识点<select v-model="questionDraft.knowledgePointId" aria-label="题目知识点" required><option :value="null" disabled>选择本课程知识点</option><option v-for="point in points" :key="point.id" :value="point.id" :disabled="!point.enabled">{{ point.name }}{{ point.enabled ? '' : '（已停用）' }}</option></select></label><p v-if="!points.some(p => p.enabled)" class="form-hint">此课程尚无可用知识点，暂不能发布题目。</p><div class="form-pair"><label>题型<select v-model="questionDraft.type" aria-label="题型" @change="emit('changeType')"><option value="SINGLE">单选题</option><option value="MULTIPLE">多选题</option><option value="TRUE_FALSE">判断题</option></select></label><label>难度<select v-model.number="questionDraft.difficulty" aria-label="难度"><option v-for="level in 5" :key="level" :value="level">{{ level }}</option></select></label></div><label>题干<textarea v-model.trim="questionDraft.stem" aria-label="题干" required maxlength="10000" rows="3" placeholder="清晰、独立且可判定的题目"></textarea></label><div class="option-heading"><strong>选项与正确答案</strong><span>{{ questionDraft.type === 'MULTIPLE' ? '至少选择两个正确项' : '选择一个正确项' }}</span></div><div v-for="option in questionDraft.options" :key="option.key" class="option-row"><input :type="questionDraft.type === 'MULTIPLE' ? 'checkbox' : 'radio'" name="correct-answer" :checked="questionDraft.answer.includes(option.key)" :aria-label="'正确答案 ' + option.key" @change="emit('toggleAnswer', option.key)" /><label :for="'option-' + option.key">{{ option.key }}</label><input :id="'option-' + option.key" v-model="option.text" :aria-label="'选项 ' + option.key" required maxlength="2000" :readonly="questionDraft.type === 'TRUE_FALSE'" /><button v-if="questionDraft.type !== 'TRUE_FALSE'" type="button" class="remove-option" :aria-label="'删除选项 ' + option.key" :disabled="questionDraft.options.length <= 2" @click="emit('removeOption', option.key)">×</button></div><button v-if="questionDraft.type !== 'TRUE_FALSE'" type="button" class="text-button" :disabled="questionDraft.options.length >= 8" @click="emit('addOption')">＋ 添加选项</button><label>答案解析<textarea v-model.trim="questionDraft.explanation" aria-label="答案解析" required maxlength="10000" rows="4" placeholder="说明正确依据与易错点"></textarea></label><button type="submit" class="button button-dark" :disabled="!points.some(p => p.enabled)">{{ saving ? '正在保存…' : '保存题目' }}</button></fieldset></form></div></template>
  </section>
</template>

<style scoped>
.question-maintenance-panel{min-width:0;padding:0;background:transparent;border:0;box-shadow:none}.question-maintenance-panel .admin-columns{display:grid;grid-template-columns:minmax(0,1fr) minmax(340px,1.05fr);gap:28px;align-items:start}.question-maintenance-panel .admin-columns>div:first-child{padding:20px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}.question-maintenance-panel .question-editor{min-width:0}
.question-filter-bar{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:10px}
.question-filter-bar select{width:100%;font-size:12px}
.question-pagination{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:14px;padding-top:12px;border-top:1px solid var(--border-soft);font-size:12px;color:var(--text-muted)}
@media(max-width:900px){.question-maintenance-panel .admin-columns{grid-template-columns:1fr}.question-filter-bar{grid-template-columns:1fr}}
</style>
