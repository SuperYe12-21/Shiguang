<template>
  <div class="login-page">
    <div class="login-hero">
      <div class="login-logo">
        <span class="logo-dot"></span>
        <h1>拾光</h1>
        <p>记录生活高光时刻</p>
      </div>

      <div class="login-card sg-card">
        <div class="field">
          <label>手机号</label>
          <input
            v-model="phone"
            type="tel"
            maxlength="11"
            placeholder="请输入手机号"
            :class="{ invalid: touched && !phoneValid }"
            @input="touched = true"
          />
          <p v-if="touched && !phoneValid" class="field-error">请输入 11 位手机号</p>
        </div>

        <div class="field">
          <label>验证码</label>
          <div class="code-row">
            <input
              v-model="code"
              type="tel"
              maxlength="6"
              placeholder="请输入验证码"
              @keyup.enter="doLogin"
            />
            <button
              class="send-btn"
              :disabled="!phoneValid || counting > 0 || sending"
              @click="sendCode"
            >
              {{ counting > 0 ? counting + 's 后重发' : '获取验证码' }}
            </button>
          </div>
          <p class="field-hint">开发环境验证码为 123456</p>
        </div>

        <button class="sg-btn-primary login-btn" :disabled="!canSubmit || loading" @click="doLogin">
          {{ loading ? '登录中…' : '登录 / 注册' }}
        </button>
        <p class="agree-hint">未注册的手机号验证通过后将自动注册</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const phone = ref('')
const code = ref('')
const touched = ref(false)
const counting = ref(0)
const sending = ref(false)
const loading = ref(false)
let timer = null

const phoneValid = computed(() => /^1\d{10}$/.test(phone.value))
const canSubmit = computed(() => phoneValid.value && code.value.length >= 4)

async function sendCode() {
  sending.value = true
  try {
    await auth.sendCode(phone.value)
    ElMessage.success('验证码已发送')
    counting.value = 60
    timer = setInterval(() => {
      counting.value -= 1
      if (counting.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    sending.value = false
  }
}

async function doLogin() {
  if (!canSubmit.value || loading.value) return
  loading.value = true
  try {
    await auth.login(phone.value, code.value)
    ElMessage.success('欢迎回来')
    router.replace('/feed')
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.login-page {
  min-height: 100%;
  background: var(--sg-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-hero {
  width: 100%;
  max-width: 400px;
}

.login-logo {
  text-align: center;
  margin-bottom: 32px;
}

.logo-dot {
  display: inline-block;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: var(--sg-gradient-deep);
  box-shadow: var(--sg-shadow);
  margin-bottom: 12px;
}

.login-logo h1 {
  font-size: 32px;
  letter-spacing: 8px;
  color: var(--sg-text);
  margin-left: 8px;
}

.login-logo p {
  color: var(--sg-text-2);
  margin-top: 6px;
  letter-spacing: 2px;
}

.login-card {
  padding: 28px 24px;
  border-radius: var(--sg-radius-lg);
}

.field {
  margin-bottom: 18px;
}

.field label {
  display: block;
  font-size: 13px;
  color: var(--sg-text-2);
  margin-bottom: 8px;
}

.field input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1.5px solid var(--sg-line);
  border-radius: var(--sg-radius);
  font-size: 16px;
  background: var(--sg-bg);
  outline: none;
  transition: border-color 0.2s;
}

.field input:focus {
  border-color: var(--sg-primary);
  background: #fff;
}

.field input.invalid {
  border-color: var(--sg-primary);
}

.field-error {
  color: var(--sg-primary-deep);
  font-size: 12px;
  margin-top: 6px;
}

.field-hint {
  color: var(--sg-text-3);
  font-size: 12px;
  margin-top: 6px;
}

.code-row {
  display: flex;
  gap: 10px;
}

.code-row input {
  flex: 1;
}

.send-btn {
  flex-shrink: 0;
  height: 46px;
  padding: 0 16px;
  border-radius: var(--sg-radius);
  background: var(--sg-primary-soft);
  color: var(--sg-primary-deep);
  font-size: 14px;
  font-weight: 600;
  transition: background 0.2s;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:not(:disabled):active {
  background: #ffd9d4;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  font-size: 16px;
}

.agree-hint {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 12px;
  margin-top: 14px;
}

@media (min-width: 768px) {
  .login-card {
    padding: 36px 32px;
  }
}
</style>