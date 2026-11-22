import { access, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { preview } from 'vite'

const root = fileURLToPath(new URL('../', import.meta.url))
const runName = process.env.UI_REVIEW_RUN || 'latest'
if (!/^[a-z0-9-]+$/.test(runName)) throw new Error('UI_REVIEW_RUN must be a lowercase label')
const auditDir = path.join(root, '.ui-visual-review', runName)
const routes = [
  '/login', '/', '/dashboard', '/courses', '/admin', '/teaching-dashboard', '/admin/content', '/admin/experiments', '/admin/teaching-dashboard', '/admin/question-bank',
  '/admin/micro-lessons', '/admin/diagnostics', '/knowledge?entity=%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90',
  '/mastery-map', '/tutor', '/practice', '/adaptive-practice', '/wrong-book', '/review', '/study-plan',
  '/micro-lessons', '/evaluation', '/learning-report', '/admin/evaluations', '/recommendations',
  '/experiments', '/experiment-tasks', '/admin/experiment-tasks',
]
const roles = ['STUDENT', 'ADMIN', 'GUEST']
const adminRoute = path => path === '/experiments' || path === '/teaching-dashboard' || path.startsWith('/admin')
function expectedPath(role, requested) {
  const path = requested.split('?')[0]
  if (role === 'GUEST') return '/login'
  if (path === '/login' || path === '/') return role === 'ADMIN' ? '/teaching-dashboard' : '/dashboard'
  if (role === 'ADMIN' && !adminRoute(path)) return '/teaching-dashboard'
  if (role === 'STUDENT' && adminRoute(path)) return '/dashboard'
  if (path === '/admin' || path === '/admin/teaching-dashboard') return '/teaching-dashboard'
  if (['/admin/question-bank', '/admin/micro-lessons', '/admin/diagnostics'].includes(path)) return '/admin/content'
  if (['/admin/evaluations', '/admin/experiment-tasks'].includes(path)) return '/admin/experiments'
  return path
}

const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })

function apiResponse(url, role, scenario) {
  const path = new URL(url).pathname
  if (path === '/api/auth/me') return json({ id: role === 'ADMIN' ? 1 : 2, username: role === 'ADMIN' ? 'smoke-admin' : 'smoke-student', displayName: role === 'ADMIN' ? '封版巡检管理员' : '封版巡检学生', role })
  if (path === '/api/dashboard/overview') return json({ mastery: 42, completed: 4, wrongCount: 2, accuracy: 75, activity: [] })
  if (path === '/api/knowledge/points') return json([{ id: 11, courseId: 3, chapterId: 1, name: '需求分析', graphEntity: '需求分析', mastery: 42, sortOrder: 1 }])
  if (path === '/api/courses') return json([{ id: 3, code: 'SE', name: '软件工程', term: '2026 秋季', description: '课程阅读与练习' }])
  if (path === '/api/admin/courses') return json([{ id: 3, code: 'SE', name: '软件工程', term: '2026 秋季', description: '课程阅读与练习', graphSpaceId: 1, enabled: true }])
  if (path === '/api/admin/teaching-dashboard') {
    const empty = scenario === 'teaching-empty'
    return json({
      from: '2026-11-15', to: '2026-11-21', days: 7, courseId: 3, courseName: '软件工程',
      overview: { activeLearners: empty ? 0 : 12, totalSections: 8, completedReads: empty ? 0 : 6, attempts: empty ? 0 : 40, correct: empty ? 0 : 30, openWrong: empty ? 0 : 8, averageMastery: empty ? 0 : 64 },
      dailyActivity: empty ? [] : [3, 8, 5, 9, 7, 8].map((attempts, index) => ({ date: `2026-11-${15 + index}`, activeLearners: index + 3, attempts, correct: Math.floor(attempts * .8), completedReads: index % 3 })),
      weakPoints: empty ? [] : ['需求规格说明书', '数据流图', '模块划分', '黑盒测试'].map((pointName, index) => ({ pointId: index + 11, pointName, averageMastery: 35 + index * 9, attempts: 8, learners: 5 })),
    })
  }
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

await access(path.join(root, 'dist/index.html')).catch(() => { throw new Error('Run npm run build before the visual smoke test') })
const server = await preview({ root, configFile: path.join(root, 'vite.config.ts'), preview: { host: '127.0.0.1', port: 0, strictPort: true, proxy: {} } })
const visualScenarios = {
  STUDENT: [],
  ADMIN: [{ route: '/teaching-dashboard', name: 'teaching-empty', check: page => page.getByText('当前范围内暂无学习活动数据。').waitFor() }],
  GUEST: [],
}
let browser
try {
  await mkdir(auditDir, { recursive: true })
  const address = server.httpServer.address()
  if (!address || typeof address === 'string') throw new Error('Vite preview is not listening on TCP')
  const baseUrl = `http://127.0.0.1:${address.port}`
  browser = await chromium.launch({ headless: true })
  const results = []
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 800 }]) {
    for (const role of roles) {
    const context = await browser.newContext({ viewport })
    const page = await context.newPage()
    const consoleErrors = []
    const pageErrors = []
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()) })
    page.on('pageerror', error => pageErrors.push(error.message))
    let scenario = ''
    await page.route('**/api/**', route => route.fulfill(apiResponse(route.request().url(), role, scenario)))
    await page.addInitScript((role) => {
      if (role === 'GUEST') return
      localStorage.setItem('slp-access-token', 'ui-smoke-access-token')
      localStorage.setItem('slp-user-name', '封版巡检')
      localStorage.setItem('slp-user-role', role)
    }, role)
    const visits = (role === 'GUEST' ? ['/login'] : routes).map(route => ({ route, name: '', check: null }))
    for (const { route, name, check } of [...visits, ...visualScenarios[role]]) {
      scenario = name
      await page.goto(baseUrl + route, { waitUntil: 'networkidle' })
      await check?.(page)
      await page.evaluate(async () => { await document.fonts?.ready })
      const metrics = await page.evaluate(() => {
        const visible = element => element instanceof HTMLElement && element.getClientRects().length > 0 && getComputedStyle(element).visibility === 'visible'
        const buttons = [...document.querySelectorAll('button')].filter(visible)
        const controls = [...document.querySelectorAll('button,input,select,textarea')].filter(visible)
        const defaultControls = controls.filter(element => {
          if (element instanceof HTMLInputElement && (element.type === 'checkbox' || element.type === 'radio')) return false
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
        const content = document.querySelector('.page-content, .login-page')
        const pageHeader = content?.querySelector('h1')
        const pageDescription = content?.querySelector('.section-description, .page-heading p:not(.eyebrow), .login-subtitle')
        const loginButton = document.querySelector('.login-submit')
        const luminance = value => value.match(/[\d.]+/g)?.slice(0, 3).map(Number).map(channel => {
          const normalized = channel / 255
          return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
        }).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
        const loginStyle = loginButton ? getComputedStyle(loginButton) : null
        const loginContrast = loginStyle ? (() => {
          const [dark, light] = [luminance(loginStyle.color), luminance(loginStyle.backgroundColor)].sort((a, b) => a - b)
          return (light + 0.05) / (dark + 0.05)
        })() : null
        const primaryActions = buttons.filter(button => button.classList.contains('button-dark') || button.classList.contains('button-primary') || button.getAttribute('type') === 'submit')
        const contentRect = content?.getBoundingClientRect()
        const contentStyle = content ? getComputedStyle(content) : null
        const textSample = [...(content?.querySelectorAll('h1,h2,p,label') || [])]
          .filter(element => visible(element) && element.textContent.trim())
          .slice(0, 12)
          .map(element => {
            const rect = element.getBoundingClientRect()
            const style = getComputedStyle(element)
            return { tag: element.tagName.toLowerCase(), text: element.textContent.trim().slice(0, 72), width: Math.round(rect.width), height: Math.round(rect.height), fontSize: style.fontSize, lineHeight: style.lineHeight, color: style.color }
          })
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
          contentWidth: contentRect ? Math.round(contentRect.width) : 0,
          contentPadding: contentStyle ? `${contentStyle.paddingTop} ${contentStyle.paddingRight} ${contentStyle.paddingBottom} ${contentStyle.paddingLeft}` : '',
          primaryActions: primaryActions.length,
          visibleButtons: buttons.length,
          textSample,
          loginTitle: document.querySelector('.login-intro-copy h1')?.textContent.trim() || '',
          loginButtonColor: document.querySelector('.login-submit') ? getComputedStyle(document.querySelector('.login-submit')).color : '',
          loginButtonBackground: document.querySelector('.login-submit') ? getComputedStyle(document.querySelector('.login-submit')).backgroundColor : '',
          loginFieldBorder: document.querySelector('.login-field > div') ? getComputedStyle(document.querySelector('.login-field > div')).borderColor : '',
          loginContrast,
        }
      })
      const safeName = route.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'root'
      const screenshot = `${role}-${viewport.width}x${viewport.height}-${safeName}${name ? `-${name}` : ''}.png`
      await page.screenshot({ path: path.join(auditDir, screenshot), fullPage: true, animations: 'disabled' })
      results.push({ ...metrics, role, viewport: `${viewport.width}x${viewport.height}`, requested: route, scenario, final: metrics.path, roleRedirectPass: metrics.path === expectedPath(role, route), overflow: metrics.scrollWidth > metrics.viewportWidth + 1, blankRoot: metrics.rootChildren === 0, topbarOverflow: metrics.topbarWidth > metrics.viewportWidth + 1, topbarOverlap: metrics.topbarBottom > metrics.contentTop + 1, screenshot, consoleErrors: [...consoleErrors], pageErrors: [...pageErrors] })
      consoleErrors.length = 0
      pageErrors.length = 0
    }
    await context.close()
    }
  }
  const failures = results.filter(item => !item.roleRedirectPass || item.overflow || item.blankRoot || item.topbarOverflow || item.topbarOverlap || item.squeezedButtons || item.defaultControls || item.unstyledTables || !item.pageHeader || !item.pageDescription || item.consoleErrors.length || item.pageErrors.length || (item.role === 'GUEST' && !(item.loginContrast >= 4.5)))
  const reportPath = path.join(auditDir, 'metrics.json')
  await writeFile(reportPath, JSON.stringify({ scope: 'Browser rendering with intercepted API fixtures, not live backend integration', total: results.length, failures, results }, null, 2), 'utf8')
  console.log(`UI smoke: ${results.length - failures.length}/${results.length} passed. ${reportPath}`)
  for (const item of failures) console.error(`${item.role} ${item.viewport} ${item.requested}: ${item.screenshot}`)
  if (failures.length) process.exitCode = 1
} finally {
  try { await browser?.close() } finally { await server.close() }
}
