<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, apiError, type AdminUserPage, type AdminUserSummary, type ExperimentGroup, type ExperimentTask } from '../api/client'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import FeedbackState from '../components/FeedbackState.vue'
import { useFormBaseline, useUnsavedChanges } from '../composables/useUnsavedChanges'

const props = withDefaults(defineProps<{
  embedded?: boolean
}>(), {
  embedded: false
})

const tasks = ref<ExperimentTask[]>([])
const groupsMap = ref<Record<number, ExperimentGroup[]>>({})
const title = ref('')
const description = ref('')
const question = ref('')
const referenceAnswer = ref('')
const error = ref('')
const notice = ref('')
const working = ref(false)
const loading = ref(true)

const taskDraft = computed(() => ({
  title: title.value,
  question: question.value,
  description: description.value,
  referenceAnswer: referenceAnswer.value,
}))
const { isDirty, resetBaseline } = useFormBaseline(taskDraft)
useUnsavedChanges(isDirty)

// 每个任务独立的配组与派发输入状态
interface TaskActionState {
  groupName: string
  groupCondition: string
  groupTaskOrder: 'A→B' | 'B→A' | ''
  groupSortOrder: number
  assignGroupId: number | null
  assignStudentIdsText: string
}

const actionState = reactive<Record<number, TaskActionState>>({})
const pickerTaskId = ref<number | null>(null)
const pickerKeyword = ref('')
const pickerLoading = ref(false)
const pickerUsers = ref<AdminUserSummary[]>([])
const pickerSelectedIds = ref<number[]>([])

const filteredPickerUsers = computed(() => {
  const kw = pickerKeyword.value.trim().toLowerCase()
  if (!kw) return pickerUsers.value
  return pickerUsers.value.filter(u => u.username.toLowerCase().includes(kw) || u.displayName.toLowerCase().includes(kw) || String(u.id).includes(kw))
})

async function toggleStudentPicker(taskId: number) {
  if (pickerTaskId.value === taskId) {
    pickerTaskId.value = null
    return
  }
  pickerTaskId.value = taskId
  pickerSelectedIds.value = []
  if (pickerUsers.value.length) return
  pickerLoading.value = true
  try {
    const res = await api.get<AdminUserPage>('/admin/users', { params: { role: 'STUDENT', enabled: true, size: 100 } })
    pickerUsers.value = res.data?.items || []
  } catch (cause) {
    error.value = apiError(cause, '加载学生名单失败，仍可手动输入学生 ID。')
  } finally {
    pickerLoading.value = false
  }
}

function togglePickerUser(id: number) {
  if (pickerSelectedIds.value.includes(id)) {
    pickerSelectedIds.value = pickerSelectedIds.value.filter(item => item !== id)
  } else {
    pickerSelectedIds.value = [...pickerSelectedIds.value, id]
  }
}

function applyPickedStudents(taskId: number) {
  if (!pickerSelectedIds.value.length) return
  const state = getActionState(taskId)
  const existing = state.assignStudentIdsText
    .split(/[\s,，\n]+/)
    .map(s => Number(s.trim()))
    .filter(n => Number.isInteger(n) && n > 0)
  const merged = Array.from(new Set([...existing, ...pickerSelectedIds.value]))
  state.assignStudentIdsText = merged.join(', ')
  pickerTaskId.value = null
  pickerSelectedIds.value = []
}

function getActionState(taskId: number): TaskActionState {
  if (!actionState[taskId]) {
    actionState[taskId] = {
      groupName: '',
      groupCondition: '',
      groupTaskOrder: '',
      groupSortOrder: 1,
      assignGroupId: null,
      assignStudentIdsText: ''
    }
  }
  return actionState[taskId]
}

async function load() {
  loading.value = true
  try {
    const res = await api.get<ExperimentTask[]>('/admin/experiment-tasks')
    tasks.value = res.data
    for (const t of tasks.value) {
      loadGroups(t.id)
    }
  } catch (cause) {
    error.value = apiError(cause, '实验任务暂时无法加载。')
  } finally {
    loading.value = false
  }
}

async function loadGroups(taskId: number) {
  try {
    const res = await api.get<ExperimentGroup[]>('/admin/experiment-tasks/' + taskId + '/groups')
    groupsMap.value[taskId] = res.data
    const state = getActionState(taskId)
    if (res.data.length && !state.assignGroupId) {
      state.assignGroupId = res.data[0].id
    }
  } catch {
    // 容错
  }
}

async function create() {
  if (!title.value.trim() || !question.value.trim()) {
    error.value = '请填写任务名称和指定问题。'
    return
  }
  working.value = true
  error.value = ''
  try {
    await api.post('/admin/experiment-tasks', {
      title: title.value.trim(),
      description: description.value.trim(),
      experimentType: 'METHODS',
      question: question.value.trim(),
      referenceAnswer: referenceAnswer.value.trim(),
      expectedEntities: [],
      includeAblations: false
    })
    notice.value = '实验任务草稿已创建。'
    title.value = ''
    question.value = ''
    description.value = ''
    referenceAnswer.value = ''
    resetBaseline()
    await load()
  } catch (cause) {
    error.value = apiError(cause, '实验任务创建失败。')
  } finally {
    working.value = false
  }
}

async function addGroup(task: ExperimentTask) {
  const state = getActionState(task.id)
  error.value = ''
  notice.value = ''
  if (!state.groupName.trim() || !state.groupCondition.trim()) {
    error.value = '请填写实验组名称与条件标识。'
    return
  }
  if (!state.groupTaskOrder || (state.groupTaskOrder !== 'A→B' && state.groupTaskOrder !== 'B→A')) {
    error.value = '新建实验组必须指定评估顺序为 A→B 或 B→A。'
    return
  }

  try {
    await api.post('/admin/experiment-tasks/' + task.id + '/groups', {
      name: state.groupName.trim(),
      conditionKey: state.groupCondition.trim(),
      sortOrder: state.groupSortOrder || 1,
      taskOrder: state.groupTaskOrder
    })
    notice.value = `已为任务「${task.title}」创建实验组。`
    state.groupName = ''
    state.groupCondition = ''
    state.groupTaskOrder = ''
    await loadGroups(task.id)
    await load()
  } catch (cause) {
    error.value = apiError(cause, '创建实验组失败。')
  }
}

function parseStudentIds(input: string): { valid: boolean; ids: number[]; error?: string } {
  const tokens = input.split(/[\s,，\n]+/).map(s => s.trim()).filter(Boolean)
  if (!tokens.length) {
    return { valid: false, ids: [], error: '请输入待派发的学生 ID 列表。' }
  }
  const ids: number[] = []
  const seen = new Set<number>()
  for (const token of tokens) {
    const val = Number(token)
    if (!Number.isInteger(val) || val <= 0) {
      return { valid: false, ids: [], error: `学生 ID 必须为正整数: "${token}"` }
    }
    if (seen.has(val)) {
      return { valid: false, ids: [], error: `存在重复的学生 ID: ${val}` }
    }
    seen.add(val)
    ids.push(val)
  }
  if (ids.length > 500) {
    return { valid: false, ids: [], error: '单次派发学生数量不能超过 500 名。' }
  }
  return { valid: true, ids }
}

interface PendingConfirm {
  title: string
  message: string
  confirmLabel: string
  tone: 'danger' | 'warning' | 'default'
  run: () => Promise<void>
}
const pendingConfirm = ref<PendingConfirm | null>(null)
const confirmBusy = ref(false)

async function executeConfirm() {
  if (!pendingConfirm.value || confirmBusy.value) return
  const item = pendingConfirm.value
  confirmBusy.value = true
  try {
    await item.run()
    pendingConfirm.value = null
  } finally {
    confirmBusy.value = false
  }
}

async function assignStudents(task: ExperimentTask) {
  const state = getActionState(task.id)
  error.value = ''
  notice.value = ''
  if (!state.assignGroupId) {
    error.value = '请选择目标实验组。'
    return
  }
  const targetGroup = (groupsMap.value[task.id] || []).find(g => g.id === state.assignGroupId)
  if (!targetGroup) {
    error.value = '所选目标实验组不存在。'
    return
  }

  const parseRes = parseStudentIds(state.assignStudentIdsText)
  if (!parseRes.valid) {
    error.value = parseRes.error || '学生 ID 列表无效。'
    // 保持输入保留原值
    return
  }

  const ids = parseRes.ids
  const confirmMsg = `确认派发实验任务？\n任务: ${task.title}\n实验组: ${targetGroup.name}\n评估顺序: ${targetGroup.taskOrder || '未设置'}\n学生数量: ${ids.length} 名`
  pendingConfirm.value = {
    title: '确认派发实验任务',
    message: confirmMsg,
    confirmLabel: '确认派发',
    tone: 'default',
    run: async () => {
      try {
        await api.post('/admin/experiment-tasks/' + task.id + '/assign', {
          groupId: targetGroup.id,
          userIds: ids
        })
        notice.value = `成功为实验组「${targetGroup.name}」派发 ${ids.length} 名学生。`
        state.assignStudentIdsText = ''
        await loadGroups(task.id)
        await load()
      } catch (cause) {
        // 失败保留原输入
        error.value = apiError(cause, '派发学生失败。')
      }
    }
  }
}

async function setStatus(task: ExperimentTask, status: 'OPEN' | 'CLOSED') {
  pendingConfirm.value = {
    title: status === 'OPEN' ? '开放实验任务' : '关闭实验任务',
    message: status === 'OPEN' ? '开放该实验任务并允许参与者开始？' : '关闭该实验任务并停止新的参与？',
    confirmLabel: status === 'OPEN' ? '确认开放' : '确认关闭',
    tone: status === 'CLOSED' ? 'danger' : 'default',
    run: async () => {
      try {
        await api.put('/admin/experiment-tasks/' + task.id + '/status', { status })
        notice.value = status === 'OPEN' ? '实验任务已开放。' : '实验任务已关闭。'
        await load()
      } catch (cause) {
        error.value = apiError(cause, '任务状态更新失败。')
      }
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack experiment-task-admin-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">EXPERIMENT TASK CONTROL</p>
        <component :is="props.embedded ? 'h2' : 'h1'">实验任务管理</component>
        <p>集中维护指定问题、实验条件和开放状态，参与者完成情况来自真实服务器运行。</p>
      </div>
    </header>
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
    <div v-if="notice" class="inline-notice success" role="status">{{ notice }}</div>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在加载实验任务" loading-text="正在读取任务、实验组和参与者状态。" />
    <template v-else>
      <section class="task-create">
        <label>任务名称<input data-testid="task-title" v-model="title" maxlength="160" /></label>
        <label>指定问题<textarea data-testid="task-question" v-model="question" rows="2" maxlength="2000"></textarea></label>
        <label>任务说明<textarea v-model="description" rows="2" maxlength="1000"></textarea></label>
        <label>参考答案<textarea v-model="referenceAnswer" rows="2" maxlength="4000"></textarea></label>
        <button class="primary-button" aria-label="创建实验任务" :disabled="working" @click="create">创建任务草稿</button>
      </section>

      <section class="task-admin-list">
        <article v-for="task in tasks" :key="task.id" class="task-admin-row">
          <div class="task-info">
            <div class="task-header-row">
              <strong>{{ task.title }}</strong>
              <span class="status-badge">{{ task.status }}</span>
            </div>
            <p class="task-question-text">{{ task.question }}</p>
            <small>{{ task.groupCount }} 个实验组 · {{ task.assignmentCount }} 名参与者</small>

            <!-- 实验组列表展示 -->
            <div class="task-groups-container">
              <h4 class="sub-heading">已配置实验组</h4>
              <div v-if="groupsMap[task.id]?.length" class="task-groups-grid">
                <div v-for="g in groupsMap[task.id]" :key="g.id" class="group-chip">
                  <span class="group-name">{{ g.name }}</span>
                  <span class="group-cond">({{ g.conditionKey }})</span>
                  <span class="group-order-tag">{{ g.taskOrder || '无顺序' }}</span>
                </div>
              </div>
              <div v-else class="empty-hint">暂无实验组，请先为任务配组。</div>
            </div>

            <!-- 配组与派发管理面板 -->
            <div class="task-operations">
              <!-- 配组表单 -->
              <div class="operation-panel group-panel">
                <h4 class="sub-heading">新建实验组</h4>
                <div class="form-row">
                  <input
                    data-testid="group-name"
                    v-model="getActionState(task.id).groupName"
                    placeholder="实验组名称"
                    class="form-input"
                  />
                  <input
                    data-testid="group-condition"
                    v-model="getActionState(task.id).groupCondition"
                    placeholder="条件标识（如 COND_A）"
                    class="form-input"
                  />
                  <select
                    data-testid="group-task-order"
                    v-model="getActionState(task.id).groupTaskOrder"
                    class="form-select"
                  >
                    <option value="">选择评估顺序</option>
                    <option value="A→B">A→B</option>
                    <option value="B→A">B→A</option>
                  </select>
                  <button
                    class="secondary-button"
                    :aria-label="'添加实验组 ' + task.id"
                    @click="addGroup(task)"
                  >
                    添加组
                  </button>
                </div>
                <small class="help-text">评估顺序为预设实验协议步骤，非随机分配。</small>
              </div>

              <!-- 派发学生表单 -->
              <div class="operation-panel assign-panel">
                <h4 class="sub-heading">派发至学生账号</h4>
                <div class="assign-form">
                  <div class="form-row">
                    <select
                      data-testid="assign-group-select"
                      v-model="getActionState(task.id).assignGroupId"
                      class="form-select"
                    >
                      <option :value="null">请选择实验组</option>
                      <option
                        v-for="g in (groupsMap[task.id] || [])"
                        :key="g.id"
                        :value="g.id"
                      >
                        {{ g.name }} ({{ g.taskOrder || '无顺序' }})
                      </option>
                    </select>
                    <button
                      type="button"
                      class="secondary-button"
                      :data-testid="`pick-students-${task.id}`"
                      @click="toggleStudentPicker(task.id)"
                    >
                      {{ pickerTaskId === task.id ? '收起学生名单' : '挑选学生账号' }}
                    </button>
                  </div>
                  <div v-if="pickerTaskId === task.id" class="student-picker-panel" :data-testid="`student-picker-${task.id}`">
                    <div class="student-picker-bar">
                      <input v-model="pickerKeyword" type="search" class="form-input" placeholder="按姓名、用户名或 ID 筛选学生" />
                      <button type="button" class="secondary-button" :disabled="!pickerSelectedIds.length" @click="applyPickedStudents(task.id)">
                        填入已选 ({{ pickerSelectedIds.length }})
                      </button>
                    </div>
                    <p v-if="pickerLoading" class="help-text">正在加载启用中的学生账号...</p>
                    <div v-else-if="filteredPickerUsers.length" class="student-picker-list">
                      <label v-for="stu in filteredPickerUsers" :key="stu.id" class="student-picker-item">
                        <input type="checkbox" :checked="pickerSelectedIds.includes(stu.id)" @change="togglePickerUser(stu.id)" />
                        <span>{{ stu.displayName }} (@{{ stu.username }}) · #{{ stu.id }}</span>
                      </label>
                    </div>
                    <p v-else class="help-text">未找到匹配的启用学生账号。</p>
                  </div>
                  <textarea
                    data-testid="assign-student-ids"
                    v-model="getActionState(task.id).assignStudentIdsText"
                    rows="2"
                    placeholder="输入学生用户 ID，多个以逗号或换行分隔（1–500 个正整数）"
                    class="form-textarea"
                  ></textarea>
                  <button
                    class="secondary-button"
                    :aria-label="'派发学生 ' + task.id"
                    @click="assignStudents(task)"
                  >
                    派发给学生
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="task-actions">
            <button
              v-if="task.status !== 'OPEN'"
              class="action-button open-btn"
              :aria-label="'开放实验任务 ' + task.id"
              @click="setStatus(task, 'OPEN')"
            >
              开放
            </button>
            <button
              v-else
              class="action-button close-btn"
              :aria-label="'关闭实验任务 ' + task.id"
              @click="setStatus(task, 'CLOSED')"
            >
              关闭
            </button>
          </div>
        </article>
        <div v-if="!tasks.length" class="empty-state">还没有实验任务。</div>
      </section>
    </template>
    <ConfirmDialog
      :open="Boolean(pendingConfirm)"
      :title="pendingConfirm?.title || ''"
      :message="pendingConfirm?.message || ''"
      :confirm-label="pendingConfirm?.confirmLabel || '确认'"
      :tone="pendingConfirm?.tone || 'default'"
      :busy="confirmBusy"
      @cancel="pendingConfirm = null"
      @confirm="executeConfirm"
    />
  </div>
</template>

<style scoped>
.task-create,
.task-admin-list {
  display: grid;
  gap: 16px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.task-create label {
  display: grid;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.task-create input,
.task-create textarea {
  padding: 10px;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  color: var(--text);
  font: inherit;
}

.primary-button {
  justify-self: start;
  min-height: 36px;
  border: 0;
  border-radius: 6px;
  padding: 0 16px;
  background: var(--accent);
  color: var(--surface);
  cursor: pointer;
  font-weight: 500;
}

.primary-button:disabled {
  opacity: 0.5;
}

.task-admin-list {
  margin-top: 16px;
}

.task-admin-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--border-soft);
}

.task-info {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.task-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-header-row strong {
  font-size: 15px;
  color: var(--text);
}

.status-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--surface-soft);
  color: var(--text-muted);
  font-weight: 600;
}

.task-question-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text);
}

.task-info small {
  color: var(--muted);
  font-size: 12px;
}

.sub-heading {
  margin: 0 0 6px 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
}

.task-groups-container {
  padding: 10px 12px;
  background: var(--surface-soft);
  border-radius: var(--radius-sm);
}

.task-groups-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.group-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: 4px;
  font-size: 12px;
}

.group-name {
  font-weight: 600;
  color: var(--text);
}

.group-cond {
  color: var(--text-muted);
}

.group-order-tag {
  padding: 1px 6px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
}

.empty-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.task-operations {
  display: grid;
  gap: 12px;
  padding-top: 6px;
}

.operation-panel {
  padding: 12px;
  background: var(--surface-tint);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-sm);
}

.group-panel,
.assign-panel {
  min-width: 0;
}

.assign-form {
  display: grid;
  gap: 8px;
}

.form-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.form-input,
.form-select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 12px;
}

.form-textarea {
  width: 100%;
  margin-top: 6px;
  margin-bottom: 6px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 12px;
  box-sizing: border-box;
}

.secondary-button {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border-default);
  border-radius: 4px;
  background: var(--surface);
  color: var(--text);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.secondary-button:hover {
  background: var(--surface-soft);
}

.help-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.task-actions {
  display: flex;
  align-items: center;
}

.action-button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
}

.open-btn {
  color: var(--accent);
}

.close-btn {
  color: var(--danger);
}

.success {
  border-color: var(--accent-soft);
  color: var(--accent);
}

.empty-state {
  padding: 16px;
  text-align: center;
  color: var(--muted);
}

.student-picker-panel {
  display: grid;
  gap: 8px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.student-picker-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.student-picker-list {
  max-height: 150px;
  overflow-y: auto;
  display: grid;
  gap: 4px;
}

.student-picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  font-size: 12px;
  color: var(--text);
  border-radius: 4px;
  cursor: pointer;
}

.student-picker-item:hover {
  background: var(--surface-soft);
}
</style>
