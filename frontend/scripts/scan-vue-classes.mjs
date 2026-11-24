import { readdirSync, readFileSync, statSync } from 'node:fs'
import { extname, join, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { parse as parseSFC } from 'vue/compiler-sfc'
import { parseExpression } from '@babel/parser'
import postcss from 'postcss'
import * as csstree from 'css-tree'

// 源码中合法的测试钩子或语义标记白名单（必须指明文件、类名和原因）
// 严禁将样式缺失的类名加入此表
const ALLOWLIST = [
  // 必须在源码 allowlist 中附 file/class/reason，不用空规则消音
]

function findFiles(dir, exts) {
  const results = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      if (entry !== 'node_modules' && entry !== 'dist' && entry !== '.git') {
        results.push(...findFiles(full, exts))
      }
    } else if (exts.includes(extname(full))) {
      results.push(full)
    }
  }
  return results
}

function extractClassesFromCss(cssContent) {
  const classes = new Set()
  if (!cssContent || !cssContent.trim()) return classes

  try {
    const root = postcss.parse(cssContent)
    root.walkRules((rule) => {
      try {
        const selectorAst = csstree.parse(rule.selector, { context: 'selectorList' })
        csstree.walk(selectorAst, {
          visit: 'ClassSelector',
          enter(node) {
            if (node.name) {
              classes.add(node.name)
            }
          }
        })
      } catch {
        // 如果个别非常规选择器解析异常，正则保底
        const matches = rule.selector.match(/\.([a-zA-Z0-9_-]+)/g) || []
        for (const m of matches) {
          classes.add(m.slice(1))
        }
      }
    })
  } catch {
    // PostCSS parse 失败时的正则保底
    const matches = cssContent.match(/\.([a-zA-Z0-9_-]+)/g) || []
    for (const m of matches) {
      classes.add(m.slice(1))
    }
  }

  return classes
}

function extractClassesFromExpression(node, dynamicUnknown, sourceExp) {
  const classes = []
  if (!node) return classes

  if (node.type === 'StringLiteral') {
    return node.value.split(/\s+/).filter(Boolean)
  }

  if (node.type === 'ArrayExpression') {
    for (const elem of node.elements) {
      if (!elem) continue
      if (elem.type === 'StringLiteral') {
        classes.push(...elem.value.split(/\s+/).filter(Boolean))
      } else if (elem.type === 'ObjectExpression') {
        classes.push(...extractClassesFromExpression(elem, dynamicUnknown, sourceExp))
      } else if (elem.type === 'ConditionalExpression') {
        classes.push(...extractClassesFromExpression(elem, dynamicUnknown, sourceExp))
      } else {
        dynamicUnknown.push(sourceExp)
      }
    }
    return classes
  }

  if (node.type === 'ObjectExpression') {
    for (const prop of node.properties) {
      if (prop.type === 'ObjectProperty') {
        if (prop.key.type === 'Identifier') {
          classes.push(prop.key.name)
        } else if (prop.key.type === 'StringLiteral') {
          classes.push(prop.key.value)
        } else {
          dynamicUnknown.push(sourceExp)
        }
      } else {
        dynamicUnknown.push(sourceExp)
      }
    }
    return classes
  }

  if (node.type === 'ConditionalExpression') {
    if (node.consequent && node.consequent.type === 'StringLiteral') {
      classes.push(...node.consequent.value.split(/\s+/).filter(Boolean))
    } else {
      dynamicUnknown.push(sourceExp)
    }
    if (node.alternate && node.alternate.type === 'StringLiteral') {
      classes.push(...node.alternate.value.split(/\s+/).filter(Boolean))
    } else {
      dynamicUnknown.push(sourceExp)
    }
    return classes
  }

  dynamicUnknown.push(sourceExp)
  return classes
}

function traverseTemplateAst(node, onElement) {
  if (!node) return
  if (node.type === 1) { // NodeTypes.ELEMENT
    onElement(node)
  }
  if (node.children) {
    for (const child of node.children) {
      traverseTemplateAst(child, onElement)
    }
  }
}

export function runClassScan(rootDir = resolve(process.cwd())) {
  const srcDir = join(rootDir, 'src')

  // 1. 读取 4 个全局 CSS 文件的所有样式规则
  const globalCssFiles = [
    join(srcDir, 'styles-design-system.css'),
    join(srcDir, 'styles.css'),
    join(srcDir, 'styles-graph.css'),
    join(srcDir, 'styles-learning.css')
  ]
  const globalClasses = new Set()
  for (const file of globalCssFiles) {
    try {
      const content = readFileSync(file, 'utf8')
      const classes = extractClassesFromCss(content)
      for (const c of classes) globalClasses.add(c)
    } catch {}
  }

  // 2. 扫描所有 .vue 文件
  const vueFiles = findFiles(srcDir, ['.vue'])
  const componentStyles = new Map() // filePath -> Set of classes
  const templateUsages = [] // { file, class, tag }
  const dynamicUnknown = [] // { file, exp }

  for (const file of vueFiles) {
    const relFile = relative(rootDir, file).replace(/\\/g, '/')
    const content = readFileSync(file, 'utf8')
    const { descriptor } = parseSFC(content, { filename: relFile })

    // 提取当前 SFC style 中的类（包括 scoped 和全局）
    const fileClasses = new Set()
    for (const style of descriptor.styles) {
      if (style.content) {
        const classes = extractClassesFromCss(style.content)
        for (const c of classes) fileClasses.add(c)
      }
    }
    componentStyles.set(relFile, fileClasses)

    // 提取 template 中的所有类名
    if (descriptor.template && descriptor.template.ast) {
      traverseTemplateAst(descriptor.template.ast, (el) => {
        for (const prop of el.props) {
          // 静态 class
          if (prop.type === 6 && prop.name === 'class' && prop.value) { // Attribute
            const staticClasses = prop.value.content.split(/\s+/).filter(Boolean)
            for (const cls of staticClasses) {
              templateUsages.push({ file: relFile, class: cls, tag: el.tag })
            }
          }
          // 动态 :class
          if (prop.type === 7 && prop.name === 'bind' && prop.arg && prop.arg.content === 'class') {
            const exp = prop.exp?.content
            if (exp) {
              try {
                const ast = parseExpression(exp)
                const dynClasses = extractClassesFromExpression(ast, [], exp)
                for (const cls of dynClasses) {
                  templateUsages.push({ file: relFile, class: cls, tag: el.tag })
                }
              } catch {
                dynamicUnknown.push({ file: relFile, exp })
              }
            }
          }
        }
      })
    }
  }

  // 3. 校验类名匹配情况
  const missingClasses = []
  const allowlisted = []

  for (const usage of templateUsages) {
    const { file, class: cls, tag } = usage

    // 忽略 Element Plus、RouterLink 等第三方或伪状态类
    if (
      cls.startsWith('el-') ||
      cls.startsWith('is-') ||
      cls.startsWith('router-') ||
      cls === 'active' ||
      cls === 'selected'
    ) {
      continue
    }

    // 检查是否在全局 CSS 或当前组件样式中定义
    const isGlobal = globalClasses.has(cls)
    const isComponent = componentStyles.get(file)?.has(cls)

    if (isGlobal || isComponent) {
      continue
    }

    // 检查白名单
    const inAllowlist = ALLOWLIST.find(item => item.file === file && item.class === cls)
    if (inAllowlist) {
      allowlisted.push({ file, class: cls, tag, reason: inAllowlist.reason })
      continue
    }

    missingClasses.push({ file, class: cls, tag })
  }

  return {
    missingClasses,
    dynamicUnknown,
    allowlisted,
    totalVueFiles: vueFiles.length,
    totalUsages: templateUsages.length,
    globalClassesCount: globalClasses.size
  }
}

// CLI 执行入口
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const isCheck = process.argv.includes('--check')
  const report = runClassScan()

  console.log(JSON.stringify(report, null, 2))

  if (isCheck && report.missingClasses.length > 0) {
    console.error(`\n[FAIL] Found ${report.missingClasses.length} genuine missing classes!`)
    process.exit(1)
  }
  if (isCheck) {
    console.log(`\n[PASS] All ${report.totalUsages} template class usages have matching styles (0 missing classes).`)
    process.exit(0)
  }
}

