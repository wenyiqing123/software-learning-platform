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
    router.replace('/')
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
    <div class="login-art" aria-hidden="true"><div class="login-orbit"></div><div class="login-orbit inner"></div><span class="login-node first">需求</span><span class="login-node second">设计</span><span class="login-node third">实现</span><span class="login-core"><BookOpenCheck :size="34" /></span></div>
    <section class="login-panel">
      <a class="brand login-brand" href="/"><span class="brand-mark"><GraduationCap :size="21" /></span><span><strong>知序</strong><small>软件工程学习平台</small></span></a>
      <p class="eyebrow">软件工程 · 课程学习空间</p>
      <h1>{{ mode === 'login' ? '欢迎回来' : '创建学习账户' }}</h1>
      <p class="login-subtitle">{{ mode === 'login' ? '登录后继续你的课程与知识图谱探索。' : '注册后即可记录练习、错题和学习建议。' }}</p>
      <form class="login-form" @submit.prevent="submit">
        <label v-if="mode === 'register'" class="login-field"><span>显示名称</span><div><UserRound :size="17" /><input v-model="displayName" required maxlength="80" autocomplete="nickname" placeholder="你的称呼" /></div></label>
        <label class="login-field"><span>用户名</span><div><UserRound :size="17" /><input v-model="username" required minlength="3" maxlength="60" autocomplete="username" placeholder="3-60 位字母、数字或符号" /></div></label>
        <label class="login-field"><span>密码</span><div><LockKeyhole :size="17" /><input v-model="password" required :minlength="mode === 'register' ? 8 : 1" maxlength="72" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" type="password" :placeholder="mode === 'register' ? '至少 8 位' : '输入密码'" /></div></label>
        <p v-if="error" class="login-error" role="alert">{{ error }}</p>
        <button class="button button-dark login-submit" type="submit" :disabled="busy"><span>{{ busy ? '请稍候' : mode === 'login' ? '登录学习平台' : '创建账户' }}</span><ArrowRight :size="17" /></button>
      </form>
      <p class="login-switch">{{ mode === 'login' ? '还没有学习账户？' : '已经注册过？' }} <button @click="mode = mode === 'login' ? 'register' : 'login'; error = ''">{{ mode === 'login' ? '立即注册' : '返回登录' }}</button></p>
      <small class="login-footnote">账户信息仅用于保存个人学习进度与答题记录。</small>
    </section>
  </main>
</template>

