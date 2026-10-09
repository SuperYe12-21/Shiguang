<template>
  <div class="ad" :class="{ dark: !isPc }">
    <header class="ad-top">
      <button class="ad-back" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <h1 class="ad-title">作品管理</h1>
      <span class="ad-role">管理员</span>
    </header>

    <main class="ad-body">
      <div v-if="denied" class="ad-denied">
        <p class="ad-denied-title">没有访问权限</p>
        <p class="ad-denied-desc">当前账号不是管理员</p>
        <button class="ad-btn ad-btn-primary" @click="router.replace('/me')">回到个人主页</button>
      </div>

      <template v-else>
        <section class="ad-filters">
          <label class="ad-field">
            <span class="ad-field-label">状态</span>
            <select v-model="filters.status" class="ad-control">
              <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </label>
          <label class="ad-field">
            <span class="ad-field-label">可见性</span>
            <select v-model="filters.visibility" class="ad-control">
              <option v-for="opt in visibilityOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </label>
          <label class="ad-field ad-field-wide">
            <span class="ad-field-label">作者</span>
            <input v-model.trim="filters.authorKeyword" class="ad-control" type="text" placeholder="昵称或手机号" @keyup.enter="search" />
          </label>
          <label class="ad-field">
            <span class="ad-field-label">作品 ID</span>
            <input v-model.trim="filters.postId" class="ad-control" type="text" inputmode="numeric" placeholder="精确查询" @keyup.enter="search" />
          </label>
          <div class="ad-filter-ops">
            <button class="ad-btn ad-btn-primary" :disabled="loading" @click="search">{{ loading ? '查询中…' : '查询' }}</button>
            <button class="ad-btn" :disabled="loading" @click="reset">重置</button>
          </div>
        </section>

        <p class="ad-summary">
          已加载 {{ items.length }} 条
          <span v-if="items.length && !hasMore">· 没有更多了</span>
        </p>

        <section class="ad-list">
          <article v-for="p in items" :key="p.id" class="ad-card">
            <img class="ad-cover" :src="p.coverUrl || placeholder" alt="封面" loading="lazy" />
            <div class="ad-main">
              <div class="ad-line1">
                <span class="ad-pid">#{{ p.id }}</span>
                <span class="ad-badge" :class="'ad-status-' + p.status">{{ statusText(p.status) }}</span>
                <span class="ad-badge ad-badge-plain">{{ p.visibility === 'PRIVATE' ? '仅自己可见' : '公开' }}</span>
                <span class="ad-badge ad-badge-plain">{{ p.type === 'VIDEO' ? '视频' : '图文' }}</span>
              </div>
              <p class="ad-name">{{ p.title || '（无标题）' }}</p>
              <p class="ad-author">{{ p.authorNickname }} · {{ p.authorPhone || '—' }}</p>
              <p class="ad-stats">
                <span>赞 {{ formatCount(p.likeCount) }}</span>
                <span>评 {{ formatCount(p.commentCount) }}</span>
                <span>播 {{ formatCount(p.viewCount) }}</span>
                <span>{{ formatDate(p.createdAt) }}</span>
              </p>
              <p v-if="p.status === 'BLOCKED' && p.blockReason" class="ad-note">下架原因：{{ p.blockReason }}</p>
              <p v-if="p.status === 'FAILED' && p.failReason" class="ad-note ad-note-fail">失败原因：{{ p.failReason }}</p>
            </div>
            <div class="ad-ops">
              <button v-if="p.status === 'PUBLISHED'" class="ad-op ad-op-warn" @click="onBlock(p)">下架</button>
              <button v-else-if="p.status === 'BLOCKED'" class="ad-op ad-op-ok" @click="onUnblock(p)">恢复</button>
              <button class="ad-op ad-op-danger" @click="onDelete(p)">删除</button>
            </div>
          </article>

          <p v-if="!items.length && !loading" class="ad-empty">没有符合条件的作品</p>
          <button v-if="hasMore" class="ad-more" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { blockAdminPost, deleteAdminPost, fetchAdminMe, fetchAdminPosts, unblockAdminPost } from '../api/admin'

const router = useRouter()

const isPc = ref(typeof window !== 'undefined' ? window.innerWidth >= 768 : false)
function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

const denied = ref(false)
const loading = ref(false)
const items = ref([])
const cursor = ref(null)
const hasMore = ref(false)

const filters = reactive({
  status: 'ALL',
  visibility: 'ALL',
  authorKeyword: '',
  postId: ''
})

const statusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'FAILED', label: '失败' },
  { value: 'BLOCKED', label: '已下架' }
]

const visibilityOptions = [
  { value: 'ALL', label: '全部可见性' },
  { value: 'PUBLIC', label: '公开' },
  { value: 'PRIVATE', label: '仅自己可见' }
]

const placeholder = 'data:image/svg+xml;utf8,' + encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='80' height='104'><rect width='80' height='104' rx='10' fill='#2b2b31'/><text x='40' y='62' font-size='26' text-anchor='middle' fill='#6f6f7a' font-family='sans-serif'>拾</text></svg>"
)

function statusText(status) {
  return { PUBLISHED: '已发布', PROCESSING: '处理中', FAILED: '失败', BLOCKED: '已下架' }[status] || status
}

function formatCount(n) {
  const value = Number(n || 0)
  if (value >= 10000) return (value / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  return String(value)
}

function formatDate(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    const data = await fetchAdminPosts({
      status: filters.status === 'ALL' ? '' : filters.status,
      visibility: filters.visibility === 'ALL' ? '' : filters.visibility,
      authorKeyword: filters.authorKeyword || '',
      postId: filters.postId || undefined,
      cursor: reset ? '' : cursor.value || '',
      limit: 20
    })
    const rows = data.items || []
    items.value = reset ? rows : items.value.concat(rows)
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } finally {
    loading.value = false
  }
}

function search() {
  if (filters.postId && !/^\d+$/.test(filters.postId)) {
    ElMessage.warning('作品 ID 只能是数字')
    return
  }
  load(true)
}

function loadMore() {
  load(false)
}

function reset() {
  filters.status = 'ALL'
  filters.visibility = 'ALL'
  filters.authorKeyword = ''
  filters.postId = ''
  load(true)
}

async function onBlock(post) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      '下架后其他用户看不到这条作品，作者仍能看到并看到你填写的原因。',
      '下架作品 #' + post.id,
      {
        confirmButtonText: '确认下架',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '下架原因（可选，最多 200 字）',
        inputValidator: (value) => !value || value.length <= 200 || '最多 200 字',
        customClass: 'ad-prompt'
      }
    )
    reason = (res.value || '').trim()
  } catch (e) {
    return
  }
  await blockAdminPost(post.id, reason)
  post.status = 'BLOCKED'
  post.blockReason = reason
  ElMessage.success('已下架')
}

async function onUnblock(post) {
  await unblockAdminPost(post.id)
  post.status = 'PUBLISHED'
  post.blockReason = ''
  ElMessage.success('已恢复为公开作品')
}

async function onDelete(post) {
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复（含 OSS 文件、点赞 / 收藏 / 评论），确定删除作品 #${post.id} 吗？`,
      '删除作品',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  await deleteAdminPost(post.id)
  items.value = items.value.filter((row) => row.id !== post.id)
  ElMessage.success('已删除')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/me')
}

onMounted(async () => {
  window.addEventListener('resize', syncIsPc)
  try {
    const data = await fetchAdminMe()
    denied.value = !data.admin
  } catch (e) {
    denied.value = true
  }
  if (!denied.value) await load(true)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.ad {
  min-height: 100vh;
  background: #f7f7f5;
  color: #26221f;
  font-family: var(--sg-font);
  -webkit-font-smoothing: antialiased;
}

.ad.dark {
  background: #0b0b0e;
  color: #f5f2ee;
}

.ad-top {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 22px 18px 12px;
}

.ad.dark .ad-top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 14px 16px;
  background: rgba(11, 11, 14, 0.92);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.ad-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 16px 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(38, 34, 31, 0.1);
  background: #fff;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.ad.dark .ad-back {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.1);
}

.ad-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 800;
}

.ad-role {
  flex: none;
  width: 84px;
  text-align: right;
  font-size: 12px;
  font-weight: 700;
  color: #ff5c5c;
}

.ad-body {
  max-width: 1080px;
  margin: 0 auto;
  padding: 6px 18px 60px;
}

/* ---------- 筛选 ---------- */
.ad-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(38, 34, 31, 0.06);
}

.ad.dark .ad-filters {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: none;
}

.ad-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 132px;
}

.ad-field-wide {
  flex: 1;
  min-width: 190px;
}

.ad-field-label {
  font-size: 12px;
  opacity: 0.55;
  padding-left: 2px;
}

.ad-control {
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid rgba(38, 34, 31, 0.12);
  background: #fdfcfb;
  color: inherit;
  font-size: 14px;
  font-family: inherit;
  outline: none;
}

.ad.dark .ad-control {
  background: rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.14);
}

.ad-control:focus {
  border-color: rgba(255, 92, 92, 0.55);
}

.ad-filter-ops {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

/* ---------- 按钮 ---------- */
.ad-btn {
  height: 38px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(38, 34, 31, 0.12);
  background: #fff;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.ad.dark .ad-btn {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
}

.ad-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.ad-btn-primary {
  border-color: transparent;
  background: linear-gradient(135deg, #ff7373, #ff5c5c);
  color: #fff;
  box-shadow: 0 6px 16px rgba(255, 92, 92, 0.28);
}

.ad.dark .ad-btn-primary {
  background: linear-gradient(135deg, #ff7373, #ff5c5c);
}

/* ---------- 列表 ---------- */
.ad-summary {
  margin: 16px 2px 10px;
  font-size: 12px;
  opacity: 0.55;
}

.ad-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ad-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 14px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(38, 34, 31, 0.05);
}

.ad.dark .ad-card {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: none;
}

.ad-cover {
  flex: none;
  width: 64px;
  height: 84px;
  border-radius: 10px;
  object-fit: cover;
  background: rgba(0, 0, 0, 0.2);
}

.ad-main {
  flex: 1;
  min-width: 0;
}

.ad-line1 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.ad-pid {
  font-size: 12px;
  font-weight: 700;
  opacity: 0.5;
}

.ad-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.6;
}

.ad-badge-plain {
  background: rgba(38, 34, 31, 0.06);
  color: inherit;
  opacity: 0.7;
    font-weight: 600;
}

.ad.dark .ad-badge-plain {
  background: rgba(255, 255, 255, 0.1);
}

.ad-status-PUBLISHED {
  background: rgba(46, 176, 108, 0.14);
  color: #2eb06c;
}

.ad-status-PROCESSING {
  background: rgba(52, 130, 246, 0.14);
  color: #3482f6;
}

.ad-status-FAILED {
  background: rgba(240, 82, 82, 0.14);
  color: #f05252;
}

.ad-status-BLOCKED {
  background: rgba(255, 152, 0, 0.16);
  color: #e08600;
}

.ad-name {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ad-author {
  margin-top: 4px;
  font-size: 12.5px;
  opacity: 0.62;
}

.ad-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.5;
}

.ad-note {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  background: rgba(255, 152, 0, 0.1);
  color: #c47800;
}

.ad.dark .ad-note {
  background: rgba(255, 152, 0, 0.14);
  color: #ffb84d;
}

.ad-note-fail {
  background: rgba(240, 82, 82, 0.1);
  color: #d63b3b;
}

.ad.dark .ad-note-fail {
  background: rgba(240, 82, 82, 0.16);
  color: #ff8a8a;
}

/* ---------- 操作 ---------- */
.ad-ops {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 84px;
}

.ad-op {
  height: 34px;
  border-radius: 999px;
  border: none;
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
}

.ad-op-warn {
  background: rgba(255, 152, 0, 0.14);
  color: #e08600;
}

.ad-op-ok {
  background: rgba(46, 176, 108, 0.14);
  color: #2eb06c;
}

.ad-op-danger {
  background: rgba(240, 82, 82, 0.12);
  color: #f05252;
}

.ad-empty {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  opacity: 0.5;
}

.ad-more {
  align-self: center;
  margin-top: 6px;
  height: 38px;
  padding: 0 26px;
  border-radius: 999px;
  border: 1px solid rgba(38, 34, 31, 0.12);
  background: #fff;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.ad.dark .ad-more {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
}

/* ---------- 无权限 ---------- */
.ad-denied {
  margin-top: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.ad-denied-title {
  font-size: 17px;
  font-weight: 800;
}

.ad-denied-desc {
  font-size: 13px;
  opacity: 0.55;
  margin-bottom: 8px;
}

@media (max-width: 767px) {
  .ad-body {
    padding: 6px 14px 60px;
  }

  .ad-card {
    flex-wrap: wrap;
  }

  .ad-cover {
    width: 58px;
    height: 76px;
  }

  .ad-ops {
    flex-direction: row;
    width: 100%;
    justify-content: flex-end;
    gap: 10px;
  }

  .ad-op {
    min-width: 78px;
    padding: 0 14px;
  }

  .ad-field {
    flex: 1 1 40%;
  }

  .ad-field-wide {
    flex: 1 1 100%;
  }
}
</style>
