<template>
  <aside class="pc-nav">
    <div class="nav-inner">
      <div class="nav-logo">
        <span class="logo-mark">拾</span>
        <span class="logo-text">拾光</span>
      </div>

      <button class="nav-item" :class="{ active: name === 'home' }" @click="go('/feed')">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M12 3l10 9h-3v9h-5v-6h-4v6H5v-9H2l10-9z" />
        </svg>
        <span>首页</span>
      </button>

      <button class="nav-item" @click="todo('发布')">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
        </svg>
        <span>发布</span>
      </button>

      <button class="nav-item" @click="todo('消息')">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" />
        </svg>
        <span>消息</span>
      </button>

      <button class="nav-item" @click="todo('我')">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
        </svg>
        <span>我</span>
      </button>
    </div>
  </aside>
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
.pc-nav {
  width: 208px;
  flex-shrink: 0;
  padding: 18px 14px;
}

.nav-inner {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 10px;
  border-radius: var(--sg-radius-lg);
  background: var(--sg-glass);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow: var(--sg-shadow);
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px 18px;
}

.logo-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 19px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: var(--sg-radius);
  color: var(--sg-text-2);
  font-size: 15px;
  transition: background 0.2s, color 0.2s;
}

.nav-item:hover {
  background: rgba(255, 92, 92, 0.08);
  color: var(--sg-text);
}

.nav-item.active {
  background: var(--sg-primary-soft);
  color: var(--sg-primary-deep);
  font-weight: 600;
}
</style>