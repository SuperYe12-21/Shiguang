<template>
  <nav class="bottom-nav sg-glass">
    <button class="nav-item" :class="{ active: name === 'home' }" @click="go('/feed')">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
        <path d="M12 3l10 9h-3v9h-5v-6h-4v6H5v-9H2l10-9z" />
      </svg>
      <span>首页</span>
    </button>

    <button class="nav-item disabled" @click="todo('朋友')">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
        <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z" />
      </svg>
      <span>朋友</span>
    </button>

    <button class="nav-item publish" @click="todo('发布')">
      <span class="publish-circle">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
          <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
        </svg>
      </span>
      <span>发布</span>
    </button>

    <button class="nav-item disabled" @click="todo('消息')">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
        <path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" />
        <circle cx="8.5" cy="11.5" r="1.4" fill="#fff" />
        <circle cx="12" cy="11.5" r="1.4" fill="#fff" />
        <circle cx="15.5" cy="11.5" r="1.4" fill="#fff" />
      </svg>
      <span>消息</span>
    </button>

    <button class="nav-item disabled" @click="todo('我')">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
      </svg>
      <span>我</span>
    </button>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const name = 'home'

function go(path) {
  router.push(path)
}

function todo(label) {
  ElMessage.info(`${label}功能开发中，敬请期待`)
}
</script>

<style scoped>
.bottom-nav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: calc(var(--sg-nav-h) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid var(--sg-line);
  border-radius: 18px 18px 0 0;
  box-shadow: 0 -6px 24px rgba(38, 34, 31, 0.06);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--sg-text-2);
  padding: 4px 8px;
  min-width: 56px;
}

.nav-item.active {
  color: var(--sg-primary-deep);
  font-weight: 600;
}

.nav-item.disabled {
  opacity: 0.55;
}

.publish-circle {
  width: 42px;
  height: 42px;
  margin-top: -18px;
  border-radius: 50%;
  background: var(--sg-gradient-deep);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(232, 75, 75, 0.35);
}
</style>