import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { isEditableShortcutContext } from '../domain/shortcuts'
import KbdBadge from './KbdBadge.vue'
import KeyboardShortcutsModal from './KeyboardShortcutsModal.vue'

afterEach(() => {
  document.body.innerHTML = ''
  document.body.style.overflow = ''
})

describe('KbdBadge and KeyboardShortcutsModal', () => {
  it('renders joined keycaps and supports slot fallback', () => {
    const joined = mount(KbdBadge, { props: { keys: ['Ctrl', 'K'] } })
    expect(joined.findAll('kbd.kbd-badge').map((n) => n.text())).toEqual(['Ctrl', 'K'])
    expect(joined.text()).toContain('+')
    joined.unmount()

    const single = mount(KbdBadge, { slots: { default: 'Space' } })
    expect(single.get('kbd.kbd-badge').text()).toBe('Space')
    single.unmount()
  })

  it('guards editable controls and IME states from triggering ?', () => {
    const input = document.createElement('input')
    const textarea = document.createElement('textarea')
    const editableDiv = document.createElement('div')
    editableDiv.setAttribute('contenteditable', 'true')
    const plainDiv = document.createElement('div')
    document.body.append(input, textarea, editableDiv, plainDiv)

    const makeEvent = (target: EventTarget, extra: Partial<KeyboardEvent> = {}) =>
      ({
        key: '?',
        isComposing: false,
        keyCode: 191,
        ctrlKey: false,
        metaKey: false,
        altKey: false,
        target,
        ...extra,
      }) as KeyboardEvent

    input.focus()
    expect(isEditableShortcutContext(makeEvent(input))).toBe(true)
    textarea.focus()
    expect(isEditableShortcutContext(makeEvent(textarea))).toBe(true)
    editableDiv.focus()
    expect(isEditableShortcutContext(makeEvent(editableDiv))).toBe(true)
    plainDiv.tabIndex = 0
    plainDiv.focus()
    expect(isEditableShortcutContext(makeEvent(plainDiv, { isComposing: true }))).toBe(true)
    expect(isEditableShortcutContext(makeEvent(plainDiv))).toBe(false)
  })

  it('shows role-appropriate shortcuts and closes on Escape restoring body overflow', async () => {
    const wrapper = mount(KeyboardShortcutsModal, {
      attachTo: document.body,
      props: { open: false, role: 'STUDENT' },
    })

    await wrapper.setProps({ open: true })
    await flushPromises()

    const dialog = document.body.querySelector('[data-testid="shortcuts-help-dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog!.textContent).toContain('概念闪卡主动回忆')
    expect(dialog!.textContent).toContain('AI 学习助教')
    expect(document.body.style.overflow).toBe('hidden')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])

    await wrapper.setProps({ open: false, role: 'ADMIN' })
    await wrapper.setProps({ open: true })
    await flushPromises()
    const adminDialog = document.body.querySelector('[data-testid="shortcuts-help-dialog"]')
    expect(adminDialog!.textContent).toContain('全局导航与界面控制')
    expect(adminDialog!.textContent).not.toContain('概念闪卡主动回忆')

    await wrapper.setProps({ open: false })
    await flushPromises()
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })
})
