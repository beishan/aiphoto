<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { authApi } from '@/api/authApi'

const router = useRouter()
const message = useMessage()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const isRegister = ref(false)

async function handleSubmit() {
  if (!form.value.username || !form.value.password) {
    message.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    if (isRegister.value) {
      await authApi.register(form.value.username, form.value.password)
      message.success('注册成功，请登录')
      isRegister.value = false
    } else {
      const { data } = await authApi.login(form.value.username, form.value.password)
      localStorage.setItem('token', data.token)
      message.success('登录成功')
      router.push('/')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg"></div>

    <div class="login-content">
      <div class="login-header">
        <div class="app-icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48">
            <path d="M2 5a3 3 0 013-3h14a3 3 0 013 3v10a3 3 0 01-3 3H5a3 3 0 01-3-3V5zm5.5 2a2.5 2.5 0 110 5 2.5 2.5 0 010-5zM4 15l4.5-6 3.5 4.5L14 11l4 6H4z" />
          </svg>
        </div>
        <h1 class="app-name">MemoryVault</h1>
        <p class="app-desc">你的私人 AI 相册</p>
      </div>

      <div class="login-card glass">
        <div class="card-header">
          <h2>{{ isRegister ? '创建账号' : '登录' }}</h2>
        </div>

        <form @submit.prevent="handleSubmit" class="login-form">
          <div class="input-group">
            <input
              v-model="form.username"
              type="text"
              placeholder="用户名"
              class="ios-input"
              autocomplete="username"
            />
          </div>
          <div class="input-group">
            <input
              v-model="form.password"
              type="password"
              placeholder="密码"
              class="ios-input"
              autocomplete="current-password"
              @keyup.enter="handleSubmit"
            />
          </div>

          <button
            type="submit"
            class="login-btn"
            :class="{ loading }"
            :disabled="loading"
          >
            <span v-if="loading" class="spinner"></span>
            <span v-else>{{ isRegister ? '注册' : '登录' }}</span>
          </button>
        </form>

        <div class="login-footer">
          <button class="switch-btn" @click="isRegister = !isRegister">
            {{ isRegister ? '已有账号？登录' : '没有账号？注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #0a1628 0%, #1a0a2e 50%, #0a1628 100%);
}

.login-bg::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 20%, rgba(10, 132, 255, 0.15) 0%, transparent 50%),
              radial-gradient(circle at 70% 80%, rgba(175, 82, 222, 0.1) 0%, transparent 50%);
  animation: bgShift 20s ease-in-out infinite alternate;
}

@keyframes bgShift {
  0% { transform: translate(0, 0); }
  100% { transform: translate(-5%, -5%); }
}

.login-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  padding: 20px;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--accent) 0%, #5e5ce6 100%);
  color: white;
  margin-bottom: 16px;
  box-shadow: 0 8px 32px rgba(10, 132, 255, 0.3);
}

.app-name {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin-bottom: 4px;
}

.app-desc {
  font-size: 15px;
  color: var(--text-secondary);
}

.login-card {
  border-radius: var(--radius-xl);
  padding: 32px 24px;
  border: 0.5px solid var(--glass-border);
}

.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.card-header h2 {
  font-size: 22px;
  font-weight: 600;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group {
  position: relative;
}

.ios-input {
  width: 100%;
  height: 50px;
  padding: 0 16px;
  background: var(--bg-tertiary);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 16px;
  font-family: inherit;
  transition: border-color 0.2s, background 0.2s;
  outline: none;
}

.ios-input::placeholder {
  color: var(--text-tertiary);
}

.ios-input:focus {
  border-color: var(--accent);
  background: rgba(10, 132, 255, 0.08);
}

.login-btn {
  width: 100%;
  height: 50px;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: white;
  font-size: 17px;
  font-weight: 600;
  font-family: inherit;
  border: none;
  cursor: pointer;
  transition: background 0.2s, transform 0.1s;
  margin-top: 4px;
}

.login-btn:hover {
  background: var(--accent-hover);
}

.login-btn:active {
  transform: scale(0.98);
}

.login-btn.loading {
  opacity: 0.7;
  pointer-events: none;
}

.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.login-footer {
  text-align: center;
  margin-top: 20px;
}

.switch-btn {
  color: var(--accent);
  font-size: 15px;
  font-family: inherit;
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
}

.switch-btn:hover {
  text-decoration: underline;
}
</style>
