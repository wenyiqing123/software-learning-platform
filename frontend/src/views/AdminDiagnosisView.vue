<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CircleAlert, Plus, Save, Trash2 } from '@lucide/vue'
import { api, apiError, type ConfiguredQuizDiagnosis } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'

interface Course { id: number; name: string; term: string }
interface Point { id: number; name: string; enabled: boolean }
interface Option { key: string; text: string }
interface Question { id: number; courseId: number; stem: string; options: Option[]; answer: string[]; enabled: boolean }
interface Draft { optionKey: string; knowledgePointId: number | null; rationale: string }

const loading = ref(true)
const saving = ref(false)
const authorized = ref(false)
const forbidden = ref(false)
const error = ref('')
const notice = ref('')
const courses = ref<Course[]>([])
const points = ref<Point[]>([])
const questions = ref<Question[]>([])
const configurations = ref<ConfiguredQuizDiagnosis[]>([])
const courseId = ref<number | null>(null)
const questionId = ref<number | null>(null)
const drafts = ref<Draft[]>([])

const selectedQuestion = computed(() => questions.value.find(question => question.id === questionId.value))
const distractors = computed(() => selectedQuestion.value?.options.filter(option => !selectedQuestion.value?.answer.includes(option.key)) ?? [])
const availablePoints = computed(() => points.value.filter(point => point.enabled))

function failure(cause: unknown) { return apiError(cause, '错因关联配置暂时不可用，请稍后重试。') }
function clearEditor() { configurations.value = []; drafts.value = []; questionId.value = null }

async function loadQuestionData() {
  if (!courseId.value) { points.value = []; questions.value = []; clearEditor(); return }
  clearEditor(); error.value = ''
  try {
    const [pointResponse, questionResponse] = await Promise.all([
      api.get<Point[]>('/admin/knowledge-points', { params: { courseId: courseId.value } }),
      api.get<Question[]>('/admin/questions', { params: { courseId: courseId.value } }),
    ])
    points.value = pointResponse.data
    questions.value = questionResponse.data.filter(question => question.enabled)
    questionId.value = questions.value[0]?.id ?? null
  } catch (cause) { error.value = failure(cause) }
}

async function loadConfiguration() {
  if (!questionId.value) { configurations.value = []; drafts.value = []; return }
  try {
    configurations.value = (await api.get<ConfiguredQuizDiagnosis[]>('/admin/questions/' + questionId.value + '/diagnostics')).data
    drafts.value = configurations.value.map(item => ({ optionKey: item.optionKey, knowledgePointId: item.knowledgePointId, rationale: item.rationale }))
  } catch (cause) { error.value = failure(cause) }
}

function addDraft() {
  const key = distractors.value.find(option => !drafts.value.some(draft => draft.optionKey === option.key))?.key || ''
  drafts.value.push({ optionKey: key, knowledgePointId: availablePoints.value[0]?.id ?? null, rationale: '' })
}
function removeDraft(index: number) { drafts.value.splice(index, 1) }
async function save() {
  if (!questionId.value) return
  if (drafts.value.some(draft => !draft.optionKey || !draft.knowledgePointId || !draft.rationale.trim())) { error.value = '每条错因关联都需要选择干扰项、混淆知识点并填写匹配依据。'; return }
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const response = await api.put<ConfiguredQuizDiagnosis[]>('/admin/questions/' + questionId.value + '/diagnostics', { entries: drafts.value.map(draft => ({ optionKey: draft.optionKey, knowledgePointId: draft.knowledgePointId, rationale: draft.rationale.trim() })) })
    configurations.value = response.data
    notice.value = '错因关联已保存；学生端只会看到这些课程配置的匹配。'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}

async function initialize() {
  loading.value = true; error.value = ''; forbidden.value = false
  try {
    const me = (await api.get<{ role: string }>('/auth/me')).data
    if (me.role !== 'ADMIN') { forbidden.value = true; return }
    authorized.value = true
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    courseId.value = courses.value[0]?.id ?? null
    await loadQuestionData()
    await loadConfiguration()
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}

watch(courseId, () => { if (authorized.value && !loading.value) void loadQuestionData() })
watch(questionId, () => { if (authorized.value && !loading.value) void loadConfiguration() })
onMounted(initialize)
</script>

<template>
  <div class="page-stack diagnosis-admin-page">
    <SectionHeading eyebrow="教学管理" title="错因关联配置" description="为课程干扰项配置混淆知识点和补救依据；未配置项不会被系统确定性解释。">
      <span class="admin-badge">ADMIN</span>
    </SectionHeading>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载错因配置" loading-text="正在读取课程题目和已配置的混淆关系，请稍候。" />
    <div v-else-if="forbidden" class="admin-empty" role="alert"><strong>需要管理员权限</strong><span>请使用管理员账号登录。</span></div>
    <template v-else-if="authorized">
      <div v-if="error" class="admin-message error" role="alert"><CircleAlert :size="16" />{{ error }}</div>
      <div v-if="notice" class="admin-message success" role="status">{{ notice }}</div>
      <section class="diagnosis-admin-panel">
        <div class="diagnosis-filters"><label>课程<select v-model="courseId" aria-label="错因课程"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><label>题目<select v-model="questionId" aria-label="错因题目"><option v-for="question in questions" :key="question.id" :value="question.id">#{{ question.id }} · {{ question.stem }}</option></select></label></div>
        <div v-if="!selectedQuestion" class="admin-empty">当前课程没有可配置的启用题目。</div>
        <template v-else>
          <div class="diagnosis-question"><p class="eyebrow">{{ selectedQuestion.options.length - selectedQuestion.answer.length }} 个可配置干扰项</p><h2>{{ selectedQuestion.stem }}</h2><p>正确答案：{{ selectedQuestion.answer.join('、') }}。不要把正确答案配置为错因。</p></div>
          <div class="diagnosis-draft-list">
            <article v-for="(draft, index) in drafts" :key="index" class="diagnosis-draft">
              <div class="diagnosis-draft-heading"><strong>错因 {{ index + 1 }}</strong><button class="icon-button" :aria-label="'删除错因关联 ' + (index + 1)" :disabled="saving" @click="removeDraft(index)"><Trash2 :size="15" /></button></div>
              <label>干扰项<select v-model="draft.optionKey" :aria-label="'错因选项 ' + (index + 1)"><option v-for="option in distractors" :key="option.key" :value="option.key">{{ option.key }} · {{ option.text }}</option></select></label>
              <label>混淆知识点<select v-model.number="draft.knowledgePointId" :aria-label="'混淆知识点 ' + (index + 1)"><option :value="null" disabled>请选择知识点</option><option v-for="point in availablePoints" :key="point.id" :value="point.id">{{ point.name }}</option></select></label>
              <label>错因匹配依据<textarea v-model.trim="draft.rationale" :aria-label="'错因匹配依据 ' + (index + 1)" maxlength="500" rows="3" placeholder="说明该干扰项对应的混淆概念以及判断依据"></textarea></label>
            </article>
          </div>
          <div class="diagnosis-admin-actions"><button class="button button-outline" aria-label="添加错因关联" :disabled="saving || drafts.length >= distractors.length" @click="addDraft"><Plus :size="15" />添加关联</button><button class="button button-dark" aria-label="保存错因关联" :disabled="saving" @click="save"><Save :size="15" />{{ saving ? '保存中…' : '保存配置' }}</button></div>
          <p v-if="!drafts.length" class="form-hint">当前没有配置。未配置的干扰项不会在学生端被解释为确定性错因。</p>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin-badge { padding: 6px 10px; color: var(--green); background: var(--green-pale); border-radius: var(--radius-sm); font-size: 11px; font-weight: 700; }
.diagnosis-admin-panel { padding: 24px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); }
.diagnosis-filters { display: grid; grid-template-columns: minmax(220px, .8fr) minmax(320px, 1.2fr); gap: 14px; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.diagnosis-filters label,.diagnosis-draft label { display: grid; gap: 7px; color: var(--text-muted); font-size: 12px; }
.diagnosis-filters select,.diagnosis-draft select,.diagnosis-draft textarea { width: 100%; padding: 10px 11px; color: var(--ink); background: var(--surface); border: 1px solid var(--border-default); border-radius: var(--radius-sm); font: inherit; line-height: 1.6; }
.diagnosis-question { padding: 20px 0 10px; }
.diagnosis-question .eyebrow { margin: 0 0 6px; color: var(--green); }
.diagnosis-question h2 { margin: 0 0 7px; color: var(--ink); font-size: 16px; line-height: 1.6; }
.diagnosis-question p:last-child { margin: 0; color: var(--muted); font-size: 11px; }
.diagnosis-draft-list { display: grid; gap: 12px; }
.diagnosis-draft { display: grid; gap: 12px; padding: 16px; background: var(--surface-soft); border: 1px solid var(--line); border-radius: var(--radius-md); }
.diagnosis-draft-heading { display: flex; justify-content: space-between; align-items: center; color: var(--ink); font-size: 12px; }
.diagnosis-draft textarea { resize: vertical; }
.diagnosis-admin-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
.admin-message { display: flex; align-items: center; gap: 7px; padding: 14px 18px; border-radius: var(--radius-sm); font-size: 12px; line-height: 1.7; }
.admin-message.error { color: var(--danger); background: var(--danger-soft); }
.admin-message.success { color: var(--green); background: var(--green-pale); }
.admin-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 15px; color: var(--muted); text-align: center; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); font-size: 13px; }
.form-hint { margin: 14px 0 0; color: var(--muted); font-size: 11px; line-height: 1.8; }
@media(max-width:700px){.diagnosis-admin-panel{padding:16px}.diagnosis-filters{grid-template-columns:1fr}}
</style>
