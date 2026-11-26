<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowRight, BookOpenCheck, CalendarDays, CircleAlert, Clock3, RotateCcw, SkipForward, X } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { learningApi, type StudyPlan, type StudyPlanTask } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
interface TaskHint {
  taskId: number
  title: string
  type: StudyPlanTask['type']
  path: string
  message: string
}
const plan=ref<StudyPlan|null>(null)
const budget=ref(45)
const loading=ref(true)
const saving=ref(false)
const error=ref('')
const taskHint=ref<TaskHint|null>(null)
let hintTimer:ReturnType<typeof setTimeout>|null=null
const used=computed(()=>plan.value?.tasks.filter(task=>task.state==='COMPLETED').reduce((sum,task)=>sum+task.minutes,0)??0)
const scheduled=computed(()=>plan.value?.tasks.reduce((sum,task)=>sum+task.minutes,0)??0)
function clearHintTimer(){
  if(hintTimer){
    clearTimeout(hintTimer)
    hintTimer=null
  }
}
function scheduleHintDismiss(){
  clearHintTimer()
  hintTimer=setTimeout(()=>{
    taskHint.value=null
    hintTimer=null
  },6500)
}
function dismissTaskHint(){
  clearHintTimer()
  taskHint.value=null
}
function showTaskHint(task:StudyPlanTask,message:string){
  taskHint.value={ taskId:task.id, title:task.title, type:task.type, path:task.path, message }
  scheduleHintDismiss()
}
async function load(){loading.value=true;error.value='';try{plan.value=await learningApi.studyPlanToday();budget.value=plan.value.budgetMinutes}catch{error.value='暂时无法读取每日计划，请检查服务连接后重试。'}finally{loading.value=false}}
async function generate(){if(saving.value)return;saving.value=true;error.value='';dismissTaskHint();try{plan.value=await learningApi.generateStudyPlan(budget.value)}catch(cause){const e=cause as {response?:{data?:{message?:string;detail?:string}}};error.value=e.response?.data?.message||e.response?.data?.detail||'计划生成失败，请检查服务连接。'}finally{saving.value=false}}
async function action(task:StudyPlanTask,kind:'COMPLETE'|'SKIP'){
  if(saving.value)return
  saving.value=true;error.value=''
  try{
    await learningApi.updateStudyPlanTask(task.id,kind)
    if(taskHint.value?.taskId===task.id) dismissTaskHint()
    plan.value=await learningApi.studyPlanToday()
  }catch(cause){
    const e=cause as {response?:{data?:{message?:string;detail?:string}}}
    const msg=e.response?.data?.message||e.response?.data?.detail||'请先完成对应课程阅读、正确作答或复习，再标记计划任务完成'
    if(kind==='COMPLETE'){
      showTaskHint(task,msg)
    }else{
      error.value=msg
    }
  }finally{saving.value=false}
}
function label(type:string){return type==='REVIEW'?'间隔复习':type==='READING'?'课程阅读':'针对练习'}
onMounted(()=>void load())
onBeforeUnmount(clearHintTimer)
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="学习安排" title="每日学习计划" description="按固定规则从到期复习、未读小节和薄弱题目生成任务；完成对应课程阅读、正确作答或复习后可标记完成。"><button class="button button-outline" :disabled="loading||saving" @click="load"><RotateCcw :size="15"/>刷新</button></SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16"/><span>{{ error }}</span><button class="text-button" @click="load">重试</button></div>
    <section class="plan-controls"><label><Clock3 :size="16"/>每日时间预算<select v-model.number="budget" :disabled="saving"><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="45">45 分钟</option><option :value="60">60 分钟</option><option :value="90">90 分钟</option><option :value="120">120 分钟</option></select></label><button class="button button-dark" :disabled="loading||saving" @click="generate"><CalendarDays :size="15"/>{{ saving?'保存中…':'生成计划' }}</button><div v-if="plan" class="plan-summary"><span>{{ plan.date }}</span><span>已完成 {{ used }} 分钟</span><span>计划 {{ scheduled }} / {{ plan.budgetMinutes }} 分钟</span></div></section>
    <FeedbackState v-if="loading" loading loading-title="正在生成今日计划" loading-text="正在读取当前账户的到期复习、课程阅读和练习任务。" />
    <section v-else-if="plan?.tasks.length" class="plan-task-list"><article v-for="(task,index) in plan.tasks" :key="task.id" class="plan-task" :class="['task-'+task.state.toLowerCase(), { 'is-prereq-blocked': taskHint?.taskId===task.id }]"><div class="plan-index">{{ String(index+1).padStart(2,'0') }}</div><div class="plan-task-copy"><div class="plan-task-top"><span class="eyebrow">{{ label(task.type) }}</span><span class="plan-minutes"><Clock3 :size="13"/>{{ task.minutes }} 分钟</span></div><h2>{{ task.title }}</h2><p>{{ task.detail }}</p><small>{{ task.reason }}</small></div><div class="plan-task-actions"><RouterLink class="button button-outline button-small" :to="task.path">开始<ArrowRight :size="14"/></RouterLink><button v-if="task.state==='PENDING'" class="icon-button" title="跳过任务" aria-label="跳过任务" :disabled="saving" @click="action(task,'SKIP')"><SkipForward :size="16"/></button><button v-if="task.state==='PENDING'" type="button" class="button button-outline button-small plan-complete-btn" title="完成任务（需先完成对应学习行为）" :disabled="saving" @click="action(task,'COMPLETE')"><BookOpenCheck :size="14"/><span>标记完成</span></button><span v-else class="task-state-label">{{ task.state==='COMPLETED'?'已完成':'已跳过' }}</span></div></article></section>
    <section v-else-if="!loading" class="empty-state"><BookOpenCheck :size="24"/><strong>今日没有可安排任务</strong><span>练习错题或新增课程进度后可以重新生成计划。</span></section>
    <Transition name="plan-toast-fade">
      <div
        v-if="taskHint"
        class="plan-hint-toast"
        role="alert"
        aria-live="assertive"
        data-testid="study-plan-hint-toast"
        @mouseenter="clearHintTimer"
        @mouseleave="scheduleHintDismiss"
      >
        <CircleAlert :size="18" class="plan-hint-icon" aria-hidden="true" />
        <div class="plan-hint-copy">
          <strong>暂不能直接完成「{{ taskHint.title }}」</strong>
          <p>{{ taskHint.message }}</p>
          <div class="plan-hint-actions">
            <RouterLink class="plan-hint-link" :to="taskHint.path" @click="dismissTaskHint">
              <span>前往完成{{ label(taskHint.type) }}</span>
              <ArrowRight :size="13" aria-hidden="true" />
            </RouterLink>
          </div>
        </div>
        <button type="button" class="plan-hint-close" aria-label="关闭提示" @click="dismissTaskHint">
          <X :size="15" aria-hidden="true" />
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.plan-task{transition:background var(--motion),box-shadow var(--motion)}
.plan-task.is-prereq-blocked{background:var(--warning-soft);box-shadow:inset 3px 0 0 var(--warning)}
.plan-complete-btn{color:var(--accent-strong);border-color:var(--border-default)}
.plan-hint-toast{position:fixed;right:24px;bottom:24px;z-index:1200;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:start;gap:10px;width:min(420px,calc(100vw - 32px));padding:13px 14px 13px 16px;color:var(--text);background:var(--surface);border:1px solid color-mix(in srgb,var(--warning) 45%,var(--border-default));border-left:4px solid var(--warning);border-radius:var(--radius-md);box-shadow:var(--shadow-md)}
.plan-hint-icon{margin-top:2px;color:var(--warning);flex-shrink:0}
.plan-hint-copy{min-width:0}
.plan-hint-copy strong{display:block;color:var(--text);font-size:13.5px;line-height:1.4;overflow-wrap:anywhere}
.plan-hint-copy p{margin:4px 0 0;color:var(--warning);font-size:13px;line-height:1.55;overflow-wrap:anywhere}
.plan-hint-actions{margin-top:8px}
.plan-hint-link{display:inline-flex;align-items:center;gap:4px;color:var(--accent-strong);font-size:12.5px;font-weight:600;text-decoration:none}
.plan-hint-link:hover{text-decoration:underline}
.plan-hint-close{width:28px;height:28px;min-height:28px;display:grid;place-items:center;padding:0;color:var(--text-muted);background:transparent;border:0;border-radius:var(--radius-xs);cursor:pointer}
.plan-hint-close:hover{color:var(--text);background:var(--surface-soft)}
.plan-toast-fade-enter-active,.plan-toast-fade-leave-active{transition:opacity var(--motion),transform var(--motion)}
.plan-toast-fade-enter-from,.plan-toast-fade-leave-to{opacity:0;transform:translateY(8px)}
@media (max-width:620px){
  .plan-hint-toast{right:14px;left:14px;bottom:76px;width:auto}
}
@media (prefers-reduced-motion:reduce){
  .plan-task,.plan-toast-fade-enter-active,.plan-toast-fade-leave-active{transition:none}
}
</style>
