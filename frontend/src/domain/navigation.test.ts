import { describe, expect, it } from 'vitest'
import {
  ADMIN_SECTIONS,
  findAdminGroupByPath,
  isNavSubItemActive,
  resolveSubItemTo,
} from './navigation'

describe('admin navigation domain helpers', () => {
  it('maps admin canonical routes and aliases to two-level group ids', () => {
    expect(findAdminGroupByPath('/teaching-dashboard')).toBe('admin-teaching')
    expect(findAdminGroupByPath('/admin/users')).toBe('admin-teaching')
    expect(findAdminGroupByPath('/admin/content')).toBe('admin-content')
    expect(findAdminGroupByPath('/admin/experiments')).toBe('admin-experiments')
    expect(findAdminGroupByPath('/experiments')).toBe('admin-experiments')
    expect(findAdminGroupByPath('/dashboard')).toBeNull()
  })

  it('matches active admin subitems accurately with default and explicit tab queries', () => {
    const contentItems = ADMIN_SECTIONS['admin-content']
    expect(isNavSubItemActive(contentItems[0], '/admin/content', {})).toBe(true)
    expect(isNavSubItemActive(contentItems[1], '/admin/content', {})).toBe(false)
    expect(isNavSubItemActive(contentItems[1], '/admin/content', { tab: 'question-bank' })).toBe(true)
    expect(isNavSubItemActive(contentItems[2], '/admin/content', { tab: 'diagnosis' })).toBe(true)
    expect(isNavSubItemActive(contentItems[0], '/admin/content', { tab: 'unknown' })).toBe(true)

    const expItems = ADMIN_SECTIONS['admin-experiments']
    expect(isNavSubItemActive(expItems[0], '/admin/experiments', {})).toBe(true)
    expect(isNavSubItemActive(expItems[2], '/admin/experiments', { tab: 'evaluation' })).toBe(true)
    expect(isNavSubItemActive(expItems[1], '/admin/content', { tab: 'tasks' })).toBe(false)
  })

  it('resolves tab-specific RouterLink targets cleanly', () => {
    const diagnosisItem = ADMIN_SECTIONS['admin-content'][2]
    expect(resolveSubItemTo(diagnosisItem)).toEqual({
      path: '/admin/content',
      query: { tab: 'diagnosis' },
    })
  })
})
