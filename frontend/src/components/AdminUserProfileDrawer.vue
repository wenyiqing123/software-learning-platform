<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BookOpen, CircleAlert, KeyRound, LogOut, MessageSquare, RefreshCw, ShieldAlert, Target, X } from '@lucide/vue'
import type { AdminUserProfile } from '../api/types'
import FeedbackState from './FeedbackState.vue'

const props = withDefaults(defineProps<{
  open: boolean
  profile: AdminUserProfile | null
  loading?: boolean
  error?: string
  isSelf?: boolean
  revoking?: boolean
  blockEscape?: boolean
}>(), {
  loading: false,
  error: '',
  isSelf: false,
  revoking: false,
  blockEscape: false,
})

const emit = defineEmits<{
  close: []
  retry: []
  'revoke-tokens': [userId: number]
}>()

type ProfileTab = 'overview' | 'mastery' | 'wrong' | 'ai'
const activeTab = ref<ProfileTab>('overview')

watch(() => props.open, (opened) => {
  if (opened) activeTab.value = 'overview'
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open && !props.blockEscape) emit('close')
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function formatTime(iso?: string) {
  if (!iso) return '暂无记录'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function assignmentStatusLabel(status: 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED') {
  return ({ ASSIGNED: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成' }[status] || status)
}

function masteryToneClass(score: number) {
  if (score >= 80) return 'tone-good'
  if (score >= 50) return 'tone-mid'
  return 'tone-low'
}

function coursePercent(completed: number, total: number) {
  if (!total) return 0
  return Math.min(100, Math.round((completed * 100) / total))
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="profile-drawer-backdrop" data-testid="user-profile-backdrop" @click.self="emit('close')">
      <aside class="profile-drawer" role="dialog" aria-modal="true" aria-label="用户独立学情画像" data-testid="user-profile-drawer">
        <header class="profile-drawer-header">
          <div class="profile-identity">
            <span class="profile-avatar">{{ (profile?.account.displayName || profile?.account.username || '用').slice(0, 1) }}</span>
            <div class="profile-identity-copy">
              <div class="profile-identity-title">
                <h2>{{ profile?.account.displayName || '用户画像详情' }}</h2>
                <span v-if="profile" class="role-badge" :class="profile.account.role === 'ADMIN' ? 'is-admin' : 'is-student'">
                  {{ profile.account.role === 'ADMIN' ? '管理员' : '学生' }}
                </span>
                <span v-if="profile" class="status-pill" :class="profile.account.enabled ? 'is-enabled' : 'is-disabled'">
                  {{ profile.account.enabled ? '启用' : '停用' }}
                </span>
              </div>
              <p v-if="profile" class="profile-subline">@{{ profile.account.username }} · UID #{{ profile.account.id }}</p>
            </div>
          </div>
          <div class="profile-header-actions">
            <button type="button" class="icon-button" title="刷新画像" aria-label="刷新用户画像" :disabled="loading" @click="emit('retry')">
              <RefreshCw :size="16" :class="{ spinning: loading }" />
            </button>
            <button type="button" class="icon-button" title="关闭画像" aria-label="关闭用户画像" @click="emit('close')">
              <X :size="18" />
            </button>
          </div>
        </header>

        <div class="profile-drawer-body">
          <div class="privacy-notice-banner" role="note">
            <ShieldAlert :size="15" />
            <span>白名单学情视图：仅呈现进度、掌握度、错题待复习与实验任务分配状态，严格屏蔽私人笔记、收藏夹与匿名评估打分原文。</span>
          </div>

          <FeedbackState v-if="loading && !profile" :loading="true" loading-title="正在整理用户独立学习画像" loading-text="正在汇总课程进度、知识点掌握度与待巩固错题。" />
          <FeedbackState v-else-if="error && !profile" :error="error" retry-label="重新加载画像" @retry="emit('retry')" />
          <template v-else-if="profile">
            <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>
            <section class="profile-kpis" aria-label="核心学习指标">
              <article class="profile-kpi-card">
                <span>章节完成</span>
                <strong>{{ profile.summary.completedSections }} / {{ profile.summary.totalSections }}</strong>
                <small>累计完成阅读小节</small>
              </article>
              <article class="profile-kpi-card">
                <span>答题正确率</span>
                <strong>{{ profile.summary.accuracy }}%</strong>
                <small>{{ profile.summary.correctCount }} / {{ profile.summary.attemptCount }} 次答题</small>
              </article>
              <article class="profile-kpi-card">
                <span>平均掌握度</span>
                <strong>{{ profile.summary.averageMastery }}%</strong>
                <small>基于已测评知识点</small>
              </article>
              <article class="profile-kpi-card">
                <span>错题 / 待复习</span>
                <strong>{{ profile.summary.openWrongCount }} / {{ profile.summary.dueReviewCount }}</strong>
                <small>未掌握错题与到期队列</small>
              </article>
            </section>

            <nav class="profile-tabs" role="tablist" aria-label="用户画像详情分类">
              <button type="button" role="tab" :aria-selected="activeTab === 'overview'" :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">
                <KeyRound :size="14" />账号与实验
              </button>
              <button type="button" role="tab" :aria-selected="activeTab === 'mastery'" :class="{ active: activeTab === 'mastery' }" @click="activeTab = 'mastery'">
                <Target :size="14" />课程与掌握
              </button>
              <button type="button" role="tab" :aria-selected="activeTab === 'wrong'" :class="{ active: activeTab === 'wrong' }" @click="activeTab = 'wrong'">
                <CircleAlert :size="14" />错题与复习 ({{ profile.wrongQuestions.length }})
              </button>
              <button type="button" role="tab" :aria-selected="activeTab === 'ai'" :class="{ active: activeTab === 'ai' }" @click="activeTab = 'ai'">
                <MessageSquare :size="14" />助教活跃
              </button>
            </nav>

            <section v-if="activeTab === 'overview'" class="profile-tab-panel" role="tabpanel">
              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3>账号与安全状态</h3>
                  <button
                    type="button"
                    class="button button-outline button-small"
                    :disabled="isSelf || revoking || profile.account.activeSessionCount === 0"
                    :title="isSelf ? '请通过右上角退出登录结束当前账号会话' : '吊销该用户所有有效刷新令牌'"
                    @click="emit('revoke-tokens', profile.account.id)"
                  >
                    <LogOut :size="14" />{{ revoking ? '处理中...' : `强制下线 (${profile.account.activeSessionCount} 活跃)` }}
                  </button>
                </div>
                <dl class="profile-meta-grid">
                  <div><dt>注册时间</dt><dd>{{ formatTime(profile.account.createdAt) }}</dd></div>
                  <div><dt>最近更新</dt><dd>{{ formatTime(profile.account.updatedAt) }}</dd></div>
                  <div><dt>最近学习活跃</dt><dd>{{ formatTime(profile.summary.lastStudiedAt) }}</dd></div>
                  <div><dt>有效登录令牌</dt><dd>{{ profile.account.activeSessionCount }} 个</dd></div>
                </dl>
              </div>

              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3>实验任务分配履约</h3>
                  <span class="profile-count-hint">共 {{ profile.assignments.length }} 项分配记录</span>
                </div>
                <div v-if="profile.assignments.length" class="drawer-table-wrap">
                  <table class="drawer-table" aria-label="实验任务分配状态">
                    <thead>
                      <tr>
                        <th scope="col">实验任务</th>
                        <th scope="col">实验组 / 条件</th>
                        <th scope="col">状态</th>
                        <th scope="col">完成时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in profile.assignments" :key="item.taskId + '-' + item.groupName">
                        <td>{{ item.taskTitle }}</td>
                        <td>{{ item.groupName }} ({{ item.conditionKey }})</td>
                        <td><span class="assignment-pill">{{ assignmentStatusLabel(item.status) }}</span></td>
                        <td>{{ formatTime(item.completedAt || item.startedAt || item.assignedAt) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="drawer-empty-copy">该账号暂无已分配的对照实验任务。</p>
              </div>
            </section>

            <section v-else-if="activeTab === 'mastery'" class="profile-tab-panel" role="tabpanel">
              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3><BookOpen :size="15" />分课程阅读进度</h3>
                </div>
                <div v-if="profile.courses.length" class="course-progress-list">
                  <article v-for="course in profile.courses" :key="course.courseId" class="course-progress-row">
                    <div class="course-progress-top">
                      <strong>{{ course.courseName }}</strong>
                      <span>{{ course.completedSections }} / {{ course.totalSections }} 节 ({{ coursePercent(course.completedSections, course.totalSections) }}%)</span>
                    </div>
                    <div class="progress-track"><i :style="{ width: coursePercent(course.completedSections, course.totalSections) + '%' }"></i></div>
                    <small>最近阅读：{{ formatTime(course.lastStudiedAt) }}</small>
                  </article>
                </div>
                <p v-else class="drawer-empty-copy">当前暂无已启用课程的阅读章节。</p>
              </div>

              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3>知识点掌握详情（按掌握度升序）</h3>
                </div>
                <div v-if="profile.knowledgeMastery.length" class="drawer-table-wrap">
                  <table class="drawer-table" aria-label="用户知识点掌握度详情">
                    <thead>
                      <tr>
                        <th scope="col">知识点</th>
                        <th scope="col">所属课程</th>
                        <th scope="col">掌握度</th>
                        <th scope="col">答对 / 作答</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="kp in profile.knowledgeMastery" :key="kp.pointId">
                        <td>{{ kp.pointName }}</td>
                        <td>{{ kp.courseName }}</td>
                        <td><span class="mastery-score-pill" :class="masteryToneClass(kp.masteryScore)">{{ kp.masteryScore }}%</span></td>
                        <td>{{ kp.correctCount }} / {{ kp.attemptCount }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="drawer-empty-copy">暂无知识点测评记录。</p>
              </div>
            </section>

            <section v-else-if="activeTab === 'wrong'" class="profile-tab-panel" role="tabpanel">
              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3>未掌握错题与复习调度</h3>
                  <span class="profile-count-hint">展示最新 {{ profile.wrongQuestions.length }} 条</span>
                </div>
                <div v-if="profile.wrongQuestions.length" class="drawer-table-wrap">
                  <table class="drawer-table" aria-label="未掌握错题列表">
                    <thead>
                      <tr>
                        <th scope="col">知识点 / 题目摘要</th>
                        <th scope="col">错误次数</th>
                        <th scope="col">复习连胜</th>
                        <th scope="col">下次复习</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="wq in profile.wrongQuestions" :key="wq.wrongQuestionId">
                        <td>
                          <div class="wrong-stem-cell">
                            <strong>{{ wq.courseName }} · {{ wq.pointName }}</strong>
                            <span>{{ wq.stem }}</span>
                          </div>
                        </td>
                        <td>{{ wq.wrongCount }} 次</td>
                        <td>{{ wq.reviewStreak }} 次</td>
                        <td>{{ wq.dueAt ? formatTime(wq.dueAt) : '待安排' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="drawer-empty-copy">当前没有待巩固的未掌握错题。</p>
              </div>
            </section>

            <section v-else class="profile-tab-panel" role="tabpanel">
              <div class="profile-section-card">
                <div class="profile-section-head">
                  <h3>AI 助教活跃概况（仅展示主题与频次）</h3>
                  <span class="profile-count-hint">共 {{ profile.summary.aiSessionCount }} 个会话 · {{ profile.summary.aiQuestionCount }} 次提问</span>
                </div>
                <div v-if="profile.aiActivity.length" class="drawer-table-wrap">
                  <table class="drawer-table" aria-label="助教活跃记录">
                    <thead>
                      <tr>
                        <th scope="col">会话主题</th>
                        <th scope="col">提问次数</th>
                        <th scope="col">最近交互</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="session in profile.aiActivity" :key="session.sessionId">
                        <td>{{ session.title }}</td>
                        <td>{{ session.userMessageCount }} 条</td>
                        <td>{{ formatTime(session.updatedAt) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="drawer-empty-copy">该账号尚未在 AI 学习助教发起对话。</p>
              </div>
            </section>
          </template>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.profile-drawer-backdrop{position:fixed;inset:0;z-index:1100;display:flex;justify-content:flex-end;background:rgb(23 43 39 / .36)}
.profile-drawer{width:min(760px,96vw);height:100dvh;display:grid;grid-template-rows:auto minmax(0,1fr);background:var(--surface);border-left:1px solid var(--border-default);box-shadow:var(--shadow-md)}
.profile-drawer-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px 22px;border-bottom:1px solid var(--border-soft)}
.profile-identity{display:flex;align-items:center;gap:12px;min-width:0}
.profile-avatar{width:40px;height:40px;flex-shrink:0;display:grid;place-items:center;color:var(--accent-strong);background:var(--accent-soft);border-radius:50%;font-size:15px;font-weight:700}
.profile-identity-copy{min-width:0}
.profile-identity-title{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.profile-identity-title h2{margin:0;font-size:18px;line-height:1.3;color:var(--text)}
.profile-subline{margin:4px 0 0;color:var(--text-muted);font-size:12px}
.profile-header-actions{display:flex;align-items:center;gap:6px}
.profile-drawer-body{overflow-y:auto;padding:18px 22px 28px;display:grid;align-content:start;gap:16px}
.privacy-notice-banner{display:flex;align-items:flex-start;gap:8px;padding:10px 12px;color:var(--info);background:var(--info-soft);border:1px solid var(--border-soft);border-radius:var(--radius-sm);font-size:12px;line-height:1.5}
.privacy-notice-banner svg{flex-shrink:0;margin-top:2px}
.profile-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.profile-kpi-card{padding:12px 14px;background:var(--surface-soft);border:1px solid var(--border-soft);border-radius:var(--radius-sm);display:grid;gap:4px;font-variant-numeric:tabular-nums}
.profile-kpi-card span,.profile-kpi-card small{color:var(--text-muted);font-size:11.5px}
.profile-kpi-card strong{color:var(--text);font-size:20px;line-height:1.25}
.profile-tabs{display:flex;flex-wrap:wrap;gap:6px;padding:4px;background:var(--surface-tint);border-radius:var(--radius-sm)}
.profile-tabs button{display:inline-flex;align-items:center;gap:6px;min-height:34px;padding:5px 12px;color:var(--text-muted);background:transparent;border:0;border-radius:6px;font-size:12.5px;font-weight:500;cursor:pointer}
.profile-tabs button:hover{color:var(--accent-strong)}
.profile-tabs button.active{color:var(--accent-strong);background:var(--surface);box-shadow:var(--shadow-xs);font-weight:650}
.profile-tab-panel{display:grid;gap:14px}
.profile-section-card{padding:16px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);display:grid;gap:12px}
.profile-section-head{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px}
.profile-section-head h3{margin:0;display:inline-flex;align-items:center;gap:7px;font-size:14.5px;color:var(--text)}
.profile-count-hint{color:var(--text-muted);font-size:12px}
.profile-meta-grid{margin:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px 16px}
.profile-meta-grid div{display:grid;gap:3px;padding:8px 10px;background:var(--surface-soft);border-radius:var(--radius-sm)}
.profile-meta-grid dt{color:var(--text-subtle);font-size:11.5px}
.profile-meta-grid dd{margin:0;color:var(--text);font-size:13px;font-weight:600;font-variant-numeric:tabular-nums}
.drawer-table-wrap{overflow-x:auto}
.drawer-table{width:100%;border-collapse:collapse;border:1px solid var(--border-default);font-size:12.5px;font-variant-numeric:tabular-nums}
.drawer-table th,.drawer-table td{padding:9px 12px;border-top:1px solid var(--border-soft);text-align:left;vertical-align:top}
.drawer-table th{background:var(--surface-soft);color:var(--text-muted);font-weight:650;white-space:nowrap}
.drawer-table tr:first-child td{border-top:0}
.course-progress-list{display:grid;gap:12px}
.course-progress-row{display:grid;gap:6px;padding:10px 12px;background:var(--surface-soft);border-radius:var(--radius-sm)}
.course-progress-top{display:flex;justify-content:space-between;gap:10px;font-size:13px}
.course-progress-row small{color:var(--text-muted);font-size:11.5px}
.progress-track{height:6px;overflow:hidden;background:var(--surface-tint);border-radius:var(--radius-pill)}
.progress-track i{display:block;height:100%;background:var(--accent);border-radius:inherit}
.role-badge,.status-pill,.assignment-pill,.mastery-score-pill{display:inline-flex;align-items:center;padding:2px 8px;border-radius:var(--radius-pill);font-size:11.5px;font-weight:600;white-space:nowrap}
.role-badge.is-admin{color:var(--info);background:var(--info-soft)}
.role-badge.is-student{color:var(--accent-strong);background:var(--accent-soft)}
.status-pill.is-enabled{color:var(--accent-strong);background:var(--accent-soft)}
.status-pill.is-disabled{color:var(--danger);background:var(--danger-soft)}
.assignment-pill{color:var(--text-muted);background:var(--surface-tint)}
.mastery-score-pill.tone-good{color:var(--accent-strong);background:var(--accent-soft)}
.mastery-score-pill.tone-mid{color:var(--warning);background:var(--warning-soft)}
.mastery-score-pill.tone-low{color:var(--danger);background:var(--danger-soft)}
.wrong-stem-cell{display:grid;gap:3px;max-width:340px}
.wrong-stem-cell strong{color:var(--text);font-size:12px}
.wrong-stem-cell span{color:var(--text-muted);font-size:12px;line-height:1.45}
.drawer-empty-copy{margin:0;color:var(--text-muted);font-size:13px}
.spinning{animation:spin 1s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:720px){.profile-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.profile-meta-grid{grid-template-columns:1fr}}
</style>
