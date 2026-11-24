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
  '/experiments', '/experiment-tasks', '/experiment-run', '/admin/experiment-tasks',
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

const course = { id: 3, code: 'SE', name: '软件工程', term: '2026 秋季', description: '从需求到测试，逐章阅读并用练习核验理解。' }
const courseSections = ['需求分析', '数据流图', '需求规格说明书'].map((title, index) => ({
  id: 31 + index, title, knowledgePointId: 11 + index, knowledgePointName: title, graphEntity: title,
  completed: index === 0,
  objectives: ['区分系统边界、业务目标与约束。', '用明确、可验证的语言描述需求。'],
  content: [
    { heading: '从问题域出发', paragraphs: ['需求分析把用户的目标、所处的环境和现有约束整理为可以沟通的说明。先把业务目标和候选解决方案分开，再确认系统负责什么、哪些工作仍由使用者完成。', '例如，在图书借阅场景里，先描述借阅者、库存与归还规则，再讨论由哪个界面或服务实现这些步骤。'] },
    { heading: '核验需求', paragraphs: ['为每项需求写出可观察的通过条件，并记录尚未解决的问题。评审时对照这些条件检查歧义与遗漏。'] },
  ],
  resources: [{ title: '需求建模参考', url: 'https://example.org/requirements', description: '对比目标、约束与验收条件。' }],
}))
const coursePoints = courseSections.map((section, index) => ({ id: section.knowledgePointId, courseId: 3, chapterId: 1, name: section.title, graphEntity: section.graphEntity, mastery: [85, 42, 0][index], sortOrder: index + 1 }))
const evidence = [
  { subject: '需求分析', predicate: '输出', object: '需求规格说明书', score: 0.92 },
  { subject: '需求分析', predicate: '使用', object: '数据流图', score: 0.81 },
]
const graphNodes = coursePoints.map(point => ({ id: point.graphEntity, label: point.name, type: 'knowledge' }))
const pathNodes = coursePoints.map((point, index) => ({ ...point, status: ['MASTERED', 'IN_PROGRESS', 'BLOCKED'][index], prerequisiteIds: index ? [point.id - 1] : [] }))
const chatQuestion = '软件需求规格说明书应该包含哪些内容？'
const chatAnswer = '## 从边界开始\n\n先说明目标、业务边界和参与者，再分别写出功能需求与约束。最后为每项需求补上可验证的验收条件。\n\n- 功能：记录借阅申请\n- 约束：库存不足时说明原因\n\n```text\n条件：库存大于 0\n预期：生成借阅记录\n```'
const quiz = { id: 101, stem: '在数据流图中，表示数据变换的加工通常怎样命名？', knowledgePoint: '数据流图', type: 'SINGLE', options: [
  { key: 'A', text: '用动词与宾语描述对数据执行的操作' },
  { key: 'B', text: '用名词短语写出保存的数据对象' },
  { key: 'C', text: '用系统名称代替加工名称' },
  { key: 'D', text: '只记录数据流的编号' },
] }
const wrongItems = [
  { id: 21, questionId: 101, question: quiz.stem, knowledgePoint: '数据流图', wrongCount: 2, status: 'UNMASTERED' },
  { id: 22, questionId: 102, question: '需求规格说明书中，怎样区分业务目标和验收条件？', knowledgePoint: '需求分析', wrongCount: 1, status: 'UNMASTERED' },
]
const reviewCard = { wrongQuestionId: 21, questionId: quiz.id, question: quiz.stem, knowledgePoint: quiz.knowledgePoint, questionType: quiz.type, options: quiz.options, wrongCount: 2, dueAt: '2026-11-21T08:00:00', previousIntervalMinutes: 1440 }
const planTasks = [
  { id: 1, type: 'REVIEW', targetId: 21, title: '复习数据流图的加工命名', minutes: 8, path: '/review?wrongQuestionId=21', state: 'PENDING', detail: '到期复习 · 数据流图', reason: '这道错题已到复习时间。' },
  { id: 2, type: 'READING', targetId: 32, title: '阅读数据流图', minutes: 12, path: '/courses?courseId=3&sectionId=32', state: 'PENDING', detail: '软件工程 · 软件需求', reason: '该小节尚未完成阅读。' },
  { id: 3, type: 'PRACTICE', targetId: 11, title: '练习写出验收条件', minutes: 10, path: '/practice?knowledgePointId=11', state: 'COMPLETED', detail: '课程练习 · 需求分析', reason: '结合近期答题记录安排。' },
]
const ablationKeys = ['ABLATION_SORTED_SCORED', 'ABLATION_WITHOUT_SCORE', 'ABLATION_WITHOUT_SORT', 'ABLATION_WITHOUT_SORT_AND_SCORE']
const experimentRun = {
  id: 'layout-fixture-methods', question: quiz.stem, createdAt: '2026-11-21T09:30:00Z', ratings: { DIRECT: 3, TRIPLES: 4 },
  results: [
    { mode: 'DIRECT', latencyMs: 620, answer: '加工名称使用动宾短语。' },
    { mode: 'TRIPLES', latencyMs: 1030, answer: '加工名称应说明数据如何转换。用动词加宾语描述操作，例如“校验借阅资格”，不要用保存的数据对象代替加工。', triples: evidence },
    { mode: 'RANKED', latencyMs: 1620, answer: '先识别加工的输入与输出，再用动词与宾语命名，如“生成借阅记录”。这样的名称比“借阅数据”更容易与数据存储区分。', triples: evidence, provenance: { systemPromptVersion: 'ranked-grounded-v3' }, grounding: { selectedIds: ['1'], candidates: [1, 2], modelCallCount: 2 } },
    ...ablationKeys.map((resultKey, index) => ({ mode: resultKey, resultKey, latencyMs: 1180 + index * 140, ...(index === 3 ? { error: '模型服务超时，未生成回答。' } : { answer: '用动宾短语描述数据变换。', triples: evidence }) })),
  ],
  evaluation: { source: 'SERVER_AI_SERVICE', experimentType: 'METHODS', note: '界面巡检夹具，不是实际实验或研究结果。', metricsByMode: { DIRECT: { rougeL: .54, bleu4: .28, bleuOrder: 4, entityRecall: .5 }, TRIPLES: { rougeL: .71, bleu4: .46, bleuOrder: 4, entityRecall: 1 }, RANKED: { rougeL: .68, bleu4: .43, bleuOrder: 4, entityRecall: 1 } } },
}
const experimentTask = { id: 9, title: '对照观察加工命名的解释', description: '阅读知情说明后，依次记录任务前后的测验结果。', question: quiz.stem, referenceAnswer: '以动宾短语命名。', experimentType: 'METHODS', expectedEntities: [], includeAblations: false, status: 'OPEN', groupCount: 2, assignmentCount: 3 }
const evaluationSummary = { conditionKey: 'METHODS', sampleCount: 12, meanPreScore: 64.5, medianPreScore: 65, stddevPreScore: 8.2, iqrPreScore: 11, meanPostScore: 73.75, medianPostScore: 75, stddevPostScore: 9.1, iqrPostScore: 12, meanDelayedScore: 68.25, medianDelayedScore: 70, iqrDelayedScore: 10, meanEffort: 4.2, medianEffort: 4, stddevEffort: 1.2, iqrEffort: 2, meanElapsedSeconds: 126, medianElapsedSeconds: 120, stddevElapsedSeconds: 12, iqrElapsedSeconds: 18, feedbackCount: 10 }
const adminDraft = { id: 14, courseId: 3, sectionId: 31, title: '从业务问题到需求描述', status: 'DRAFT', generatedText: '先整理业务目标，再确认系统边界。为每项需求补写可观察的验收结果。', objectives: [], prerequisites: [], triples: evidence, commonErrors: [], selfTests: [], evidence: { graphEntity: '需求分析', tripleCount: evidence.length } }

function apiResponse(url, role, scenario, method) {
  const path = new URL(url).pathname
  if (path === '/api/auth/me') return json({ id: role === 'ADMIN' ? 1 : 2, username: role === 'ADMIN' ? 'smoke-admin' : 'smoke-student', displayName: role === 'ADMIN' ? '封版巡检管理员' : '封版巡检学生', role })
  if (path === '/api/dashboard/overview') return json({ mastery: 42, completed: 4, wrongCount: 2, accuracy: 75, activity: [] })
  if (path === '/api/knowledge/points') return json(coursePoints)
  if (path === '/api/courses') return json([course])
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
  if (path === '/api/admin/question-bank/versions') return json([
    { id: 13, courseId: 3, courseName: course.name, versionName: '软件工程 · 需求章节修订', status: 'DRAFT', sourceFilename: 'requirements.json', questionCount: 12 },
    { id: 10, courseId: 3, courseName: course.name, versionName: '软件工程 · 学期题库', status: 'PUBLISHED', sourceFilename: 'intro.json', questionCount: 48 },
  ])
  if (path === '/api/admin/micro-lessons') return json([adminDraft])
  if (path.includes('/admin/courses/') && path.endsWith('/prerequisite-quality')) return json({ courseId: 3, pointCount: 1, validEdgeCount: 0, errorCount: 0, warningCount: 0, valid: true, issues: [] })
  if (path === '/api/admin/knowledge-points') return json(coursePoints.map(point => ({ ...point, enabled: true })))
  if (path === '/api/admin/questions') return json([{ ...quiz, courseId: 3, knowledgePointId: 12, answer: ['A'], explanation: '用动宾短语描述加工。', difficulty: 2, enabled: true, source: 'MANUAL' }])
  if (path === '/api/admin/questions/101/diagnostics') return json([{ id: 1, optionKey: 'B', optionText: quiz.options[1].text, knowledgePointId: 12, knowledgePointName: quiz.knowledgePoint, rationale: '将描述数据存储的名词短语误用为加工名称。', enabled: true }])
  if (path.endsWith('/study')) {
    if (role === 'ADMIN' && scenario === 'micro-author-forbidden') return json({ message: '当前账号没有课程阅读权限。' }, 403)
    const empty = scenario === 'course-empty'
    return json({ ...course, courseId: course.id, chapters: empty ? [] : [{ id: 1, name: '软件需求', description: '理解需求、建模与核验。', sections: courseSections }], totalSections: empty ? 0 : courseSections.length, completedSections: empty ? 0 : 1 })
  }
  if (/^\/api\/courses\/3\/study\/sections\/\d+\/micro-lesson$/.test(path)) return json({
    id: 4, courseId: 3, sectionId: 31, title: '从业务问题到需求描述', status: 'PUBLISHED',
    generatedText: '需求分析从问题域开始。先明确系统的使用者、输入输出以及要解决的问题，再把期望功能与限制条件分别记录下来。\n\n以图书借阅为例，先描述借阅期限与库存校验的规则，最后为规则补上可观察的验收结果。',
    objectives: ['判断一项需求能否被验证'], prerequisites: [{ id: 11, name: '需求分析', graphEntity: '需求分析' }], triples: evidence,
    commonErrors: [{ stem: '把实现方案写成用户目标', explanation: '先描述业务需要，再比较解决方案。', wrongCount: 2 }],
    selfTests: [{ questionId: 101, stem: '如何核验一条借阅需求？', options: [] }], evidence: { graphEntity: '需求分析', tripleCount: evidence.length },
  })
  if (path === '/api/knowledge/graph') return json(scenario === 'graph-offline'
    ? { available: false, nodes: [], edges: [], evidence: [] }
    : { available: true, nodes: graphNodes, edges: evidence.map(({ subject, object, predicate, score }) => ({ source: subject, target: object, label: predicate, score })), evidence })
  if (path === '/api/knowledge/learning-path') return json({ targetId: Number(new URL(url).searchParams.get('targetId')), courseId: 3, nodes: pathNodes, cycleDetected: false })
  if (path === '/api/knowledge/mastery-map') return json({ courseId: 3, courseName: '软件工程', nodes: pathNodes.map((point, index) => ({ ...point, attemptCount: index < 2 ? 4 : 0, correctCount: 4 - index * 2, dueReviewCount: index === 1 ? 2 : 0 })), edges: [{ source: 11, target: 12, relation: '先修' }, { source: 12, target: 13, relation: '先修' }] })
  if (path === '/api/adaptive-practice') return json([{ questionId: quiz.id, stem: quiz.stem, knowledgePointId: 12, knowledgePointName: quiz.knowledgePoint, difficulty: 2, mastery: 42, wrongCount: 2, due: true, score: 84, reason: '已到复习时间；掌握度相对较低，建议先复习加工命名。' }])
  if (path === '/api/recommendations') return json(scenario === 'recommendation-empty' ? [] : [
    { id: 11, knowledgePointId: 12, title: '巩固数据流图的加工命名', reason: '该知识点的待复习错题已到期，且近期作答中的加工与数据存储概念容易混淆。建议先对照课程中的数据流图示例，再练习一题判断加工的命名是否能准确描述数据变换。', kind: '薄弱点巩固', score: 0.91, knowledgePoint: '数据流图' },
    { id: 12, knowledgePointId: 11, title: '回顾需求分析的验收条件', reason: '这是理解需求规格说明书的先修知识点。', kind: '先修补齐', score: 0.73, knowledgePoint: '需求分析' },
  ])
  if (path === '/api/wrong-book') return json(wrongItems)
  if (path === '/api/quizzes') return json([quiz])
  if (path === '/api/quizzes/101/submit') return json({ questionId: 101, correct: false, correctAnswer: ['A'], explanation: '加工描述对数据执行的操作；数据存储描述被保存的对象。' })
  if (path === '/api/quizzes/101/diagnosis') return json([{ optionKey: 'B', optionText: quiz.options[1].text, knowledgePointId: 12, knowledgePointName: '加工命名', rationale: '用名词短语命名的是数据存储；加工应描述输入如何变成输出。', sectionId: 32, sectionTitle: '数据流图', practiceQuestionId: 101 }])
  if (path === '/api/spaced-reviews/due') return json([reviewCard])
  if (path === '/api/spaced-reviews/paused') return json([])
  if (path === '/api/spaced-reviews/21/review') return json({ attemptId: 8, questionId: 101, correct: true, correctAnswer: ['A'], explanation: '加工名称应能说明数据变换。', recallRating: 2, intervalMinutes: 4320, nextDueAt: '2026-11-26T08:00:00', mastered: false, consecutiveSuccessfulReviews: 1 })
  if (path === '/api/study-plans/today') return json({ id: 1, date: '2026-11-21', budgetMinutes: 30, tasks: planTasks })
  if (path === '/api/reports/learning') return json({ from: '2026-11-15', to: '2026-11-21', days: 7, courseName: '软件工程', reading: { totalSections: 8, completedSections: 3, completedInPeriod: 2 }, practice: { attempts: 12, correct: 9, accuracy: 75 }, reviews: { reviews: 2, correct: 1, accuracy: 50 }, plans: { totalTasks: 3, completedTasks: 1, plannedMinutes: 30, completedMinutes: 10 }, dailyActivity: [3, 4, 5].map((attempts, index) => ({ date: `2026-11-${19 + index}`, attempts, correct: attempts - 1, readingCompleted: index === 0 ? 0 : 1 })), chapters: [{ chapterId: 1, chapterName: '软件需求', totalSections: 4, completedSections: 2, completedInPeriod: 1 }, { chapterId: 2, chapterName: '软件设计', totalSections: 4, completedSections: 1, completedInPeriod: 1 }], knowledge: coursePoints.map(point => ({ pointId: point.id, pointName: point.name, mastery: point.mastery, attempts: 4, correct: Math.round(point.mastery / 25), dueReviewCount: point.id === 12 ? 1 : 0 })) })
  if (path === '/api/experiments') return json([experimentRun])
  if (path === '/api/admin/experiments/fixed-suite') return json({
    suiteVersion: '2026.1',
    cases: [
      { caseId: 'CASE-001', question: quiz.stem, sourceSection: '数据流图', knowledgePoint: '数据流图' }
    ]
  })
  if (path === '/api/admin/experiment-tasks') return json([experimentTask, { ...experimentTask, id: 10, title: '课程阅读后的延后测验', status: 'DRAFT', assignmentCount: 0 }])
  if (path === '/api/experiment-tasks') return json([{ ...experimentTask, id: 41, taskId: 9, groupId: 1, userId: 2, groupName: 'A 组', conditionKey: 'METHODS', status: 'ASSIGNED', taskStatus: 'OPEN', referenceAnswer: '', taskOrder: 'A→B' }])
  if (path === '/api/evaluations/sessions') {
    const sample = { id: 12, conditionKey: 'METHODS', taskKey: '需求分析理解任务', taskOrder: 'A→B', consentVersion: 'evaluation-v2', completedPhases: ['PRE', 'INTERVENTION', 'POST'] }
    return method === 'POST'
      ? json({ ...sample, startedAt: new Date().toISOString() })
      : json([{ ...sample, id: 11, startedAt: '2026-11-21T08:00:00Z', completedAt: '2026-11-21T08:02:00Z', psychologicalEffort: 4 }])
  }
  if (path === '/api/admin/evaluations/summary') return json([evaluationSummary, { ...evaluationSummary, conditionKey: 'LEARNING_TASK', sampleCount: 7, meanEffort: 5, medianEffort: 5, feedbackCount: 3 }])
  if (path === '/api/admin/evaluations/export') return { status: 200, contentType: 'text/csv', body: 'participant,condition\nanon-001,METHODS\n' }
  if (path === '/api/tutor/sessions') return json(scenario === 'tutor-history' ? [{ id: 7, title: '需求规格说明书练习' }, { id: 8, title: '数据流图复习' }] : [])
  if (path === '/api/tutor/sessions/7/messages') return json([
    { id: 1, role: 'USER', content: chatQuestion, mode: 'TRIPLES' },
    { id: 2, role: 'ASSISTANT', content: chatAnswer, mode: 'TRIPLES', metadata: { results: [{ mode: 'TRIPLES', answer: chatAnswer, triples: evidence, latencyMs: 920 }] } },
  ])
  if (path.startsWith('/api/learning/notes')) return json(scenario === 'course-notes' ? [{ id: 1, sectionId: 32, title: '先画系统边界', content: '先列出外部实体，再检查每条输入和输出。', tags: ['数据流图'] }] : [])
  if (path.startsWith('/api/learning/bookmarks')) return json({ bookmarked: false })
  if (path === '/api/ai/dependencies') return json({ configured: true, pythonKnowledgeStatus: 'UP', rankedModelEnabled: false, rankedModelConfigured: false, rankedModelStatus: 'DISABLED' })
  if (path === '/api/system/status') return json({ checkedAt: new Date().toISOString(), dependencies: [{ key: 'mysql', label: 'MySQL', status: 'UP', impact: '' }, { key: 'python', label: 'Python Bridge', status: 'UP', impact: '' }] })
  if (path.startsWith('/api/ai/')) return json({ answer: '演示回答', mode: 'DIRECT', hasEvidence: true, triples: [], entities: [], error: null })
  return json({})
}

await access(path.join(root, 'dist/index.html')).catch(() => { throw new Error('Run npm run build before the visual smoke test') })
const server = await preview({ root, configFile: path.join(root, 'vite.config.ts'), preview: { host: '127.0.0.1', port: 0, strictPort: true, proxy: {} } })
const visualScenarios = {
  STUDENT: [
    { route: '/courses?courseId=3&sectionId=32', name: 'course-notes', async check(page) {
      const toggle = page.getByRole('button', { name: /展开笔记/ })
      if (await toggle.count()) await toggle.click()
      await page.getByText('先画系统边界').waitFor()
    } },
    { route: '/courses', name: 'course-empty', check: page => page.getByText('本课程的阅读内容正在准备中。', { exact: false }).waitFor() },
    { route: '/knowledge', name: 'graph-offline', async check(page) {
      await page.getByText('等待知识服务连接').waitFor()
      if (await page.locator('.graph-panel canvas, .evidence-triple-card').count()) throw new Error('Unavailable graph rendered replacement data')
    } },
    { route: '/knowledge', name: 'graph-evidence', async check(page) {
      await page.locator('.graph-panel canvas').first().waitFor()
      await page.getByRole('button', { name: '查看详情' }).click()
      await page.locator('.evidence-triple-card').first().waitFor()
    } },
    { route: '/knowledge', name: 'graph-3d', async check(page) {
      await page.locator('.graph-panel canvas').first().waitFor()
      await page.getByRole('button', { name: '3D 图谱', exact: true }).click()
      const canvas = page.getByTestId('three-graph-canvas').locator('canvas')
      await canvas.waitFor()
      // A WebGL canvas with only its flat background passes DOM visibility checks.
      const sample = () => canvas.evaluate(element => {
        const image = document.createElement('canvas')
        image.width = image.height = 64
        const ctx = image.getContext('2d')
        ctx.drawImage(element, 0, 0, 64, 64)
        const rgba = ctx.getImageData(0, 0, 64, 64).data
        const rgb = []
        for (let i = 0; i < rgba.length; i += 4) rgb.push((rgba[i] << 16) | (rgba[i + 1] << 8) | rgba[i + 2])
        return rgb
      })
      await page.getByText('交互分析').waitFor()
      const before = await sample()
      if (new Set(before).size < 6) throw new Error('3D viewport has no visible graph')
      const bounds = await canvas.boundingBox()
      const x = bounds.x + bounds.width / 2
      const y = bounds.y + bounds.height / 2
      await page.mouse.move(x, y)
      await page.mouse.down()
      await page.mouse.move(x + 85, y + 20, { steps: 5 })
      await page.mouse.up()
      const after = await sample()
      if (before.every((color, i) => color === after[i])) throw new Error('3D viewport did not react to dragging')
    } },
    { route: '/knowledge?view=path&knowledgePointId=13', name: 'prerequisite-path', check: page => page.getByTestId('path-node').first().waitFor() },
    { route: '/mastery-map', name: 'mastery-data', async check(page) {
      await page.locator('.mastery-canvas canvas').first().waitFor()
      await page.locator('.mastery-node h2').first().click()
      await page.locator('.evidence-drawer.open').getByText('当前节点：', { exact: false }).waitFor()
      if (await page.locator('.evidence-triple-card').count()) throw new Error('Mastery graph rendered synthetic triples')
    } },
    { route: '/tutor?courseId=3&sectionId=32&entity=数据流图', name: 'tutor-history', async check(page) {
      await page.getByRole('button', { name: '需求规格说明书练习', exact: true }).click()
      await page.locator('.tutor-message-bubble pre').waitFor()
      await page.locator('.evidence-block summary').click()
    } },
    { route: '/practice', name: 'practice-selected', check: page => page.locator('.answer-option').nth(1).click() },
    { route: '/practice', name: 'practice-diagnosis', async check(page) {
      await page.locator('.answer-option').nth(1).click()
      await page.getByRole('button', { name: '提交答案' }).click()
      await page.getByText('可能混淆：加工命名').waitFor()
      const resultOptions = await page.locator('.answer-option.correct, .answer-option.incorrect').evaluateAll(items => items.map(item => getComputedStyle(item).opacity))
      if (resultOptions.length !== 2 || resultOptions.some(opacity => opacity !== '1')) throw new Error('Graded answers lost visual contrast')
    } },
    { route: '/review', name: 'review-result', async check(page) {
      await page.locator('.answer-option').first().click()
      await page.getByRole('radio', { name: '一般' }).check()
      await page.getByRole('button', { name: '提交复习' }).click()
      await page.getByText('4320 分钟后', { exact: false }).waitFor()
    } },
    { route: '/recommendations', name: 'recommendation-empty', check: page => page.getByText('当前暂无学习建议').waitFor() },
    { route: '/recommendations', name: 'recommendation-reason', async check(page) {
      const reason = page.locator('.rec-copy p').first()
      await reason.waitFor()
      if (await reason.evaluate(element => element.scrollWidth > element.clientWidth + 1 || getComputedStyle(element).whiteSpace === 'nowrap')) throw new Error('Full recommendation reason is not readable')
    } },
    { route: '/wrong-book', name: 'wrong-search', async check(page) {
      const search = page.getByPlaceholder('搜索题目或知识点')
      await search.fill('数据流图')
      if (await page.locator('.wrong-row').count() !== 1) throw new Error('Wrong-book filter did not narrow the list')
      if (await search.evaluate(element => getComputedStyle(element).borderTopWidth !== '0px')) throw new Error('Wrong-book search has two visible borders')
    } },
    { route: '/evaluation', name: 'evaluation-effort', async check(page) {
      await page.getByRole('checkbox', { name: '我已阅读说明并同意记录本次评估数据' }).check()
      await page.getByRole('button', { name: '开始评估' }).click()
      await page.locator('.effort-scale input[value="7"]').check()
      if (await page.locator('.effort-scale input:checked').count() !== 1) throw new Error('Paas must keep exactly one selected rating')
      const positions = await page.locator('.effort-scale label').evaluateAll(labels => labels.map(label => {
        const { top, height } = label.getBoundingClientRect()
        const { width } = label.getBoundingClientRect()
        return { top, height, width }
      }))
      const widths = positions.map(position => position.width)
      if (positions.length !== 9 || new Set(positions.map(p => Math.round(p.top))).size !== 1 || positions.some(p => p.height < 44 || p.width < 44) || Math.max(...widths) - Math.min(...widths) > 2) throw new Error('Paas ticks must fit one evenly spaced, comfortable desktop row')
    } },
    { route: '/learning-report', name: 'active-sidebar', async check(page) {
      const active = page.locator('.primary-nav .nav-item.active')
      await active.waitFor()
      if (!await active.evaluate(element => {
        const nav = element.closest('nav').getBoundingClientRect()
        const item = element.getBoundingClientRect()
        return item.top >= nav.top - 1 && item.bottom <= nav.bottom + 1
      })) throw new Error('Current route is clipped in the sidebar')
    } },
    { route: '/dashboard', name: 'password-modal-validation', async check(page) {
      const userTrigger = page.locator('button.user-menu')
      await userTrigger.waitFor()
      await userTrigger.click()
      const pwdBtn = page.getByRole('menuitem', { name: '修改密码' })
      await pwdBtn.waitFor()
      await pwdBtn.click()
      const dialog = page.locator('[role="dialog"][aria-labelledby="change-password-title"]')
      await dialog.waitFor()
      await dialog.locator('input[autocomplete="current-password"]').fill('oldpass123')
      await dialog.locator('input[autocomplete="new-password"]').first().fill('newpass1234')
      await dialog.locator('input[autocomplete="new-password"]').last().fill('mismatchpass')
      await dialog.locator('button[type="submit"]').click()
      await page.getByText('两次输入的新密码不一致').waitFor()
      await dialog.locator('button[aria-label="关闭对话框"]').click()
    } },
    { route: '/experiment-run?taskId=9&assignmentId=41', name: 'student-assigned-run-hidden-rubric', async check(page) {
      await page.getByText('任务实验运行').waitFor()
      if (await page.locator('.reference-answer, .scoring-rubric, .teacher-rubric').count() > 0) throw new Error('Student assigned run leaked teacher rubric')
    } },
    { route: '/experiment-tasks', name: 'student-task-order', async check(page) {
      await page.locator('.task-card').first().waitFor()
      await page.getByText('顺序 A→B').first().waitFor()
    } },
  ],
  ADMIN: [
    { route: '/admin/content?tab=question-bank', name: 'unsaved-route-confirm', async check(page) {
      const input = page.locator('[data-testid="version-name"]')
      await input.waitFor()
      await input.fill('未保存测试草稿')
      page.once('dialog', dialog => dialog.dismiss())
      await page.locator('[data-testid="tab-course"]').click()
      await input.waitFor()
      if ((await input.inputValue()) !== '未保存测试草稿') throw new Error('Unsaved changes guard failed to preserve input on cancel')
    } },
    { route: '/admin/content?tab=question-bank', name: 'question-bank-file-control', async check(page) {
      const fileButton = page.locator('.question-bank-panel .file-button')
      await fileButton.waitFor()
      const style = await fileButton.evaluate(element => ({ display: getComputedStyle(element).display, color: getComputedStyle(element).color }))
      if (!['flex', 'inline-flex'].includes(style.display) || style.color === 'rgb(255, 255, 255)') throw new Error('Question bank file picker lost alignment or readable text color')
      await page.locator('.version-row').first().waitFor()
    } },
    { route: '/teaching-dashboard', name: 'teaching-empty', check: page => page.getByText('当前范围内暂无学习活动数据。').waitFor() },
    { route: '/admin/experiments', name: 'experiment-results', async check(page) {
      await page.locator('.saved-experiment-list button').first().click()
      await page.getByText('模型服务超时，未生成回答。').waitFor()
      if (await page.locator('.compare-result-card').count() !== 7) throw new Error('Not all experiment conditions rendered')
      if (await page.locator('.result-failed .compare-answer, .result-failed .rating-select').count()) throw new Error('Failed condition rendered as a successful result')
      const metricTops = await page.locator('.experiment-scores').evaluateAll(groups => groups.slice(0, 2).map(group => Math.round(group.getBoundingClientRect().top)))
      if (metricTops.length !== 2 || metricTops[0] !== metricTops[1]) throw new Error('First-row experiment metrics are misaligned')
    } },
    { route: '/admin/evaluations', name: 'evaluation-table', async check(page) {
      const table = page.locator('.evaluation-table')
      await table.waitFor()
      if (!await table.evaluate(element => element.scrollWidth <= element.clientWidth + 1)) throw new Error('Evaluation table hides columns at desktop width')
      if (await page.locator('.page-content h1').count() !== 1) throw new Error('Embedded admin tab duplicates the page title level')
    } },
    { route: '/admin/micro-lessons', name: 'micro-author-forbidden', async check(page) {
      await page.getByRole('alert').getByText('当前账号没有课程阅读权限。').waitFor()
      if (!await page.getByRole('button', { name: '生成证据微课草稿' }).isDisabled()) throw new Error('Draft generation remains available after the section request was denied')
    } },
    { route: '/admin/micro-lessons', name: 'micro-author-layout', async check(page) {
      await page.getByRole('combobox', { name: '微课课程' }).waitFor()
      await page.getByText('从业务问题到需求描述').waitFor()
      if (await page.getByRole('button', { name: '生成证据微课草稿' }).isDisabled()) throw new Error('Valid course data did not enable evidence draft authoring')
    } },
  ],
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
    const expectedHttpErrors = []
    const httpErrors = []
    const resourceConsoleErrors = []
    let scenario = ''
    page.on('response', response => {
      if (response.status() < 400) return
      const issue = { path: new URL(response.url()).pathname, status: response.status(), method: response.request().method() }
      if (scenario === 'micro-author-forbidden' && issue.path === '/api/courses/3/study' && issue.status === 403) expectedHttpErrors.push(issue)
      else httpErrors.push(issue)
    })
    page.on('console', message => {
      if (message.type() !== 'error') return
      const text = message.text()
      if (/^Failed to load resource:.*status of \d+/.test(text)) resourceConsoleErrors.push(text)
      else consoleErrors.push(text)
    })
    page.on('pageerror', error => pageErrors.push(error.message))
    await page.route('**/api/**', route => route.fulfill(apiResponse(route.request().url(), role, scenario, route.request().method())))
    await page.addInitScript((role) => {
      if (role === 'GUEST') return
      localStorage.setItem('slp-access-token', 'ui-smoke-access-token')
      localStorage.setItem('slp-user-name', '封版巡检')
      localStorage.setItem('slp-user-role', role)
    }, role)
    const visits = (role === 'GUEST' ? ['/login'] : routes).map(route => ({ route, name: '', check: null }))
    const scenariosOnly = process.env.UI_REVIEW_SCENARIOS_ONLY === '1'
    const pathFilter = process.env.UI_REVIEW_PATHS?.split(',').filter(Boolean)
    for (const { route, name, check } of [...(scenariosOnly ? [] : visits), ...visualScenarios[role]]) {
      if (pathFilter && !pathFilter.includes(route.split('?')[0])) continue
      scenario = name
      await page.goto(baseUrl + route, { waitUntil: 'networkidle' })
      let scenarioError = ''
      try { await check?.(page) } catch (error) { scenarioError = error.message }
      await page.evaluate(async () => {
        await document.fonts?.ready
        // Playwright may scroll to a clicked control. Check first-screen layout at the initial scroll position.
        window.scrollTo({ top: 0, behavior: 'instant' })
      })
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
      const expectedResourceErrors = scenario === 'micro-author-forbidden'
        ? resourceConsoleErrors.filter(text => text.includes('status of 403')).length
        : 0
      results.push({ ...metrics, role, viewport: `${viewport.width}x${viewport.height}`, requested: route, scenario, scenarioError, final: metrics.path, roleRedirectPass: metrics.path === expectedPath(role, route), overflow: metrics.scrollWidth > metrics.viewportWidth + 1, blankRoot: metrics.rootChildren === 0, topbarOverflow: metrics.topbarWidth > metrics.viewportWidth + 1, topbarOverlap: metrics.topbarBottom > metrics.contentTop + 1, screenshot, consoleErrors: [...consoleErrors], pageErrors: [...pageErrors], httpErrors: [...httpErrors], resourceConsoleErrors: [...resourceConsoleErrors], expectedHttpErrors: [...expectedHttpErrors], expectedResourceErrors })
      consoleErrors.length = 0
      pageErrors.length = 0
      expectedHttpErrors.length = 0
      httpErrors.length = 0
      resourceConsoleErrors.length = 0
    }
    await context.close()
    }
  }
  if (!results.length) throw new Error('No routes matched the visual review filters')
  const failures = results.filter(item => item.scenarioError || !item.roleRedirectPass || item.overflow || item.blankRoot || item.topbarOverflow || item.topbarOverlap || item.squeezedButtons || item.defaultControls || item.unstyledTables || !item.pageHeader || !item.pageDescription || item.consoleErrors.length || item.pageErrors.length || item.httpErrors.length || item.resourceConsoleErrors.length !== item.expectedResourceErrors || (item.scenario === 'micro-author-forbidden' && !item.expectedHttpErrors.length) || (item.role === 'GUEST' && !(item.loginContrast >= 4.5)))
  const reportPath = path.join(auditDir, 'metrics.json')
  await writeFile(reportPath, JSON.stringify({ scope: 'Browser rendering with intercepted API fixtures, not live backend integration', total: results.length, failures, results }, null, 2), 'utf8')
  console.log(`UI smoke: ${results.length - failures.length}/${results.length} passed. ${reportPath}`)
  for (const item of failures) console.error(`${item.role} ${item.viewport} ${item.requested}: ${item.screenshot}${item.scenarioError ? ` (${item.scenarioError})` : ''}`)
  if (failures.length) process.exitCode = 1
} finally {
  try { await browser?.close() } finally { await server.close() }
}
