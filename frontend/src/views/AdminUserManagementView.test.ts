import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AdminUserManagementView from './AdminUserManagementView.vue'

const mocks = vi.hoisted(() => ({
  listAdminUsers: vi.fn(),
  createAdminUser: vi.fn(),
  updateAdminUser: vi.fn(),
  setAdminUserEnabled: vi.fn(),
  resetAdminUserPassword: vi.fn(),
  revokeAdminUserTokens: vi.fn(),
  getAdminUserProfile: vi.fn(),
}))

const sessionMock = vi.hoisted(() => ({
  user: { value: { id: 3, username: 'admin01', displayName: '课程管理员', role: 'ADMIN' } },
}))

vi.mock('../api/client', () => ({
  learningApi: mocks,
  apiError: (err: unknown, fallback: string) =>
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback,
}))

vi.mock('../state/session', () => ({
  useSession: () => sessionMock,
}))

const sampleUsers = [
  {
    id: 1,
    username: 'student01',
    displayName: '张同学',
    role: 'STUDENT' as const,
    enabled: true,
    completedSections: 4,
    attemptCount: 10,
    correctCount: 8,
    accuracy: 80,
    averageMastery: 72.5,
    openWrongCount: 2,
    dueReviewCount: 1,
    activeTokenCount: 1,
    lastActiveAt: '2026-11-25T10:00:00',
  },
  {
    id: 3,
    username: 'admin01',
    displayName: '课程管理员',
    role: 'ADMIN' as const,
    enabled: true,
    completedSections: 0,
    attemptCount: 0,
    correctCount: 0,
    accuracy: 0,
    averageMastery: 0,
    openWrongCount: 0,
    dueReviewCount: 0,
    activeTokenCount: 1,
  },
]

const sampleProfile = {
  account: {
    id: 1,
    username: 'student01',
    displayName: '张同学',
    role: 'STUDENT' as const,
    enabled: true,
    createdAt: '2026-11-01T08:00:00',
    updatedAt: '2026-11-25T10:00:00',
    activeSessionCount: 1,
  },
  summary: {
    completedSections: 4,
    totalSections: 8,
    attemptCount: 10,
    correctCount: 8,
    accuracy: 80,
    averageMastery: 72.5,
    openWrongCount: 2,
    dueReviewCount: 1,
    aiSessionCount: 2,
    aiQuestionCount: 6,
    lastStudiedAt: '2026-11-25T10:00:00',
  },
  courses: [
    { courseId: 1, courseName: '软件工程', completedSections: 4, totalSections: 8, lastStudiedAt: '2026-11-25T10:00:00' },
  ],
  knowledgeMastery: [
    { pointId: 2, pointName: '数据流图 DFD', courseId: 1, courseName: '软件工程', masteryScore: 48.0, attemptCount: 4, correctCount: 2 },
  ],
  wrongQuestions: [
    {
      wrongQuestionId: 9,
      questionId: 101,
      courseName: '软件工程',
      pointName: '数据流图 DFD',
      stem: '数据流图中的加工应该如何规范命名？',
      wrongCount: 2,
      status: 'UNMASTERED',
      reviewStreak: 0,
      dueAt: '2026-11-26T09:00:00',
    },
  ],
  assignments: [
    { taskId: 9, taskTitle: '需求分析对照任务', groupName: '实验组 A', conditionKey: 'RANKED', status: 'IN_PROGRESS' as const },
  ],
  aiActivity: [
    { sessionId: 5, title: 'DFD 加工命名探讨', userMessageCount: 4, updatedAt: '2026-11-25T09:30:00' },
  ],
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.listAdminUsers.mockResolvedValue({ items: sampleUsers, total: 2, page: 0, size: 20 })
  mocks.createAdminUser.mockResolvedValue({ ...sampleUsers[0], id: 4, username: 'student02', displayName: '李同学' })
  mocks.updateAdminUser.mockResolvedValue({ ...sampleUsers[0], displayName: '张改名' })
  mocks.setAdminUserEnabled.mockResolvedValue({ ...sampleUsers[0], enabled: false, activeTokenCount: 0 })
  mocks.resetAdminUserPassword.mockResolvedValue(undefined)
  mocks.revokeAdminUserTokens.mockResolvedValue({ revokedCount: 1 })
  mocks.getAdminUserProfile.mockResolvedValue(sampleProfile)
})

afterEach(() => {
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

describe('AdminUserManagementView', () => {
  it('renders user directory, uses in-page confirm modal instead of window.confirm, and updates row without unmounting table', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const wrapper = mount(AdminUserManagementView, { attachTo: document.body })
    await flushPromises()

    expect(mocks.listAdminUsers).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20, role: 'ALL' }))
    expect(wrapper.text()).toContain('张同学')
    expect(wrapper.text()).toContain('@student01')
    expect(wrapper.text()).toContain('当前账号')

    const selfDisableBtn = wrapper.get('[data-testid="user-row-3"] button[aria-label="停用账号"]')
    expect(selfDisableBtn.attributes('disabled')).toBeDefined()

    const initialTableEl = wrapper.get('table.user-table').element

    // 点击停用触发页内确认弹窗，绝不弹出浏览器 window.confirm
    await wrapper.get('[data-testid="user-row-1"] button[aria-label="停用账号"]').trigger('click')
    await flushPromises()
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(mocks.setAdminUserEnabled).not.toHaveBeenCalled()

    const confirmDialog = document.body.querySelector('[data-testid="user-confirm-dialog"]')
    expect(confirmDialog).not.toBeNull()
    expect(confirmDialog?.textContent).toContain('停用用户账号')

    ;(document.body.querySelector('[data-testid="confirm-dialog-submit"]') as HTMLButtonElement).click()
    await flushPromises()

    expect(mocks.setAdminUserEnabled).toHaveBeenCalledWith(1, false)
    // 原地更新行数据，不重建整张表 DOM（消除闪烁）
    expect(wrapper.get('table.user-table').element).toBe(initialTableEl)
    expect(wrapper.get('[data-testid="user-row-1"]').text()).toContain('已停用')
    expect(document.body.querySelector('[data-testid="user-toast"]')?.textContent).toContain('已停用')
    wrapper.unmount()
  })

  it('opens the privacy-whitelisted independent user profile drawer and switches tabs', async () => {
    const wrapper = mount(AdminUserManagementView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-testid="user-profile-btn-1"]').trigger('click')
    await flushPromises()

    expect(mocks.getAdminUserProfile).toHaveBeenCalledWith(1)
    const drawer = document.body.querySelector('[data-testid="user-profile-drawer"]')
    expect(drawer).not.toBeNull()
    expect(drawer?.textContent).toContain('白名单学情视图')
    expect(drawer?.textContent).toContain('需求分析对照任务')

    const tabs = Array.from(drawer!.querySelectorAll('[role="tab"]')) as HTMLButtonElement[]
    tabs[1].click()
    await flushPromises()
    expect(drawer?.textContent).toContain('数据流图 DFD')
    expect(drawer?.textContent).toContain('48%')

    tabs[2].click()
    await flushPromises()
    expect(drawer?.textContent).toContain('数据流图中的加工应该如何规范命名？')

    tabs[3].click()
    await flushPromises()
    expect(drawer?.textContent).toContain('DFD 加工命名探讨')

    const closeBtn = drawer!.querySelector('button[aria-label="关闭用户画像"]') as HTMLButtonElement
    closeBtn.click()
    await flushPromises()
    expect(document.body.querySelector('[data-testid="user-profile-drawer"]')).toBeNull()
    wrapper.unmount()
  })

  it('validates and submits new user creation form', async () => {
    const wrapper = mount(AdminUserManagementView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-testid="create-user-btn"]').trigger('click')
    await flushPromises()

    const dialog = document.body.querySelector('[role="dialog"]')!
    const inputs = dialog.querySelectorAll('input')
    await wrapper.vm.$nextTick()

    ;(inputs[0] as HTMLInputElement).value = 'student_new'
    inputs[0].dispatchEvent(new Event('input'))
    ;(inputs[1] as HTMLInputElement).value = '新同学'
    inputs[1].dispatchEvent(new Event('input'))
    ;(inputs[2] as HTMLInputElement).value = 'Pass123456'
    inputs[2].dispatchEvent(new Event('input'))
    ;(inputs[3] as HTMLInputElement).value = 'Pass123456'
    inputs[3].dispatchEvent(new Event('input'))

    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(mocks.createAdminUser).toHaveBeenCalledWith({
      username: 'student_new',
      displayName: '新同学',
      role: 'STUDENT',
      password: 'Pass123456',
    })
    wrapper.unmount()
  })
})
