<template>
  <main class="auth-workspace">
    <RouterLink to="/auth" class="auth-brand" aria-label="FlowForge">
      <img :src="logo" alt="FlowForge" />
    </RouterLink>

    <section class="auth-surface" aria-live="polite">
      <div class="auth-heading">
        <span class="section-kicker">Private AI Workspace</span>
        <h1>{{ auth.setupRequired ? '创建工作区所有者' : '回到你的工作空间' }}</h1>
        <p v-if="auth.setupRequired">第一次启动只需创建一个所有者账号，现有 Flow、Prompt 与运行历史会保留在这个工作区中。</p>
        <p v-else>登录后继续创建、执行和复用你的 AI 工作流。</p>
      </div>

      <div v-if="auth.connectionError" class="auth-notice error">
        <WarningFilled />
        <span>{{ auth.connectionError }}</span>
        <button type="button" @click="retryConnection">重试</button>
      </div>

      <form v-else class="auth-form" @submit.prevent="submit">
        <label v-if="auth.setupRequired">
          <span>显示名称</span>
          <input
            v-model="form.displayName"
            type="text"
            maxlength="80"
            autocomplete="name"
            placeholder="你的名字"
            autofocus
          />
        </label>
        <label>
          <span>邮箱</span>
          <input
            v-model="form.email"
            type="email"
            maxlength="254"
            autocomplete="email"
            placeholder="name@example.com"
            :autofocus="!auth.setupRequired"
          />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            minlength="10"
            maxlength="128"
            :autocomplete="auth.setupRequired ? 'new-password' : 'current-password'"
            placeholder="至少 10 个字符"
          />
        </label>
        <label v-if="auth.setupRequired">
          <span>确认密码</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            minlength="10"
            maxlength="128"
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </label>

        <p v-if="formError" class="auth-form-error">{{ formError }}</p>

        <button type="submit" class="primary-button auth-submit" :disabled="!canSubmit || auth.loading">
          <Loading v-if="auth.loading" class="auth-spinner" />
          <span>{{ auth.setupRequired ? '创建并进入 FlowForge' : '登录 FlowForge' }}</span>
          <Right v-if="!auth.loading" />
        </button>
      </form>
    </section>

  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, Right, WarningFilled } from '@element-plus/icons-vue'
import logo from '@/assets/icons/logo.png'
import { useAuthStore } from '@/stores/auth'
import { safeAuthRedirect } from '@/utils/authRedirect'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const formError = ref('')
const form = reactive({
  displayName: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const canSubmit = computed(() => {
  const credentialsReady = Boolean(form.email.trim() && form.password.length >= 10)
  if (!auth.setupRequired) {
    return credentialsReady
  }
  return Boolean(
    credentialsReady &&
      form.displayName.trim() &&
      form.confirmPassword.length >= 10
  )
})

async function submit() {
  formError.value = ''
  if (auth.setupRequired && form.password !== form.confirmPassword) {
    formError.value = '两次输入的密码不一致'
    return
  }

  const result = auth.setupRequired
    ? await auth.setup({
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        password: form.password
      })
    : await auth.login({
        email: form.email.trim(),
        password: form.password
      })

  if (!result.ok) {
    formError.value = result.message
    return
  }
  await router.replace(safeAuthRedirect(route.query.redirect))
}

async function retryConnection() {
  await auth.bootstrap(true)
}

</script>
