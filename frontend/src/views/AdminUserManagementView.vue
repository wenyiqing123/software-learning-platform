<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { KeyRound, RefreshCw, Search, ShieldAlert, UserCheck, UserCog, UserPlus, UserX, Users, X } from '@lucide/vue'
import { apiError, learningApi } from '../api/client'
import type { AdminUserProfile, AdminUserSummary } from '../api/types'
import { useSession } from '../state/session'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import AdminUserProfileDrawer from '../components/AdminUserProfileDrawer.vue'

const session = useSession()
const currentUserId = computed(() => session.user.value?.id)

const initialLoading = ref(true)
const refreshing = ref(false)
const working = ref(false)
const error = ref('')
const toastMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | null = null

const users = ref<AdminUserSummary[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = 20

const keyword = ref('')
const roleFilter = ref<'ALL' | 'STUDENT' | 'ADMIN'>('ALL')
const statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')

// 独立画像抽屉状态
const profileOpen = ref(false)
const profileLoading = ref(false)
const profileError = ref('')
const profileData = ref<AdminUserProfile | null>(null)
const profileUserId = ref<number | null>(null)
const revokingTokens = ref(false)

// 表单对话框状态（新建 / 编辑 / 重置密码）
const modalKind = ref<'create' | 'edit' | 'password' | null>(null)
const modalError = ref('')
const editingUser = ref<AdminUserSummary | null>(null)
const formState = reactive({
  username: '',
  displayName: '',
  role: 'STUDENT' as 'STUDENT' | 'ADMIN',
  password: '',
  confirmPassword: '',
})

// 页面内二次确认弹窗（替代浏览器原生 window.confirm）
interface ConfirmDialogState {
  kind: 'disable' | 'enable' | 'revoke'
  userId: number
  title: string
  description: string
  confirmLabel: string
  danger: boolean
}
const confirmDialog = ref<ConfirmDialogState | null>(null)
const confirmWorking = ref(false)
const confirmError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function showToast(message: string) {
  toastMessage.value = message
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
    toastTimer = null
  }, 3200)
}

function dismissToast() {
  if (toastTimer) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
  toastMessage.value = ''
}

function formatTime(iso?: string) {
  if (!iso) return '尚未活跃'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}

function upsertUserRow(updated: AdminUserSummary) {
  const idx = users.value.findIndex(item => item.id === updated.id)
  if (idx >= 0) {
    const next = [...users.value]
    next[idx] = updated
    users.value = next
  }
}

async function loadUsers(targetPage = page.value, silent = false) {
  const showFullLoader = !silent && initialLoading.value && !users.value.length
  if (!showFullLoader) refreshing.value = true
  error.value = ''
  try {
    const enabled = statusFilter.value === 'ALL' ? undefined : statusFilter.value === 'ENABLED'
    const data = await learningApi.listAdminUsers({
      keyword: keyword.value.trim() || undefined,
      role: roleFilter.value,
      enabled,
      page: targetPage,
      size: pageSize,
    })
    users.value = data.items
    total.value = data.total
    page.value = data.page
  } catch (cause) {
    error.value = apiError(cause, '用户列表暂时无法加载。')
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

function searchUsers() {
  void loadUsers(0)
}

function resetFilters() {
  keyword.value = ''
  roleFilter.value = 'ALL'
  statusFilter.value = 'ALL'
  void loadUsers(0)
}

async function openProfile(user: AdminUserSummary) {
  const switchedUser = profileUserId.value !== user.id
  profileUserId.value = user.id
  if (switchedUser) {
    profileData.value = null
    profileError.value = ''
  }
  profileOpen.value = true
  await reloadProfile()
}

async function reloadProfile() {
  if (!profileUserId.value) return
  profileLoading.value = true
  profileError.value = ''
  try {
    profileData.value = await learningApi.getAdminUserProfile(profileUserId.value)
  } catch (cause) {
    profileError.value = apiError(cause, '用户独立画像加载失败。')
  } finally {
    profileLoading.value = false
  }
}

function requestRevokeTokens(userId: number) {
  const target = users.value.find(item => item.id === userId)
  const label = target ? `${target.displayName} (@${target.username})` : `UID #${userId}`
  confirmError.value = ''
  confirmDialog.value = {
    kind: 'revoke',
    userId,
    title: '强制全端下线',
    description: `确认吊销「${label}」的所有有效刷新令牌？该账号下次刷新凭证时需重新登录。`,
    confirmLabel: '确认强制下线',
    danger: true,
  }
}

function requestToggleEnabled(user: AdminUserSummary) {
  if (user.id === currentUserId.value) return
  confirmError.value = ''
  if (user.enabled) {
    confirmDialog.value = {
      kind: 'disable',
      userId: user.id,
      title: '停用用户账号',
      description: `确认停用「${user.displayName}」(@${user.username})？停用后该账号将无法继续登录，现有刷新令牌也会被立即吊销。`,
      confirmLabel: '确认停用',
      danger: true,
    }
  } else {
    confirmDialog.value = {
      kind: 'enable',
      userId: user.id,
      title: '恢复启用账号',
      description: `确认恢复启用「${user.displayName}」(@${user.username})，允许其重新登录平台？`,
      confirmLabel: '确认启用',
      danger: false,
    }
  }
}

function closeConfirmDialog() {
  if (confirmWorking.value) return
  confirmDialog.value = null
  confirmError.value = ''
}

async function submitConfirmDialog() {
  const dialog = confirmDialog.value
  if (!dialog || confirmWorking.value) return
  confirmWorking.value = true
  confirmError.value = ''
  error.value = ''

  try {
    if (dialog.kind === 'revoke') {
      revokingTokens.value = true
      const res = await learningApi.revokeAdminUserTokens(dialog.userId)
      confirmDialog.value = null
      showToast(`已吊销 ${res.revokedCount} 个活跃会话令牌。`)
      const targetRow = users.value.find(item => item.id === dialog.userId)
      if (targetRow) upsertUserRow({ ...targetRow, activeTokenCount: 0 })
      if (profileOpen.value && profileUserId.value === dialog.userId) await reloadProfile()
      return
    }

    const nextEnabled = dialog.kind === 'enable'
    const updated = await learningApi.setAdminUserEnabled(dialog.userId, nextEnabled)
    confirmDialog.value = null
    upsertUserRow(updated)
    showToast(`账号「${updated.displayName}」已${updated.enabled ? '启用' : '停用'}。`)
    if (profileOpen.value && profileUserId.value === updated.id) await reloadProfile()
  } catch (cause) {
    confirmError.value = apiError(cause, '操作未能完成，请重试。')
  } finally {
    confirmWorking.value = false
    revokingTokens.value = false
  }
}

function openCreateModal() {
  modalKind.value = 'create'
  modalError.value = ''
  editingUser.value = null
  formState.username = ''
  formState.displayName = ''
  formState.role = 'STUDENT'
  formState.password = ''
  formState.confirmPassword = ''
}

function openEditModal(user: AdminUserSummary) {
  modalKind.value = 'edit'
  modalError.value = ''
  editingUser.value = user
  formState.username = user.username
  formState.displayName = user.displayName
  formState.role = user.role
}

function openPasswordModal(user: AdminUserSummary) {
  modalKind.value = 'password'
  modalError.value = ''
  editingUser.value = user
  formState.password = ''
  formState.confirmPassword = ''
}

function closeModal() {
  if (working.value) return
  modalKind.value = null
  modalError.value = ''
  editingUser.value = null
}

function validatePasswordInputs() {
  if (!formState.password || !formState.confirmPassword) {
    return '请完整填写密码与确认密码。'
  }
  if (formState.password !== formState.confirmPassword) {
    return '两次输入的密码不一致。'
  }
  const codePoints = Array.from(formState.password).length
  if (codePoints < 8) {
    return '密码长度至少需要 8 个字符。'
  }
  if (new TextEncoder().encode(formState.password).length > 72) {
    return '密码 UTF-8 字节长度不能超过 72。'
  }
  return ''
}

async function submitModal() {
  if (working.value || !modalKind.value) return
  modalError.value = ''

  if (modalKind.value === 'create') {
    const username = formState.username.trim()
    if (!/^[A-Za-z0-9_.-]{3,60}$/.test(username)) {
      modalError.value = '用户名需为 3~60 位字母、数字或 . _ - 符号。'
      return
    }
    const pwdErr = validatePasswordInputs()
    if (pwdErr) {
      modalError.value = pwdErr
      return
    }
    working.value = true
    try {
      const created = await learningApi.createAdminUser({
        username,
        displayName: formState.displayName.trim() || username,
        role: formState.role,
        password: formState.password,
      })
      modalKind.value = null
      showToast(`已创建用户「${created.displayName}」(@${created.username})。`)
      await loadUsers(0, true)
    } catch (cause) {
      modalError.value = apiError(cause, '创建用户失败。')
    } finally {
      working.value = false
    }
    return
  }

  if (modalKind.value === 'edit' && editingUser.value) {
    if (!formState.displayName.trim()) {
      modalError.value = '请填写显示名称。'
      return
    }
    working.value = true
    try {
      const updated = await learningApi.updateAdminUser(editingUser.value.id, {
        displayName: formState.displayName.trim(),
        role: formState.role,
      })
      modalKind.value = null
      upsertUserRow(updated)
      showToast(`已更新用户「${updated.displayName}」资料。`)
      if (profileOpen.value && profileUserId.value === updated.id) await reloadProfile()
    } catch (cause) {
      modalError.value = apiError(cause, '更新用户失败。')
    } finally {
      working.value = false
    }
    return
  }

  if (modalKind.value === 'password' && editingUser.value) {
    const pwdErr = validatePasswordInputs()
    if (pwdErr) {
      modalError.value = pwdErr
      return
    }
    working.value = true
    try {
      const target = editingUser.value
      await learningApi.resetAdminUserPassword(target.id, formState.password)
      modalKind.value = null
      upsertUserRow({ ...target, activeTokenCount: 0 })
      showToast(`已重置「${target.displayName}」密码并吊销旧会话。`)
      if (profileOpen.value && profileUserId.value === target.id) await reloadProfile()
    } catch (cause) {
      modalError.value = apiError(cause, '重置密码失败。')
    } finally {
      working.value = false
    }
  }
}

function onModalKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (confirmDialog.value) {
    closeConfirmDialog()
    return
  }
  if (modalKind.value) closeModal()
}

onMounted(() => {
  void loadUsers(0)
  window.addEventListener('keydown', onModalKeydown)
})
onBeforeUnmount(() => {
  dismissToast()
  window.removeEventListener('keydown', onModalKeydown)
})
</script>

<template>
  <div class="page-stack admin-users-page">
    <SectionHeading
      eyebrow="管理员工作台 · 用户治理"
      title="用户与学情管理"
      description="管理平台账号状态与安全会话，按白名单脱敏查看单用户课程进度、知识点掌握及错题概况（不包含私人笔记与匿名评价原文）。"
    >
      <button type="button" class="button button-outline" :disabled="initialLoading || refreshing" @click="loadUsers(page, true)">
        <RefreshCw :size="15" :class="{ spinning: refreshing }" />刷新
      </button>
      <button type="button" class="button button-dark" data-testid="create-user-btn" @click="openCreateModal"><UserPlus :size="15" />新建用户</button>
    </SectionHeading>

    <div v-if="error" class="inline-notice" role="alert">{{ error }}</div>

    <section class="user-filters" aria-label="用户筛选条件">
      <label class="user-search-field">
        <Search :size="15" />
        <input v-model="keyword" type="search" placeholder="搜索用户名或姓名" aria-label="搜索用户" @keydown.enter.prevent="searchUsers" />
      </label>
      <label>
        <span>角色</span>
        <select v-model="roleFilter" aria-label="筛选角色" @change="searchUsers">
          <option value="ALL">全部角色</option>
          <option value="STUDENT">学生</option>
          <option value="ADMIN">管理员</option>
        </select>
      </label>
      <label>
        <span>状态</span>
        <select v-model="statusFilter" aria-label="筛选状态" @change="searchUsers">
          <option value="ALL">全部状态</option>
          <option value="ENABLED">已启用</option>
          <option value="DISABLED">已停用</option>
        </select>
      </label>
      <button type="button" class="button button-outline button-small" @click="searchUsers">查询</button>
      <button type="button" class="text-button" @click="resetFilters">重置</button>
      <span class="user-total-count"><Users :size="14" />共 {{ total }} 位用户</span>
    </section>

    <FeedbackState v-if="initialLoading" :loading="true" loading-title="正在加载用户与学习概览" loading-text="正在读取账号目录及汇总进度数据。" />
    <FeedbackState v-else-if="!users.length" :empty="true" empty-title="没有匹配的用户记录" empty-text="尝试调整搜索关键词或角色筛选条件。" action-label="新建用户" @action="openCreateModal" />
    <section v-else class="user-table-card" :class="{ 'is-refreshing': refreshing }">
      <div class="user-table-wrap">
        <table class="user-table" aria-label="平台用户管理表">
          <thead>
            <tr>
              <th scope="col">用户账号</th>
              <th scope="col">角色</th>
              <th scope="col">状态与会话</th>
              <th scope="col">学习与掌握概况</th>
              <th scope="col">最近学习</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id" :data-testid="`user-row-${user.id}`">
              <td>
                <div class="user-identity-cell">
                  <div class="user-name-line">
                    <strong>{{ user.displayName }}</strong>
                    <span v-if="user.id === currentUserId" class="self-badge">当前账号</span>
                  </div>
                  <small>@{{ user.username }} · #{{ user.id }}</small>
                </div>
              </td>
              <td>
                <span class="role-badge" :class="user.role === 'ADMIN' ? 'is-admin' : 'is-student'">
                  {{ user.role === 'ADMIN' ? '管理员' : '学生' }}
                </span>
              </td>
              <td>
                <div class="user-status-cell">
                  <span class="status-pill" :class="user.enabled ? 'is-enabled' : 'is-disabled'">
                    {{ user.enabled ? '已启用' : '已停用' }}
                  </span>
                  <small>活跃会话 {{ user.activeTokenCount }}</small>
                </div>
              </td>
              <td>
                <div class="user-learning-cell">
                  <span>已读 {{ user.completedSections }} 节 · 掌握度 {{ user.averageMastery }}%</span>
                  <small>答题 {{ user.correctCount }}/{{ user.attemptCount }} ({{ user.accuracy }}%) · 错题 {{ user.openWrongCount }} · 待复习 {{ user.dueReviewCount }}</small>
                </div>
              </td>
              <td>{{ formatTime(user.lastActiveAt) }}</td>
              <td>
                <div class="user-row-actions">
                  <button type="button" class="button button-outline button-small" :data-testid="`user-profile-btn-${user.id}`" @click="openProfile(user)">学情画像</button>
                  <button type="button" class="icon-button" title="编辑资料与角色" aria-label="编辑用户" @click="openEditModal(user)"><UserCog :size="15" /></button>
                  <button type="button" class="icon-button" title="重置密码" aria-label="重置密码" @click="openPasswordModal(user)"><KeyRound :size="15" /></button>
                  <button
                    type="button"
                    class="icon-button"
                    :disabled="user.id === currentUserId"
                    :title="user.id === currentUserId ? '不能停用当前登录账号' : (user.enabled ? '停用账号' : '启用账号')"
                    :aria-label="user.enabled ? '停用账号' : '启用账号'"
                    @click="requestToggleEnabled(user)"
                  >
                    <UserX v-if="user.enabled" :size="15" />
                    <UserCheck v-else :size="15" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="totalPages > 1" class="user-pagination">
        <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
        <div class="user-pagination-btns">
          <button type="button" class="button button-outline button-small" :disabled="page <= 0" @click="loadUsers(page - 1, true)">上一页</button>
          <button type="button" class="button button-outline button-small" :disabled="page + 1 >= totalPages" @click="loadUsers(page + 1, true)">下一页</button>
        </div>
      </footer>
    </section>

    <AdminUserProfileDrawer
      :open="profileOpen"
      :profile="profileData"
      :loading="profileLoading"
      :error="profileError"
      :is-self="profileUserId === currentUserId"
      :revoking="revokingTokens"
      :block-escape="Boolean(confirmDialog || modalKind)"
      @close="profileOpen = false"
      @retry="reloadProfile"
      @revoke-tokens="requestRevokeTokens"
    />

    <Teleport to="body">
      <div v-if="toastMessage" class="user-toast" role="status" data-testid="user-toast">
        <span>{{ toastMessage }}</span>
        <button type="button" class="icon-button" aria-label="关闭提示" @click="dismissToast"><X :size="14" /></button>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="modalKind" class="user-modal-backdrop" @click.self="closeModal">
        <div class="user-modal-card" role="dialog" aria-modal="true" :aria-label="modalKind === 'create' ? '新建用户' : modalKind === 'edit' ? '编辑用户资料' : '重置用户密码'">
          <header class="user-modal-head">
            <h3>{{ modalKind === 'create' ? '新建平台账号' : modalKind === 'edit' ? `编辑账号 · ${editingUser?.displayName}` : `重置密码 · ${editingUser?.displayName}` }}</h3>
            <button type="button" class="icon-button" aria-label="关闭窗口" :disabled="working" @click="closeModal"><X :size="16" /></button>
          </header>

          <form class="user-modal-form" @submit.prevent="submitModal">
            <div v-if="modalError" class="inline-notice" role="alert">{{ modalError }}</div>

            <template v-if="modalKind === 'create'">
              <label>
                <span>用户名（3~60 位字母/数字/._-）</span>
                <input v-model="formState.username" type="text" required autocomplete="off" placeholder="例如 stu_2026_01" />
              </label>
              <label>
                <span>显示名称</span>
                <input v-model="formState.displayName" type="text" maxlength="80" placeholder="例如 张三" />
              </label>
              <label>
                <span>用户角色</span>
                <select v-model="formState.role">
                  <option value="STUDENT">学生 (STUDENT)</option>
                  <option value="ADMIN">管理员 (ADMIN)</option>
                </select>
              </label>
            </template>

            <template v-if="modalKind === 'edit'">
              <label>
                <span>显示名称</span>
                <input v-model="formState.displayName" type="text" required maxlength="80" />
              </label>
              <label>
                <span>用户角色</span>
                <select v-model="formState.role" :disabled="editingUser?.id === currentUserId">
                  <option value="STUDENT">学生 (STUDENT)</option>
                  <option value="ADMIN">管理员 (ADMIN)</option>
                </select>
                <small v-if="editingUser?.id === currentUserId" class="modal-hint">安全策略：不可修改当前登录管理员自身角色。</small>
              </label>
            </template>

            <template v-if="modalKind === 'create' || modalKind === 'password'">
              <label>
                <span>{{ modalKind === 'create' ? '初始密码（8~72 字符）' : '新临时密码（8~72 字符）' }}</span>
                <input v-model="formState.password" type="password" required autocomplete="new-password" />
              </label>
              <label>
                <span>确认密码</span>
                <input v-model="formState.confirmPassword" type="password" required autocomplete="new-password" />
              </label>
              <p v-if="modalKind === 'password'" class="modal-hint">重置成功后将立刻吊销该账号的所有活跃登录会话。</p>
            </template>

            <footer class="user-modal-actions">
              <button type="button" class="button button-outline" :disabled="working" @click="closeModal">取消</button>
              <button type="submit" class="button button-dark" :disabled="working">{{ working ? '提交中...' : '确认提交' }}</button>
            </footer>
          </form>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="confirmDialog" class="user-modal-backdrop" data-testid="user-confirm-backdrop" @click.self="closeConfirmDialog">
        <div class="user-modal-card" role="dialog" aria-modal="true" :aria-label="confirmDialog.title" data-testid="user-confirm-dialog">
          <header class="user-modal-head">
            <div class="confirm-head-title">
              <span class="confirm-head-icon" :class="{ 'is-danger': confirmDialog.danger }"><ShieldAlert :size="16" /></span>
              <h3>{{ confirmDialog.title }}</h3>
            </div>
            <button type="button" class="icon-button" aria-label="关闭确认窗口" :disabled="confirmWorking" @click="closeConfirmDialog"><X :size="16" /></button>
          </header>
          <div class="user-modal-form">
            <div v-if="confirmError" class="inline-notice" role="alert">{{ confirmError }}</div>
            <p class="confirm-description">{{ confirmDialog.description }}</p>
            <footer class="user-modal-actions">
              <button type="button" class="button button-outline" :disabled="confirmWorking" @click="closeConfirmDialog">取消</button>
              <button
                type="button"
                class="button"
                :class="confirmDialog.danger ? 'button-danger' : 'button-dark'"
                :disabled="confirmWorking"
                data-testid="confirm-dialog-submit"
                @click="submitConfirmDialog"
              >
                {{ confirmWorking ? '处理中...' : confirmDialog.confirmLabel }}
              </button>
            </footer>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.admin-users-page{min-width:0}
.user-filters{display:flex;align-items:center;flex-wrap:wrap;gap:10px 14px;padding:12px 14px;background:var(--surface-tint);border-radius:var(--radius-md)}
.user-filters label{display:flex;align-items:center;gap:8px;color:var(--text-muted);font-size:13px}
.user-search-field{flex:1 1 210px;min-width:0;padding:0 10px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-sm)}
.user-search-field input{width:100%;min-height:var(--control-height-small);border:0;background:transparent;outline:0;font-size:13px}
.user-filters select{min-height:var(--control-height-small)}
.user-total-count{margin-left:auto;display:inline-flex;align-items:center;gap:6px;color:var(--text-muted);font-size:13px;font-variant-numeric:tabular-nums}
.user-table-card{background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs);overflow:hidden;transition:opacity var(--motion)}
.user-table-card.is-refreshing{opacity:.78}
.user-table-wrap{overflow-x:auto}
.user-table{width:100%;border-collapse:collapse;border:1px solid var(--border-soft);font-size:13px;font-variant-numeric:tabular-nums}
.user-table th,.user-table td{padding:11px 14px;border-top:1px solid var(--border-soft);text-align:left;vertical-align:middle}
.user-table th{background:var(--surface-soft);color:var(--text-muted);font-size:12px;font-weight:650;white-space:nowrap}
.user-table tbody tr:first-child td{border-top:0}
.user-table tbody tr:hover{background:var(--surface-soft)}
.user-identity-cell,.user-status-cell,.user-learning-cell{display:grid;gap:3px}
.user-name-line{display:flex;align-items:center;gap:8px}
.user-identity-cell small,.user-status-cell small,.user-learning-cell small{color:var(--text-muted);font-size:12px}
.self-badge{padding:1px 7px;color:var(--accent-strong);background:var(--accent-soft);border-radius:var(--radius-pill);font-size:12px;font-weight:600}
.role-badge,.status-pill{display:inline-flex;width:fit-content;align-items:center;padding:2px 8px;border-radius:var(--radius-pill);font-size:12px;font-weight:600;white-space:nowrap}
.role-badge.is-admin{color:var(--info);background:var(--info-soft)}
.role-badge.is-student{color:var(--accent-strong);background:var(--accent-soft)}
.status-pill.is-enabled{color:var(--accent-strong);background:var(--accent-soft)}
.status-pill.is-disabled{color:var(--danger);background:var(--danger-soft)}
.user-row-actions{display:flex;align-items:center;gap:4px}
.user-pagination{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-top:1px solid var(--border-soft);color:var(--text-muted);font-size:13px}
.user-pagination-btns{display:flex;gap:8px}
.user-modal-backdrop{position:fixed;inset:0;z-index:1150;display:grid;place-items:center;padding:16px;background:rgb(23 43 39 / .36)}
.user-modal-card{width:min(440px,100%);background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-md);overflow:hidden}
.user-modal-head{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid var(--border-soft)}
.user-modal-head h3{margin:0;font-size:16px;color:var(--text)}
.confirm-head-title{display:flex;align-items:center;gap:10px}
.confirm-head-icon{width:30px;height:30px;display:grid;place-items:center;color:var(--accent-strong);background:var(--accent-soft);border-radius:var(--radius-sm)}
.confirm-head-icon.is-danger{color:var(--danger);background:var(--danger-soft)}
.confirm-description{margin:0;color:var(--text-muted);font-size:13.5px;line-height:1.6}
.user-modal-form{padding:18px 20px;display:grid;gap:14px}
.user-modal-form label{display:grid;gap:6px;font-size:12.5px;color:var(--text-muted)}
.user-modal-form input,.user-modal-form select{min-height:var(--control-height);padding:6px 10px;color:var(--text);background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-sm);font:inherit}
.modal-hint{margin:0;color:var(--text-muted);font-size:12px}
.user-modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:4px}
.button-danger{color:var(--surface);background:var(--danger)}
.button-danger:hover:not(:disabled){opacity:.9}
.user-toast{position:fixed;right:24px;bottom:24px;z-index:1250;display:flex;align-items:center;gap:10px;max-width:min(420px,calc(100vw - 32px));padding:10px 12px 10px 16px;color:var(--accent-strong);background:var(--surface);border:1px solid var(--border-default);border-left:3px solid var(--accent);border-radius:var(--radius-md);box-shadow:var(--shadow-md);font-size:13px;font-weight:500}
.spinning{animation:spin 1s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:700px){.user-total-count{width:100%;margin-left:0}}
</style>
