<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PrerequisiteQualityReport } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import AdminCoursePanel from '../components/AdminCoursePanel.vue'
import AdminQuestionPanel from '../components/AdminQuestionPanel.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { useFormBaseline, useUnsavedChanges } from '../composables/useUnsavedChanges'

interface Course { id: number; code: string; name: string; term: string; description: string; graphSpaceId: number; enabled: boolean }
interface Point { id: number; courseId: number; name: string; graphEntity: string; enabled: boolean }
interface Option { key: string; text: string }
type QuestionType = 'SINGLE' | 'MULTIPLE' | 'TRUE_FALSE'
interface Question { id: number; courseId: number; knowledgePointId: number; type: QuestionType; stem: string; options: Option[]; answer: string[]; explanation: string; difficulty: number; enabled: boolean; source: string }
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })
const loading = ref(true)
const courseLoading = ref(false)
const saving = ref(false)
const authorized = ref(false)
const forbidden = ref(false)
const error = ref('')
const notice = ref('')
const courses = ref<Course[]>([])
const points = ref<Point[]>([])
const questions = ref<Question[]>([])
const prerequisiteQuality = ref<PrerequisiteQualityReport | null>(null)
const qualityLoading = ref(false)
const qualityError = ref('')
const courseId = ref<number | null>(null)
const editCourseId = ref<number | null>(null)
const editQuestionId = ref<number | null>(null)
const search = ref('')
const courseDraft = reactive({ code: '', name: '', term: '', description: '', graphSpaceId: 1 })
const questionDraft = reactive({ knowledgePointId: null as number | null, type: 'SINGLE' as QuestionType, stem: '', options: [{ key: 'A', text: '' }, { key: 'B', text: '' }, { key: 'C', text: '' }, { key: 'D', text: '' }] as Option[], answer: [] as string[], explanation: '', difficulty: 2 })

const { isDirty: isCourseDirty, resetBaseline: resetCourseBaseline } = useFormBaseline(() => ({
  code: courseDraft.code,
  name: courseDraft.name,
  term: courseDraft.term,
  description: courseDraft.description,
  graphSpaceId: courseDraft.graphSpaceId,
}))

const { isDirty: isQuestionDirty, resetBaseline: resetQuestionBaseline } = useFormBaseline(() => ({
  knowledgePointId: questionDraft.knowledgePointId,
  type: questionDraft.type,
  stem: questionDraft.stem,
  options: questionDraft.options,
  answer: questionDraft.answer,
  explanation: questionDraft.explanation,
  difficulty: questionDraft.difficulty,
}))

const isDirty = computed(() => isCourseDirty() || isQuestionDirty())
const { confirmIfDirty } = useUnsavedChanges(isDirty)

const selectedCourse = computed(() => courses.value.find(c => c.id === courseId.value))
const filteredQuestions = computed(() => questions.value.filter(q => q.stem.toLowerCase().includes(search.value.trim().toLowerCase())))
const pointNames = computed(() => new Map(points.value.map(p => [p.id, p.name])))
const types: Record<QuestionType, string> = { SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题' }
function failure(cause: unknown) { const e = cause as { response?: { status?: number; data?: { message?: string; detail?: string } } }; return e.response?.data?.message || e.response?.data?.detail || (e.response?.status === 403 ? '当前账号没有管理权限。' : '操作未完成，请检查输入并重试。') }
function newCourse() { editCourseId.value = null; Object.assign(courseDraft, { code: '', name: '', term: '', description: '', graphSpaceId: 1 }); resetCourseBaseline() }
function editCourse(course: Course) { editCourseId.value = course.id; Object.assign(courseDraft, { code: course.code, name: course.name, term: course.term, description: course.description, graphSpaceId: 1 }); resetCourseBaseline(); notice.value = '' }
function resetQuestion() { editQuestionId.value = null; Object.assign(questionDraft, { knowledgePointId: null, type: 'SINGLE', stem: '', options: ['A', 'B', 'C', 'D'].map(key => ({ key, text: '' })), answer: [], explanation: '', difficulty: 2 }); resetQuestionBaseline() }
function changeType() { questionDraft.answer = []; questionDraft.options = questionDraft.type === 'TRUE_FALSE' ? [{ key: 'TRUE', text: '正确' }, { key: 'FALSE', text: '错误' }] : ['A', 'B', 'C', 'D'].map(key => ({ key, text: '' })) }
function editQuestion(question: Question) { editQuestionId.value = question.id; Object.assign(questionDraft, { knowledgePointId: question.knowledgePointId, type: question.type, stem: question.stem, options: question.options.map(o => ({ ...o })), answer: [...question.answer], explanation: question.explanation, difficulty: question.difficulty }); resetQuestionBaseline(); notice.value = '' }
function toggleAnswer(key: string) { questionDraft.answer = questionDraft.type === 'MULTIPLE' ? questionDraft.answer.includes(key) ? questionDraft.answer.filter(k => k !== key) : [...questionDraft.answer, key] : [key] }
function removeOption(key: string) { questionDraft.options = questionDraft.options.filter(o => o.key !== key); questionDraft.answer = questionDraft.answer.filter(k => k !== key) }
async function selectCourse(value: number) {
  if (value === courseId.value) return
  if (isDirty.value) {
    const ok = await confirmIfDirty()
    if (!ok) return
  }
  courseId.value = value; void loadCourse()
}
function updateSearch(value: string) { search.value = value }
function addOption() { const key = 'ABCDEFGH'.split('').find(k => !questionDraft.options.some(o => o.key === k)); if (key) questionDraft.options.push({ key, text: '' }) }
let listVersion = 0
async function loadCourse() {
  const version = ++listVersion
  points.value = []; questions.value = []; prerequisiteQuality.value = null; qualityError.value = ''; resetQuestion(); error.value = ''
  if (!courseId.value) return
  courseLoading.value = true
  try {
    qualityLoading.value = true
    const [pointResponse, questionResponse] = await Promise.all([
      api.get<Point[]>('/admin/knowledge-points', { params: { courseId: courseId.value } }),
      api.get<Question[]>('/admin/questions', { params: { courseId: courseId.value } }),
    ])
    if (version !== listVersion) return
    points.value = pointResponse.data; questions.value = questionResponse.data
    try {
      prerequisiteQuality.value = (await api.get<PrerequisiteQualityReport>('/admin/courses/' + courseId.value + '/prerequisite-quality')).data
      qualityError.value = ''
    } catch {
      prerequisiteQuality.value = null
      qualityError.value = '先修关系检查暂时不可用，请重试。'
    }
  } catch (cause) { if (version === listVersion) error.value = failure(cause) }
  finally { if (version === listVersion) { courseLoading.value = false; qualityLoading.value = false } }
}
async function initialize() {
  loading.value = true; error.value = ''; forbidden.value = false
  try {
    const me = (await api.get<{ role: string }>('/auth/me')).data
    if (me.role !== 'ADMIN') { forbidden.value = true; authorized.value = false; return }
    authorized.value = true
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    if (!courses.value.some(c => c.id === courseId.value)) courseId.value = courses.value[0]?.id ?? null
    await loadCourse()
  } catch (cause) { error.value = failure(cause) }
  finally { loading.value = false }
}
async function saveCourse() {
  if (!courseDraft.code.trim() || !courseDraft.name.trim() || !courseDraft.term.trim() || !courseDraft.description.trim()) {
    error.value = '课程代码、名称、学期和简介均不能为空。'
    return
  }
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const payload = { ...courseDraft, graphSpaceId: 1 }
    const response = editCourseId.value ? await api.put<Course>('/admin/courses/' + editCourseId.value, payload) : await api.post<Course>('/admin/courses', payload)
    courses.value = (await api.get<Course[]>('/admin/courses')).data
    courseId.value = response.data.id; editCourse(response.data); await loadCourse(); notice.value = '课程已保存。'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
interface ConfirmActionState {
  title: string
  message: string
  confirmLabel: string
  tone: 'danger' | 'warning' | 'default'
  run: () => Promise<void>
}
const confirmState = ref<ConfirmActionState | null>(null)
const confirmBusy = ref(false)

async function executeConfirm() {
  if (!confirmState.value || confirmBusy.value) return
  const task = confirmState.value
  confirmBusy.value = true
  try {
    await task.run()
    confirmState.value = null
  } finally {
    confirmBusy.value = false
  }
}

async function toggleCourse(course: Course) {
  confirmState.value = {
    title: course.enabled ? '停用课程' : '启用课程',
    message: (course.enabled ? '停用' : '启用') + '课程“' + course.name + '”？' + (course.enabled ? '停用后学生将无法打开该课程。' : ''),
    confirmLabel: course.enabled ? '确认停用' : '确认启用',
    tone: course.enabled ? 'danger' : 'default',
    run: async () => {
      saving.value = true; error.value = ''; notice.value = ''
      try {
        const response = await api.put<Course>('/admin/courses/' + course.id + '/enabled', { enabled: !course.enabled })
        Object.assign(course, response.data)
        notice.value = course.enabled ? '课程已启用。' : '课程已停用，学生将无法打开该课程。'
      } catch (cause) { error.value = failure(cause) }
      finally { saving.value = false }
    },
  }
}
function validateQuestion() {
  if (!questionDraft.knowledgePointId || !points.value.some(p => p.id === questionDraft.knowledgePointId && p.enabled)) return '请选择本课程的已启用知识点。'
  if (!questionDraft.answer.length || (questionDraft.type === 'MULTIPLE' ? questionDraft.answer.length < 2 : questionDraft.answer.length !== 1)) return questionDraft.type === 'MULTIPLE' ? '多选题至少选择两个正确答案。' : '请选择一个正确答案。'
  if (questionDraft.options.some(o => !o.text.trim())) return '每个选项都需要填写文本。'
  return ''
}
async function saveQuestion() {
  error.value = validateQuestion(); notice.value = ''
  if (error.value || !courseId.value) return
  saving.value = true
  try {
    const payload = { ...questionDraft, courseId: courseId.value }
    if (editQuestionId.value) await api.put('/admin/questions/' + editQuestionId.value, payload)
    else await api.post('/admin/questions', payload)
    resetQuestionBaseline()
    await loadCourse(); notice.value = '题目已保存。正确答案与解析仅在管理和答题反馈中提供。'
  } catch (cause) { error.value = failure(cause) }
  finally { saving.value = false }
}
async function toggleQuestion(question: Question) {
  confirmState.value = {
    title: question.enabled ? '停用题目' : '启用题目',
    message: (question.enabled ? '停用' : '启用') + '这道题？' + (question.enabled ? '停用后学生将无法继续作答。' : ''),
    confirmLabel: question.enabled ? '确认停用' : '确认启用',
    tone: question.enabled ? 'danger' : 'default',
    run: async () => {
      saving.value = true; error.value = ''; notice.value = ''
      try {
        const response = await api.put<Question>('/admin/questions/' + question.id + '/enabled', { enabled: !question.enabled })
        Object.assign(question, response.data)
        notice.value = question.enabled ? '题目已启用。' : '题目已停用，历史答题记录仍保留。'
      } catch (cause) { error.value = failure(cause) }
      finally { saving.value = false }
    },
  }
}
onMounted(initialize)
</script>

<template>
  <div class="page-stack admin-page">
    <SectionHeading :embedded="props.embedded" eyebrow="教学管理" title="课程与题库管理" description="维护课程信息、知识点映射与客观题。所有更改均由管理员权限校验。"><span class="admin-badge">ADMIN</span></SectionHeading>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在校验管理员权限" loading-text="正在读取课程、知识点和题库配置，请稍候。" />
    <div v-else-if="forbidden" class="admin-empty" role="alert"><strong>需要管理员权限</strong><p>请使用本地环境配置的管理员账号登录。</p><RouterLink to="/courses">返回课程学习</RouterLink></div>
    <div v-if="error" class="admin-message error" role="alert">{{ error }} <button v-if="!authorized" class="text-button" @click="initialize">重试</button></div>
    <div v-if="notice" class="admin-message success" role="status">{{ notice }}</div>
    <template v-if="authorized && !loading">
      <AdminCoursePanel :courses="courses" :course-draft="courseDraft" :edit-course-id="editCourseId" :saving="saving" :prerequisite-quality="prerequisiteQuality" :quality-loading="qualityLoading" :quality-error="qualityError" @new-course="newCourse" @edit-course="editCourse" @toggle-course="toggleCourse" @save-course="saveCourse" @retry-quality="loadCourse" />
      <AdminQuestionPanel :courses="courses" :selected-course="selectedCourse" :course-id="courseId" :course-loading="courseLoading" :saving="saving" :points="points" :questions="questions" :filtered-questions="filteredQuestions" :point-names="pointNames" :types="types" :search="search" :edit-question-id="editQuestionId" :question-draft="questionDraft" @select-course="selectCourse" @update-search="updateSearch" @edit-question="editQuestion" @toggle-question="toggleQuestion" @reset-question="resetQuestion" @change-type="changeType" @toggle-answer="toggleAnswer" @remove-option="removeOption" @add-option="addOption" @save-question="saveQuestion" />
    </template>
    <ConfirmDialog
      :open="Boolean(confirmState)"
      :title="confirmState?.title || ''"
      :message="confirmState?.message || ''"
      :confirm-label="confirmState?.confirmLabel || '确认'"
      :tone="confirmState?.tone || 'danger'"
      :busy="confirmBusy"
      @cancel="confirmState = null"
      @confirm="executeConfirm"
    />
  </div>
</template>

<style scoped>
.admin-badge { padding: 6px 10px; color: var(--accent-strong); background: var(--accent-soft); border-radius: var(--radius-sm); font-size: 12px; font-weight: 700; }
</style>
