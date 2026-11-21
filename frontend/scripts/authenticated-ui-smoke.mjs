import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

const port = 4174
const baseUrl = `http://127.0.0.1:${port}`
const auditDir = path.resolve(process.cwd(), '.ui-audit')
const routes = [
  '/login', '/', '/courses', '/admin', '/admin/teaching-dashboard', '/admin/question-bank',
  '/admin/micro-lessons', '/admin/diagnostics', '/knowledge?entity=%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90',
  '/mastery-map', '/tutor', '/practice', '/adaptive-practice', '/wrong-book', '/review', '/study-plan',
  '/micro-lessons', '/evaluation', '/learning-report', '/admin/evaluations', '/recommendations',
  '/experiments', '/experiment-tasks', '/admin/experiment-tasks',
]

const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })

function apiResponse(url) {
  const path = new URL(url).pathname
  if (path === '/api/auth/me') return json({ id: 1, username: 'smoke-admin', displayName: '封版巡检管理员', role: 'ADMIN' })
  if (path === '/api/dashboard/overview') return json({ mastery: 42, completed: 4, wrongCount: 2, accuracy: 75, activity: [] })
  if (path === '/api/knowledge/points') return json([{ id: 11, courseId: 3, chapterId: 1, name: '需求分析', graphEntity: '需求分析', mastery: 42, sortOrder: 1 }])
  if (path === '/api/courses') return json([{ id: 3, code: 'SE', name: '软件工程', term: '2026 秋季', description: '课程阅读与练习' }])
  if (path === '/api/admin/courses') return json([{ id: 3, code: 'SE', name: '软件工程', term: '2026 秋季', description: '课程阅读与练习', graphSpaceId: 1, enabled: true }])
  if (path === '/api/admin/teaching-dashboard') return json({ from: '2026-11-15', to: '2026-11-21', days: 7, courseId: 3, courseName: '软件工程', overview: { activeLearners: 1, totalSections: 8, completedReads: 3, attempts: 4, correct: 3, openWrong: 2, averageMastery: 42 }, dailyActivity: [], weakPoints: [] })
  if (path === '/api/admin/question-bank/versions') return json([])
  if (path === '/api/admin/micro-lessons') return json([])
  if (path === '/api/admin/diagnostics') return json([])
  if (path.includes('/admin/courses/') && path.endsWith('/prerequisite-quality')) return json({ courseId: 3, pointCount: 1, validEdgeCount: 0, errorCount: 0, warningCount: 0, valid: true, issues: [] })
  if (path === '/api/admin/knowledge-points' || path === '/api/admin/questions') return json([])
  if (path.startsWith('/api/admin/questions/')) return json([])
  if (path.endsWith('/study')) return json({ course: { id: 3, name: '软件工程' }, chapters: [], sections: [] })
  if (path === '/api/knowledge/graph') return json({ available: true, nodes: [{ id: '需求分析', label: '需求分析' }, { id: '需求规格说明书', label: '需求规格说明书' }], edges: [{ source: '需求分析', target: '需求规格说明书', label: '输出' }], evidence: [{ subject: '需求分析', predicate: '输出', object: '需求规格说明书', score: 0.9 }] })
  if (path.includes('/knowledge/learning-path')) return json({ targetId: 11, courseId: 3, nodes: [], cycleDetected: false })
  if (path.includes('/knowledge/mastery-map')) return json({ courseId: 3, courseName: '软件工程', nodes: [], edges: [] })
  if (path === '/api/adaptive-practice') return json([])
  if (path === '/api/recommendations') return json([{ id: 11, knowledgePointId: 11, title: '复习需求分析', reason: '基于掌握度、错题与先修关系的可解释规则推荐。', kind: '薄弱点巩固', score: 0.8, knowledgePoint: '需求分析' }])
  if (path === '/api/wrong-book') return json([{ id: 21, questionId: 101, question: '加工如何命名？', knowledgePoint: '数据流图', wrongCount: 2, status: 'UNMASTERED' }])
  if (path.startsWith('/api/quizzes')) return json([])
  if (path.startsWith('/api/spaced-reviews')) return json([])
  if (path.startsWith('/api/study-plans')) return json({ id: 1, date: '2026-11-21', budgetMinutes: 30, tasks: [] })
  if (path === '/api/reports/learning') return json({ from: '2026-11-15', to: '2026-11-21', days: 7, courseName: '软件工程', reading: { totalSections: 8, completedSections: 3, completedInPeriod: 0 }, practice: { attempts: 4, correct: 3, accuracy: 75 }, reviews: { reviews: 0, correct: 0, accuracy: 0 }, plans: { totalTasks: 0, completedTasks: 0, plannedMinutes: 0, completedMinutes: 0 }, dailyActivity: [], chapters: [], knowledge: [] })
  if (path === '/api/experiments') return json([])
  if (path.startsWith('/api/experiment-tasks')) return json([])
  if (path.startsWith('/api/evaluations/sessions')) return json([])
  if (path === '/api/admin/evaluations/summary') return json([{ conditionKey: 'METHODS', sampleCount: 3, meanPreScore: 3, medianPreScore: 3, meanPostScore: 4, medianPostScore: 4, meanEffort: 4, medianEffort: 4, feedbackCount: 2 }])
  if (path === '/api/admin/evaluations/export') return { status: 200, contentType: 'text/csv', body: 'participant,condition\nanon-001,METHODS\n' }
  if (path === '/api/tutor/sessions') return json([])
  if (path.startsWith('/api/learning/notes')) return json([])
  if (path.startsWith('/api/learning/bookmarks')) return json({ bookmarked: false })
  if (path === '/api/ai/dependencies') return json({ configured: true, pythonKnowledgeStatus: 'UP', rankedModelEnabled: false, rankedModelConfigured: false, rankedModelStatus: 'DISABLED' })
  if (path === '/api/system/status') return json({ checkedAt: new Date().toISOString(), dependencies: [{ key: 'mysql', label: 'MySQL', status: 'UP', impact: '' }, { key: 'python', label: 'Python Bridge', status: 'UP', impact: '' }] })
  if (path.startsWith('/api/ai/')) return json({ answer: '演示回答', mode: 'DIRECT', hasEvidence: true, triples: [], entities: [], error: null })
  return json({})
}

async function waitForServer() {
  for (let attempt = 0; attempt < 50; attempt++) {
    try { await fetch(baseUrl); return } catch { await new Promise(resolve => setTimeout(resolve, 100)) }
  }
  throw new Error('vite preview did not start')
}

const preview = spawn('cmd.exe', ['/d', '/s', '/c', `npm run preview -- --host 127.0.0.1 --port ${port}`], { cwd: process.cwd(), windowsHide: true, stdio: 'ignore' })
try {
  await mkdir(auditDir, { recursive: true })
  await waitForServer()
  const browser = await chromium.launch({ headless: true })
  const results = []
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 800 }]) {
    const context = await browser.newContext({ viewport })
    const page = await context.newPage()
    const consoleErrors = []
    const pageErrors = []
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()) })
    page.on('pageerror', error => pageErrors.push(error.message))
    await page.route('**/api/**', route => route.fulfill(apiResponse(route.request().url())))
    await page.addInitScript(() => {
      localStorage.setItem('slp-access-token', 'ui-smoke-access-token')
      localStorage.setItem('slp-user-name', '封版巡检管理员')
    })
    for (const route of routes) {
      await page.goto(baseUrl + route, { waitUntil: 'networkidle' })
      const metrics = await page.evaluate(() => {
        const visible = (element) => element instanceof HTMLElement && element.offsetParent !== null
        const buttons = [...document.querySelectorAll('button')].filter(visible)
        const controls = [...document.querySelectorAll('button,input,select,textarea')].filter(visible)
        const ignoredButtons = new Set(['icon-button', 'nav-item', 'nav-group-toggle', 'course-switch', 'user-menu', 'system-status-button', 'help-quick', 'text-button'])
        const defaultControls = controls.filter(element => {
          if (element instanceof HTMLInputElement && (element.type === 'checkbox' || element.type === 'radio')) return false
          if (element.tagName === 'BUTTON' && [...element.classList].some(name => ignoredButtons.has(name) || name.startsWith('button'))) return false
          const style = getComputedStyle(element)
          const radius = parseFloat(style.borderTopLeftRadius || '0')
          const padding = parseFloat(style.paddingLeft || '0') + parseFloat(style.paddingRight || '0')
          return style.borderTopStyle === 'inset' || style.borderTopStyle === 'outset' || (radius < 3 && padding < 6 && (style.borderTopColor === 'rgb(118, 118, 118)' || style.borderTopColor === 'rgb(0, 0, 0)'))
        })
        const unstyledTables = [...document.querySelectorAll('table')].filter(table => visible(table)).filter(table => {
          const cell = table.querySelector('th,td')
          if (!cell) return false
          const style = getComputedStyle(cell)
          return !table.querySelector('th') || parseFloat(style.paddingTop || '0') < 6 || getComputedStyle(table).borderTopStyle === 'none'
        })
        const cardDepth = [...document.querySelectorAll('.page-content *')].reduce((max, element) => {
          const style = getComputedStyle(element)
          if (style.backgroundColor !== 'rgb(255, 255, 255)' && style.backgroundColor !== 'rgba(255, 255, 255, 1)') return max
          let depth = 0
          let parent = element.parentElement
          while (parent) { const parentStyle = getComputedStyle(parent); if (parentStyle.backgroundColor === 'rgb(255, 255, 255)' && parentStyle.borderTopStyle !== 'none') depth++; parent = parent.parentElement }
          return Math.max(max, depth)
        }, 0)
        const pageHeader = document.querySelector('.page-content h1, .page-content .page-heading h1, .page-content .section-heading h1')
        const pageDescription = document.querySelector('.page-content .section-description, .page-content .page-heading p:not(.eyebrow)')
        return {
          path: location.pathname,
          scrollWidth: document.documentElement.scrollWidth,
          viewportWidth: innerWidth,
          rootChildren: document.querySelector('#app')?.children.length ?? 0,
          topbarWidth: document.querySelector('.topbar')?.getBoundingClientRect().width ?? 0,
          contentTop: document.querySelector('.page-content')?.getBoundingClientRect().top ?? 0,
          topbarBottom: document.querySelector('.topbar')?.getBoundingClientRect().bottom ?? 0,
          squeezedButtons: buttons.filter(button => button.scrollWidth > button.clientWidth + 1).length,
          squeezedButtonDetails: buttons.filter(button => button.scrollWidth > button.clientWidth + 1).map(button => ({ text: button.textContent.trim(), title: button.getAttribute('title'), aria: button.getAttribute('aria-label'), width: button.getBoundingClientRect().width, clientWidth: button.clientWidth, scrollWidth: button.scrollWidth })),
          defaultControls: defaultControls.length,
          unstyledTables: unstyledTables.length,
          cardDepth,
          pageHeader: Boolean(pageHeader),
          pageDescription: Boolean(pageDescription),
          headingFontSize: pageHeader ? getComputedStyle(pageHeader).fontSize : '',
        }
      })
      const safeName = route.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'dashboard'
      await page.screenshot({ path: path.join(auditDir, `${viewport.width}x${viewport.height}-${safeName}.png`), fullPage: false })
      results.push({ viewport: `${viewport.width}x${viewport.height}`, requested: route, final: metrics.path, overflow: metrics.scrollWidth > metrics.viewportWidth + 1, blankRoot: metrics.rootChildren === 0, topbarOverflow: metrics.topbarWidth > metrics.viewportWidth + 1, topbarOverlap: metrics.topbarBottom > metrics.contentTop + 1, squeezedButtons: metrics.squeezedButtons, squeezedButtonDetails: metrics.squeezedButtonDetails, defaultControls: metrics.defaultControls, unstyledTables: metrics.unstyledTables, cardDepth: metrics.cardDepth, pageHeader: metrics.pageHeader, pageDescription: metrics.pageDescription, headingFontSize: metrics.headingFontSize, consoleErrors: [...consoleErrors], pageErrors: [...pageErrors] })
      consoleErrors.length = 0
      pageErrors.length = 0
    }
    await context.close()
  }
  await browser.close()
  const report = { scope: 'authenticated frontend rendering only; does not replace full-stack backend or graph integration', total: results.length, failures: results.filter(item => item.overflow || item.blankRoot || item.topbarOverflow || item.topbarOverlap || item.squeezedButtons || item.defaultControls || item.unstyledTables || !item.pageHeader || !item.pageDescription || item.consoleErrors.length || item.pageErrors.length), results }
  await writeFile(path.join(auditDir, 'metrics.json'), JSON.stringify(report, null, 2), 'utf8')
  console.log(JSON.stringify(report, null, 2))
} finally {
  preview.kill()
}
