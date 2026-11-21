<script setup lang="ts">
import { ArrowRight, Check, CircleAlert, CircleCheck, CircleX } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import type { QuizDiagnosis } from '../api/client'

interface QuizOption { key: string; text: string }
interface QuizQuestion { id: number; stem: string; type: string; options: QuizOption[]; knowledgePoint?: string }
interface SubmitResult { questionId: number; correct: boolean; correctAnswer: string[]; explanation?: string }
defineProps<{
  question: QuizQuestion
  index: number
  total: number
  questionType: string
  progress: number
  isMultiple: boolean
  isTrueFalse: boolean
  selected: string[]
  correctAnswers: string[]
  result: SubmitResult | null
  submitting: boolean
  submitError: string
  wrongItemId?: number
  diagnoses: QuizDiagnosis[]
  diagnosisLoading: boolean
  diagnosisError: string
} >()
const emit = defineEmits<{ toggle: [key: string]; submit: []; retrySubmit: []; retryQuestion: []; next: []; openKnowledge: [] }>()
</script>

<template>
  <div class="quiz-main practice-question-card">
    <div class="quiz-topline"><span class="question-count">{{ String(index + 1).padStart(2, '0') }} <i>/ {{ String(total).padStart(2, '0') }}</i></span><span class="question-type">{{ questionType }}</span><span v-if="question.knowledgePoint" class="question-point">{{ question.knowledgePoint }}</span></div>
    <div class="question-progress" role="progressbar" aria-label="当前题目位置" :aria-valuenow="index + 1" :aria-valuemax="total" aria-valuemin="1"><i :style="{ width: progress + '%' }"></i></div>
    <h2 class="question-stem">{{ question.stem }}</h2>
    <p class="selection-hint">{{ isMultiple ? '选择所有正确答案' : isTrueFalse ? '判断题目中的表述是否正确' : '选择一个最合适的答案' }}</p>
    <div class="option-list"><button v-for="option in question.options" :key="option.key" class="answer-option" :class="{ selected: selected.includes(option.key), correct: result && correctAnswers.includes(option.key), incorrect: result && selected.includes(option.key) && !correctAnswers.includes(option.key) }" :aria-pressed="selected.includes(option.key)" :disabled="submitting || !!result" @click="emit('toggle', option.key)"><span class="option-marker">{{ option.key }}</span><span>{{ option.text }}</span><Check v-if="result && correctAnswers.includes(option.key)" :size="18" /></button></div>
    <div v-if="!question.options.length" class="inline-notice">该题目尚未配置可作答选项，请联系课程管理员。</div>
    <div v-if="submitError" class="inline-notice submit-error" role="alert"><CircleAlert :size="16" /><span>{{ submitError }}</span><button class="text-button" :disabled="submitting" @click="emit('retrySubmit')">{{ submitting ? '重试中…' : '重试提交' }}</button></div>
    <div v-if="result" class="answer-feedback" :class="result.correct ? 'feedback-correct' : 'feedback-wrong'"><component :is="result.correct ? CircleCheck : CircleX" :size="19" /><div><strong>{{ result.correct ? '回答正确' : '本次回答有误' }}</strong><p>正确答案：{{ correctAnswers.join('、') }}</p><p>{{ result.explanation || '服务端暂未提供解析。' }}</p><span>答题记录已保存{{ wrongItemId !== undefined && result.correct ? '，该错题已完成复习。' : '。' }}</span></div></div>
    <section v-if="result && !result.correct" class="diagnosis-feedback" aria-label="错因关联"><div class="diagnosis-heading"><div><p class="eyebrow">课程配置的证据</p><h3>错因关联与补救</h3></div><span v-if="diagnosisLoading" class="diagnosis-loading">正在读取</span></div><p v-if="diagnosisError" class="diagnosis-note diagnosis-error">{{ diagnosisError }}</p><p v-else-if="diagnosisLoading" class="diagnosis-note">正在匹配干扰项与课程知识点。</p><template v-else-if="diagnoses.length"><article v-for="item in diagnoses" :key="item.optionKey + '-' + item.knowledgePointId" class="diagnosis-item"><div><strong>你选择了 {{ item.optionKey }}：{{ item.optionText }}</strong><span>可能混淆：{{ item.knowledgePointName }}</span></div><p>{{ item.rationale }}</p><div class="diagnosis-actions"><RouterLink v-if="item.sectionId" class="text-button" :to="{ path: '/courses', query: { sectionId: String(item.sectionId) } }">补读{{ item.sectionTitle ? ' · ' + item.sectionTitle : '' }}<ArrowRight :size="13" /></RouterLink><RouterLink v-if="item.practiceQuestionId" class="text-button" :to="{ path: '/practice', query: { questionId: String(item.practiceQuestionId) } }">练习类似题<ArrowRight :size="13" /></RouterLink></div></article></template><p v-else class="diagnosis-note">本题暂未配置可追溯的错因关联，平台不会仅凭一次错误答案猜测你的认知原因。</p></section>
    <div class="quiz-actions"><span class="key-hint"><kbd v-for="(_, optionIndex) in question.options.slice(0, 9)" :key="optionIndex">{{ optionIndex + 1 }}</kbd> 选择选项</span><button v-if="!result" class="button button-dark" :disabled="!selected.length || submitting" @click="emit('submit')">{{ submitting ? '提交中…' : submitError ? '重试提交' : '提交答案' }} <ArrowRight :size="16" /></button><template v-else><button v-if="!result.correct" class="text-button" @click="emit('retryQuestion')">再次作答</button><button class="button button-dark" @click="emit('next')">{{ wrongItemId !== undefined ? '返回错题列表' : index + 1 < total ? '下一题' : '查看学习建议' }} <ArrowRight :size="16" /></button></template></div>
  </div>
</template>

<style scoped>
.practice-question-card{min-width:0}.diagnosis-feedback{margin-top:16px;padding:16px;background:var(--surface-soft);border:1px solid var(--border-default);border-radius:var(--radius-md)}.diagnosis-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.diagnosis-heading .eyebrow{margin:0 0 4px;color:var(--accent)}.diagnosis-heading h3{margin:0;color:var(--text);font-size:14px}.diagnosis-loading{color:var(--text-muted);font-size:10px}.diagnosis-note{margin:12px 0 0;color:var(--text-muted);font-size:11px;line-height:1.7}.diagnosis-error{color:var(--danger)}.diagnosis-item{margin-top:12px;padding-top:12px;border-top:1px solid var(--border-soft)}.diagnosis-item>div:first-child{display:grid;gap:4px;color:var(--text);font-size:11px}.diagnosis-item>div:first-child span{color:var(--accent)}.diagnosis-item p{margin:8px 0;color:var(--text-muted);font-size:11px;line-height:1.7}.diagnosis-actions{display:flex;flex-wrap:wrap;gap:14px}
</style>
