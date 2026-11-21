<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BookOpenCheck, GraduationCap, LockKeyhole, UserRound } from '@lucide/vue'
import { learningApi } from '../api/client'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const result = mode.value === 'login'
      ? await learningApi.login({ username: username.value.trim(), password: password.value })
      : await learningApi.register({ username: username.value.trim(), password: password.value, displayName: displayName.value.trim() })
    localStorage.setItem('slp-access-token', result.accessToken)
    localStorage.setItem('slp-refresh-token', result.refreshToken)
    localStorage.setItem('slp-user-name', result.user.displayName)
    localStorage.setItem('slp-user-role', result.user.role)
    router.replace(result.user.role === 'ADMIN' ? '/teaching-dashboard' : '/dashboard')
  } catch (cause) {
    const response = (cause as { response?: { data?: { detail?: string; message?: string } } }).response
    error.value = response?.data?.detail || response?.data?.message || '暂时无法连接账户服务，请确认后端已经启动。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro" aria-label="平台介绍">
      <a class="brand login-brand" href="/"><span class="brand-mark"><GraduationCap :size="21" /></span><span><strong>知序</strong><small>软件工程学习平台</small></span></a>
      <div class="login-intro-copy"><p class="eyebrow">KNOWLEDGE-GUIDED LEARNING</p><h1>把知识关系，变成下一步学习。</h1><p>围绕软件工程课程，将课程阅读、知识图谱、题库练习与 AI 助教组织成一条可追踪的学习路径。</p></div>
      <div class="login-route" aria-label="学习闭环"><span><b>01</b>理解课程</span><i></i><span><b>02</b>连接知识</span><i></i><span><b>03</b>验证掌握</span></div>
      <small class="login-boundary">基于真实课程材料与图谱证据，服务不可用时会明确提示，不以示例数据代替。</small>
    </section>
    <section class="login-panel" aria-labelledby="login-title">
      <p class="eyebrow">软件工程 · 课程学习空间</p><h2 id="login-title">{{ mode === 'login' ? '欢迎回来' : '创建学习账户' }}</h2><p class="login-subtitle">{{ mode === 'login' ? '继续你的课程、图谱与练习记录。' : '注册后即可保存个人学习进度与答题记录。' }}</p>
      <form class="login-form" @submit.prevent="submit">
        <label v-if="mode === 'register'" class="login-field"><span>显示名称</span><div><UserRound :size="17" /><input v-model="displayName" required maxlength="80" autocomplete="nickname" placeholder="你的称呼" /></div></label>
        <label class="login-field"><span>用户名</span><div><UserRound :size="17" /><input v-model="username" required minlength="3" maxlength="60" autocomplete="username" placeholder="3–60 位用户名" /></div></label>
        <label class="login-field"><span>密码</span><div><LockKeyhole :size="17" /><input v-model="password" required :minlength="mode === 'register' ? 8 : 1" maxlength="72" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" type="password" :placeholder="mode === 'register' ? '至少 8 位' : '输入密码'" /></div></label>
        <p v-if="error" class="login-error" role="alert">{{ error }}</p>
        <button class="button button-dark login-submit" type="submit" :disabled="busy"><span>{{ busy ? '正在连接学习空间' : mode === 'login' ? '登录学习平台' : '创建账户' }}</span><ArrowRight :size="17" /></button>
      </form>
      <p class="login-switch">{{ mode === 'login' ? '还没有学习账户？' : '已经注册过？' }} <button type="button" @click="mode = mode === 'login' ? 'register' : 'login'; error = ''">{{ mode === 'login' ? '立即注册' : '返回登录' }}</button></p>
      <small class="login-footnote">账户信息仅用于保存你的学习进度、练习和评估记录。</small>
    </section>
  </main>
</template>

<style scoped>
.login-page{min-height:100vh;display:grid;grid-template-columns:minmax(0,1fr) 440px;gap:clamp(28px,6vw,96px);align-items:center;padding:clamp(28px,7vw,88px) clamp(22px,8vw,120px);background:radial-gradient(circle at 18% 20%,rgba(52,124,92,.12),transparent 34%),var(--surface-soft)}.login-intro{min-width:0;max-width:690px;display:grid;gap:clamp(34px,7vh,74px);padding:10px}.login-brand{padding:0}.login-intro-copy{max-width:620px}.login-intro-copy h1{max-width:600px;margin:14px 0 18px;color:var(--text);font-size:clamp(34px,4.6vw,68px);line-height:1.08;letter-spacing:0}.login-intro-copy>p:not(.eyebrow){max-width:52ch;margin:0;color:var(--text-muted);font-size:15px;line-height:1.85}.login-route{display:flex;align-items:center;gap:15px;color:var(--text-muted);font-size:11px}.login-route span{display:inline-flex;align-items:center;gap:8px;white-space:nowrap}.login-route b{color:var(--accent);font-size:10px}.login-route i{width:38px;height:1px;background:var(--border-default)}.login-boundary{max-width:58ch;color:var(--text-subtle);font-size:10px;line-height:1.7}.login-panel{width:100%;padding:34px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-lg);box-shadow:var(--shadow-md)}.login-panel h2{margin:10px 0 8px;color:var(--text);font-size:28px}.login-subtitle{margin:0 0 26px;color:var(--text-muted);font-size:12px;line-height:1.7}.login-form{display:grid;gap:16px}.login-field{display:grid;gap:7px;color:var(--text-muted);font-size:11px}.login-field>div{display:flex;align-items:center;gap:9px;min-height:44px;padding:0 12px;color:var(--accent);background:var(--surface-soft);border:1px solid var(--border-default);border-radius:var(--radius-md)}.login-field input{width:100%;min-width:0;border:0;outline:0;background:transparent;color:var(--text);font-size:13px}.login-submit{width:100%;justify-content:space-between;min-height:46px;margin-top:4px}.login-error{margin:0;padding:10px 12px;color:var(--danger);background:var(--danger-soft);border:1px solid #efd5cc;border-radius:var(--radius-sm);font-size:11px;line-height:1.6}.login-switch{margin:22px 0 0;color:var(--text-muted);font-size:11px;text-align:center}.login-switch button{padding:0;border:0;background:transparent;color:var(--accent);font-weight:700;cursor:pointer}.login-footnote{display:block;margin-top:24px;color:var(--text-subtle);font-size:10px;text-align:center}@media(max-width:900px){.login-page{grid-template-columns:1fr;max-width:640px;margin:0 auto;padding:30px 20px}.login-intro{gap:28px}.login-intro-copy h1{font-size:40px}.login-boundary{display:none}}@media(max-width:520px){.login-page{padding:18px 14px}.login-intro-copy h1{font-size:32px}.login-route{gap:8px}.login-route i{width:15px}.login-route span{font-size:9px}.login-panel{padding:24px 19px}}
</style>
