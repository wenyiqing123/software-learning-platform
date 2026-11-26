<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, apiError, type ExperimentAssignment } from '../api/client'
import { useRouter } from 'vue-router'

const router = useRouter()
const assignments = ref<ExperimentAssignment[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    assignments.value = (await api.get<ExperimentAssignment[]>('/experiment-tasks')).data
  } catch (cause) {
    error.value = apiError(cause, '实验任务暂时无法加载。')
  } finally {
    loading.value = false
  }
}

function startAssessment(task: ExperimentAssignment) {
  if (!task.taskOrder || task.status === 'COMPLETED' || task.taskStatus !== 'OPEN') return
  void router.push({
    path: '/evaluation',
    query: {
      taskKey: task.title,
      conditionKey: task.conditionKey,
      taskOrder: task.taskOrder,
      assignmentId: String(task.id),
      taskId: String(task.taskId)
    }
  })
}

function openQuestionnaire(task: ExperimentAssignment) {
  void router.push({
    path: '/evaluation',
    query: {
      taskKey: task.title,
      conditionKey: task.conditionKey,
      taskOrder: task.taskOrder || undefined,
      assignmentId: String(task.id),
      taskId: String(task.taskId)
    }
  })
}

onMounted(load)
</script>

<template>
  <div class="page-stack experiment-tasks-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">SERVER-MANAGED EXPERIMENTS</p>
        <h1>实验任务</h1>
        <p>任务、分组、顺序和完成状态由服务器记录；先阅读知情说明，再开始前测和实验任务。</p>
      </div>
    </header>
    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
    <section v-if="loading" class="empty-state">正在加载实验任务…</section>
    <section v-else class="task-list">
      <article v-for="task in assignments" :key="task.id" class="task-card">
        <div class="task-card-heading">
          <div>
            <strong>{{ task.title }}</strong>
            <small>{{ task.groupName }} · {{ task.conditionKey }} · {{ task.experimentType }}</small>
          </div>
          <span :class="['status-tag', task.status.toLowerCase()]">
            {{ task.status === 'ASSIGNED' ? '待开始' : task.status === 'IN_PROGRESS' ? '进行中' : '已完成' }}
          </span>
        </div>
        <p>{{ task.description || '完成指定问答实验并提交真实服务器结果。' }}</p>
        <div class="task-protocol">
          <span class="protocol-order">{{ task.taskOrder ? `顺序 ${task.taskOrder}` : '顺序未记录' }}</span>
          <span>前测 → 任务 → 后测</span>
          <span>可补录延后测验</span>
        </div>
        <div class="task-question">{{ task.question }}</div>
        <div class="task-actions">
          <button
            class="primary-button"
            :aria-label="'开始实验任务 ' + task.id"
            :disabled="task.status === 'COMPLETED' || task.taskStatus !== 'OPEN' || !task.taskOrder"
            @click="startAssessment(task)"
          >
            {{ task.status === 'COMPLETED' ? '已完成' : task.taskStatus !== 'OPEN' ? '等待开放' : !task.taskOrder ? '顺序未记录（已禁用）' : '开始评估与任务' }}
          </button>
          <button
            class="secondary-button"
            :disabled="task.status === 'COMPLETED'"
            @click="openQuestionnaire(task)"
          >
            仅查看评估说明
          </button>
        </div>
      </article>
      <div v-if="!assignments.length" class="empty-state">当前账户还没有被分配实验任务。</div>
    </section>
  </div>
</template>

<style scoped>
.task-list {
  display: grid;
  gap: 16px;
}

.task-card {
  display: grid;
  gap: 14px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.task-card-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.task-card-heading strong,
.task-card-heading small {
  display: block;
}

.task-card-heading small {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 12px;
}

.task-card p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.task-protocol {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.task-protocol span {
  padding: 4px 8px;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.protocol-order {
  font-weight: 600;
}

.task-question {
  padding: 10px 14px;
  color: var(--text);
  background: var(--surface-soft);
  border-left: 3px solid var(--accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: 13px;
}

.status-tag {
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  align-self: flex-start;
}

.status-tag.assigned {
  background: var(--warning-soft);
  color: var(--warning);
}

.status-tag.in_progress {
  background: var(--info-soft);
  color: var(--info);
}

.status-tag.completed {
  background: var(--accent-soft);
  color: var(--accent);
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.primary-button,
.secondary-button {
  min-height: 36px;
  border-radius: var(--radius-sm);
  padding: 0 16px;
  cursor: pointer;
  font-size: 13px;
}

.primary-button {
  border: 0;
  background: var(--accent);
  color: var(--surface);
  font-weight: 500;
}

.primary-button:hover:not(:disabled) {
  background: var(--accent-strong);
}

.secondary-button {
  border: 1px solid var(--border-default);
  background: var(--surface);
  color: var(--text-muted);
}

.secondary-button:hover:not(:disabled) {
  background: var(--surface-tint);
  color: var(--text);
  border-color: var(--border-focus);
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
