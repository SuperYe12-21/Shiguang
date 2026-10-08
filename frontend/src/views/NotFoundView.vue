<template>
  <div class="nf-page">
    <div class="nf-card">
      <div class="nf-mark">拾</div>
      <h1 class="nf-code">404</h1>
      <p class="nf-title">这里没有拾到任何光</p>
      <p class="nf-desc">你要找的页面可能已被删除，或者链接输错了</p>
      <div class="nf-actions">
        <button class="nf-btn primary" @click="goHome">回到首页</button>
        <button class="nf-btn" @click="goBack">返回上一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/feed')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/feed')
}
</script>

<style scoped>
.nf-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sg-gradient);
  padding: 24px;
}

.nf-card {
  width: min(420px, 100%);
  background: var(--sg-card);
  border-radius: var(--sg-radius-lg);
  box-shadow: var(--sg-shadow-lg);
  padding: 40px 32px 32px;
  text-align: center;
}

.nf-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: var(--sg-primary-soft);
  color: var(--sg-primary);
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nf-code {
  margin: 0;
  font-size: 44px;
  font-weight: 800;
  letter-spacing: 2px;
  color: var(--sg-text);
}

.nf-title {
  margin: 6px 0 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--sg-text);
}

.nf-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--sg-text-2);
  line-height: 1.6;
}

.nf-actions {
  margin-top: 26px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.nf-btn {
  height: 40px;
  padding: 0 22px;
  border-radius: var(--sg-radius-full);
  border: 1px solid var(--sg-line);
  background: transparent;
  color: var(--sg-text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nf-btn:hover {
  border-color: var(--sg-primary);
  color: var(--sg-primary);
}

.nf-btn.primary {
  background: var(--sg-primary);
  border-color: var(--sg-primary);
  color: #fff;
}

.nf-btn.primary:hover {
  background: var(--sg-primary-deep);
  border-color: var(--sg-primary-deep);
  color: #fff;
}
</style>
