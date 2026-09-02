<template>
  <div class="profile-page" :class="{ 'pc-mode': isPc }">
    <!-- PC：方案 A 经典分栏 -->
    <template v-if="isPc">
      <div class="pc-shell">
        <aside class="pc-side">
          <div class="pc-logo" @click="router.push('/feed')">拾</div>
          <button class="pc-side-btn" @click="router.push('/feed')">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"/></svg>
            <span>首页</span>
          </button>
          <button class="pc-side-btn" @click="router.push('/publish')">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <span>发布</span>
          </button>
          <button class="pc-side-btn" @click="msgTodo">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>
            <span>消息</span>
          </button>
          <div class="pc-side-grow"></div>
          <button class="pc-side-btn" :class="{ on: isMe }" @click="goMe">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
            <span>我</span>
          </button>
        </aside>
        <div class="pc-main">
          <button class="pc-back" aria-label="返回" @click="goBack">
            <svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
            返回
          </button>
          <div class="pc-banner"></div>
          <header class="pc-head">
            <img class="pc-avatar" :src="profile.avatarUrl || fallbackAvatar" alt="头像" />
            <div class="pc-head-info">
              <div class="pc-hrow">
                <h1 class="pc-nick">{{ displayName || '拾光用户' }}</h1>
                <span v-if="remark && !isMe" class="pc-remark">备注</span>
                <span class="pc-uid">拾光号 {{ profile.id }}</span>
                <div class="pc-actions">
                  <template v-if="isMe">
                    <button class="pc-btn pc-btn-ghost" @click="shareProfile">分享主页</button>
                    <button class="pc-btn pc-btn-primary" @click="openEdit">编辑资料</button>
                  </template>
                  <template v-else>
                    <button class="pc-btn pc-btn-ghost" @click="openRemark">设置备注</button>
                    <button class="pc-btn pc-btn-ghost" @click="shareProfile">分享主页</button>
                    <button class="pc-btn pc-btn-primary" :class="{ 'pc-btn-on': profile.followedByMe }" @click="toggleFollow">{{ profile.followedByMe ? '已关注' : '+ 关注' }}</button>
                  </template>
                </div>
              </div>
              <p v-if="profile.bio" class="pc-bio">{{ profile.bio }}</p>
              <p class="pc-join">加入拾光 · {{ formatDate(profile.createdAt) }}</p>
              <div class="pc-stats">
                <div class="pc-stat"><b>{{ profile.postCount ?? 0 }}</b><span>作品</span></div>
                <div class="pc-stat"><b>{{ formatCount(profile.likeCount) }}</b><span>获赞</span></div>
                <div class="pc-stat pc-stat-link" @click="goFollowing"><b>{{ formatCount(profile.followingCount) }}</b><span>关注</span></div>
                <div class="pc-stat pc-stat-link" @click="goFollowers"><b>{{ formatCount(profile.followerCount) }}</b><span>粉丝</span></div>
              </div>
            </div>
          </header>
          <nav class="pc-tabs">
            <div class="pc-tab" :class="{ on: activeTab === 'posts' }" @click="switchTab('posts')">作品</div>
            <div class="pc-tab" :class="{ on: activeTab === 'likes' }" @click="switchTab('likes')">点赞</div>
            <div class="pc-tab" :class="{ on: activeTab === 'favorites' }" @click="switchTab('favorites')">收藏</div>
          </nav>
          <section class="pc-grid-wrap">
            <div v-if="activeTab === 'favorites'" class="pc-empty">
              <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg></span>
              <p>还没有收藏的作品</p>
            </div>
            <template v-else-if="activeTab === 'likes' && !likes.length && !likesLoading">
              <div class="pc-empty">
                <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg></span>
                <p>还没有点赞的作品</p>
              </div>
            </template>
            <div v-else-if="!gridItems.length && activeTab === 'posts'" class="pc-empty">
              <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></span>
              <p>还没有作品，去发布第一条拾光吧</p>
            </div>
            <div v-else class="pc-grid">
              <div v-for="p in gridItems" :key="p.id" class="pc-cell" @click="openGridPost(p)">
                <img :src="coverOf(p)" :alt="p.title || '作品'" loading="lazy" />
                <span class="pc-cell-ov">
                  <span v-if="p.type === 'VIDEO'" class="pc-ov-tag pc-ov-play"><svg viewBox="0 0 24 24" width="11" height="11" fill="#fff"><path d="M8 5v14l11-7z"/></svg></span>
                  <span v-if="p.type === 'IMAGE' && (p.images || []).length > 1" class="pc-ov-tag pc-ov-multi">
                    <svg viewBox="0 0 24 24" width="11" height="11" fill="#fff"><path d="M4 5h16v2H4V5zm0 6h16v2H4v-2zm0 6h10v2H4v-2z"/></svg>
                  </span>
                  <span class="pc-ov-tag pc-ov-like"><svg viewBox="0 0 24 24" width="10" height="10" fill="#fff"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>{{ formatCount(p.likeCount) }}</span>
                </span>
              </div>
            </div>
            <div class="pc-more">
              <span v-if="loadingMore || likesLoading">加载中…</span>
              <span v-else-if="activeTab === 'posts' && posts.length && !hasMore">— 没有更多了 —</span>
              <span v-else-if="activeTab === 'likes' && likes.length && !likesHasMore">— 没有更多了 —</span>
            </div>
          </section>
        </div>
      </div>
    </template>

    <!-- 移动端 -->
    <template v-else>
    <!-- 顶栏 -->
    <header class="pf-top">
      <button class="pf-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span class="pf-back-text">返回</span>
      </button>
      <span class="pf-top-title">{{ displayName || '个人主页' }}</span>
      <button v-if="!isPc" class="pf-more-btn" aria-label="更多" @click="drawerOpen = true">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>
      </button>
    </header>

    <!-- 移动端横幅 -->
    <div v-if="!isPc" class="pf-banner"><div class="pf-banner-shine"></div></div>

    <!-- 用户信息 -->
    <section class="pf-head">
      <div class="pf-head-row">
        <img class="pf-avatar" :src="profile.avatarUrl || fallbackAvatar" alt="头像" />
        <div class="pf-head-side">
          <h2 class="pf-nickname">{{ displayName || '拾光用户' }}<span v-if="remark && !isMe" class="pf-remark-badge">备注</span></h2>
          <button v-if="isMe" class="pf-follow" @click="openEdit">编辑资料</button>
          <button v-else class="pf-follow" :class="{ 'pf-follow-on': profile.followedByMe }" @click="toggleFollow">{{ profile.followedByMe ? '已关注' : '+ 关注' }}</button>
        </div>
      </div>
      <p v-if="profile.bio" class="pf-bio">{{ profile.bio }}</p>
      <p class="pf-join">加入拾光 · {{ formatDate(profile.createdAt) }}</p>
      <div class="pf-stats">
        <div class="pf-stat"><b>{{ profile.postCount ?? 0 }}</b><span>作品</span></div>
        <div class="pf-stat"><b>{{ formatCount(profile.likeCount) }}</b><span>获赞</span></div>
        <div class="pf-stat pf-stat-link" @click="goFollowing"><b>{{ formatCount(profile.followingCount) }}</b><span>关注</span></div>
        <div class="pf-stat pf-stat-link" @click="goFollowers"><b>{{ formatCount(profile.followerCount) }}</b><span>粉丝</span></div>
      </div>
      <div class="pf-actions">
        <template v-if="isMe">
          <button class="pf-btn pf-btn-primary" @click="openEdit">编辑资料</button>
          <button class="pf-btn pf-btn-ghost" @click="logout">退出登录</button>
        </template>
        <template v-else>
          <button v-if="profile.followedByMe" class="pf-btn pf-btn-ghost" @click="toggleFollow">已关注</button>
          <button v-else class="pf-btn pf-btn-primary" @click="toggleFollow">+ 关注</button>
        </template>
      </div>
    </section>

    <!-- 移动端 Tab 栏 -->
    <nav v-if="!isPc" class="pf-tabs">
      <div class="pf-tab" :class="{ on: activeTab === 'posts' }" @click="switchTab('posts')">
        <svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 6h18v2H3V6zm0 5h18v2H3v-2zm0 5h12v2H3v-2z"/></svg>作品<span class="pf-tab-bar" />
      </div>
      <div class="pf-tab" :class="{ on: activeTab === 'likes' }" @click="switchTab('likes')">
        <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>点赞<span class="pf-tab-bar" />
      </div>
      <div class="pf-tab" :class="{ on: activeTab === 'favorites' }" @click="switchTab('favorites')">
        <svg viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>收藏<span class="pf-tab-bar" />
      </div>
    </nav>

    <!-- 作品网格 -->
    <section class="pf-grid">
      <template v-if="activeTab === 'favorites'">
        <div class="pf-empty-box">
          <span class="pf-empty-ico"><svg viewBox="0 0 24 24"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg></span>
          <p>还没有收藏的作品</p>
        </div>
      </template>
      <template v-else-if="activeTab === 'likes' && !likes.length && !likesLoading">
        <div class="pf-empty-box">
          <span class="pf-empty-ico"><svg viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg></span>
          <p>还没有点赞的作品</p>
        </div>
      </template>
      <template v-else>
        <div v-for="p in gridItems" :key="p.id" class="pf-cell" @click="openGridPost(p)">
          <img :src="coverOf(p)" :alt="p.title || '作品'" loading="lazy" />
          <span class="pf-cell-ov"><span class="pf-cell-play"><svg viewBox="0 0 24 24" width="14" height="14" fill="#fff"><path d="M8 5v14l11-7z"/></svg></span></span>
          <span v-if="p.type === 'VIDEO'" class="pf-cell-mark pf-cell-video">
            <svg viewBox="0 0 24 24" width="11" height="11" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
          </span>
          <span v-else-if="(p.images || []).length > 1" class="pf-cell-mark pf-cell-multi">{{ p.images.length }}</span>
          <span class="pf-cell-like">
            <svg viewBox="0 0 24 24" width="9" height="9" fill="#ff5c5c"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
            {{ formatCount(p.likeCount) }}
          </span>
        </div>
      </template>
    </section>
    <div v-if="activeTab === 'posts'">
      <div v-if="loadingMore" class="pf-more">加载中…</div>
      <div v-else-if="posts.length && !hasMore" class="pf-more">— 没有更多了 —</div>
      <div v-if="!posts.length && !loading" class="pf-empty">还没有作品，去发布第一条拾光吧</div>
    </div>
    <div v-else-if="activeTab === 'likes'">
      <div v-if="likesLoading" class="pf-more">加载中…</div>
      <div v-else-if="likes.length && !likesHasMore" class="pf-more">— 没有更多了 —</div>
    </div>
    </template>

    <!-- 编辑资料弹窗 -->
    <div v-if="editOpen" class="pf-modal" @click.self="editOpen = false">
      <div class="pf-modal-panel">
        <h3 class="pf-modal-title">编辑资料</h3>
        <div class="pf-edit-avatar" @click="pickAvatar">
          <img :src="editAvatar || profile.avatarUrl || fallbackAvatar" alt="头像" />
          <span class="pf-edit-avatar-tip">点击更换头像</span>
        </div>
        <input v-model="editNickname" class="pf-input" maxlength="30" placeholder="昵称" />
        <textarea v-model="editBio" class="pf-input pf-textarea" maxlength="120" rows="3" placeholder="一句话介绍自己"></textarea>
        <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onAvatarChange" />
        <div class="pf-modal-actions">
          <button class="pf-btn pf-btn-ghost" @click="editOpen = false">取消</button>
          <button class="pf-btn pf-btn-primary" :disabled="saving" @click="saveProfile">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>

    <!-- 设置备注弹窗 -->
    <div v-if="remarkOpen" class="pf-modal" @click.self="remarkOpen = false">
      <div class="pf-modal-panel">
        <h3 class="pf-modal-title">设置备注</h3>
        <p class="pf-remark-tip">备注仅保存在本设备，方便你识别这位用户</p>
        <input v-model="remarkText" class="pf-input" maxlength="30" placeholder="输入备注名" />
        <div class="pf-modal-actions">
          <button class="pf-btn pf-btn-ghost" @click="remarkOpen = false">取消</button>
          <button class="pf-btn pf-btn-primary" @click="saveRemark">{{ remarkText ? '保存' : '清除备注' }}</button>
        </div>
      </div>
    </div>

    <!-- 移动端右侧抽屉 -->
    <template v-if="!isPc">
      <div class="pf-mask" :class="{ 'pf-mask-show': drawerOpen }" @click="closeDrawer"></div>
      <aside class="pf-drawer" :class="{ 'pf-drawer-open': drawerOpen }" aria-label="更多菜单">
        <div class="pf-drawer-head">
          <span class="pf-drawer-title">更多</span>
          <button class="pf-drawer-close" aria-label="关闭" @click="closeDrawer">✕</button>
        </div>
        <div class="pf-drawer-body">
          <div v-if="isMe" class="pf-drawer-item" @click="onDrawerEdit">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.996.996 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg></span>
            <span>编辑资料</span>
            <span class="pf-drawer-arr"><svg viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg></span>
          </div>
          <div v-else class="pf-drawer-item" @click="onDrawerRemark">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z"/></svg></span>
            <span>设置备注</span>
            <span class="pf-drawer-arr"><svg viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg></span>
          </div>
          <div class="pf-drawer-item" @click="shareProfile">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/></svg></span>
            <span>分享主页</span>
            <span class="pf-drawer-arr"><svg viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg></span>
          </div>
          <div v-if="isMe" class="pf-drawer-item" @click="onDrawerSettings">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg></span>
            <span>账号设置</span>
            <span class="pf-drawer-arr"><svg viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg></span>
          </div>
        </div>
        <div class="pf-drawer-foot">拾光 · v1.0.0</div>
      </aside>
    </template>

    <BottomNav v-if="!isPc" :active="isMe ? 'me' : ''" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMe, fetchProfile, fetchUserLikes, fetchUserPosts, updateMe, followUser, unfollowUser } from '../api/user'
import { presignUpload } from '../api/posts'
import { useAuthStore } from '../stores/auth'
import BottomNav from '../components/mobile/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPc = ref(typeof window !== 'undefined' ? window.innerWidth >= 768 : false)
function syncIsPc() {
  isPc.value = window.innerWidth >= 768
  if (!isPc.value) closeDrawer()
}
const profile = ref({})
const posts = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const nextCursor = ref(null)
const hasMore = ref(true)
const meId = ref(null)
const activeTab = ref('posts')
const likes = ref([])
const likesLoading = ref(false)
const likesCursor = ref(null)
const likesHasMore = ref(true)
const drawerOpen = ref(false)
const remarkOpen = ref(false)
const remarkText = ref('')
const remark = ref('')

const editOpen = ref(false)
const saving = ref(false)
const editNickname = ref('')
const editBio = ref('')
const editAvatar = ref('')
const avatarInput = ref(null)
let newAvatarObject = ''

const isMe = computed(() => meId.value !== null && meId.value === profile.value.id)

const gridItems = computed(() => (activeTab.value === 'likes' ? likes.value : posts.value))

const displayName = computed(() => remark.value || profile.value.nickname || '')

const fallbackAvatar = computed(() => {
  const name = profile.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((profile.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

function coverOf(p) {
  if (p.type === 'VIDEO') return p.coverUrl || (p.images && p.images[0]) || ''
  return (p.images && p.images[0]) || p.coverUrl || ''
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function formatDate(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${da}`
}

async function resolveUserId() {
  if (route.params.id !== undefined && route.params.id !== 'me') return Number(route.params.id)
  if (!auth.isLoggedIn) {
    router.replace('/login')
    return null
  }
  const me = await fetchMe()
  meId.value = me.id
  return me.id
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await fetchProfile(profile.value.id)
    profile.value = data
  } catch (e) {
    ElMessage.error('加载主页失败')
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const data = await fetchUserPosts(profile.value.id, nextCursor.value, 12)
    const items = data.items || []
    const seen = new Set(posts.value.map((p) => p.id))
    for (const item of items) {
      if (!seen.has(item.id)) {
        posts.value.push(item)
        seen.add(item.id)
      }
    }
    nextCursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 静默，滚动可重试
  } finally {
    loadingMore.value = false
  }
}

function switchTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  if (tab === 'likes' && !likes.value.length && !likesLoading.value) loadLikes()
  if (tab === 'posts' && !posts.value.length && !loading.value && !loadingMore.value) loadPosts()
}

async function loadLikes() {
  if (likesLoading.value || !likesHasMore.value) return
  likesLoading.value = true
  try {
    const data = await fetchUserLikes(profile.value.id, likesCursor.value, 12)
    const items = data.items || []
    const seen = new Set(likes.value.map((p) => p.id))
    for (const item of items) {
      if (!seen.has(item.id)) {
        likes.value.push(item)
        seen.add(item.id)
      }
    }
    likesCursor.value = data.nextCursor || null
    likesHasMore.value = !!data.hasMore
  } catch (e) {
    // 静默，滚动可重试
  } finally {
    likesLoading.value = false
  }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    const data = profile.value.followedByMe
      ? await unfollowUser(profile.value.id)
      : await followUser(profile.value.id)
    profile.value.followedByMe = data.following
    profile.value.followerCount = (profile.value.followerCount || 0) + (data.following ? 1 : -1)
  } catch (e) {
    // 错误已提示
  }
}

function goBack() {
  const state = window.history.state
  const backPath = state && state.back
  // 上一页是作品流（/feed?userId=...）时，穿透它直达首页
  if (typeof backPath === 'string' && backPath.startsWith('/feed') && backPath.includes('userId=')) {
    router.replace('/feed')
  } else {
    router.back()
  }
}

function goMe() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push('/me')
}

function goFollowers() {
  router.push(`/user/${profile.value.id}/followers`)
}

function goFollowing() {
  router.push(`/user/${profile.value.id}/following`)
}

function msgTodo() {
  ElMessage.info('消息功能开发中，敬请期待')
}

function openRemark() {
  remarkText.value = remark.value || ''
  remarkOpen.value = true
}

function openPost(p) {
  router.push({ path: '/feed', query: { postId: p.id, userId: profile.value.id } })
}

function openGridPost(p) {
  if (activeTab.value === 'likes') {
    // 点赞列表：进入该用户的点赞列表流并定位到这条作品
    router.push({ path: '/feed', query: { postId: p.id, likesOf: profile.value.id } })
  } else {
    openPost(p)
  }
}

function openEdit() {
  editNickname.value = profile.value.nickname || ''
  editBio.value = profile.value.bio || ''
  editAvatar.value = profile.value.avatarUrl || ''
  newAvatarObject = ''
  editOpen.value = true
}

function pickAvatar() {
  avatarInput.value && avatarInput.value.click()
}

async function onAvatarChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  try {
    const dot = file.name.lastIndexOf('.')
    const extension = dot >= 0 ? file.name.slice(dot + 1) : ''
    const presign = await presignUpload('IMAGE', file.type, extension)
    await putFile(presign.uploadUrl, file)
    newAvatarObject = presign.objectName
    // 本地预览（对象名提交后端，返回时实时签名）
    editAvatar.value = URL.createObjectURL(file)
  } catch (err) {
    ElMessage.error('头像上传失败')
  }
}

function putFile(url, file) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error('HTTP ' + xhr.status))
    }
    xhr.onerror = () => reject(new Error('网络异常'))
    xhr.send(file)
  })
}

async function saveProfile() {
  const nickname = editNickname.value.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await updateMe({ nickname, bio: editBio.value.trim(), avatarUrl: newAvatarObject || profile.value.avatarUrl || '' })
    ElMessage.success('资料已更新')
    editOpen.value = false
    await loadProfile()
  } catch (e) {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

function logout() {
  auth.logout()
  router.replace('/login')
}

function closeDrawer() {
  drawerOpen.value = false
}

function onDrawerEdit() {
  closeDrawer()
  openEdit()
}

function onDrawerSettings() {
  closeDrawer()
  ElMessage.info('账号设置即将上线')
}

function loadRemark(userId) {
  try {
    remark.value = localStorage.getItem(`sg-remark-${userId}`) || ''
  } catch (e) {
    remark.value = ''
  }
}

function onDrawerRemark() {
  closeDrawer()
  remarkText.value = remark.value || ''
  remarkOpen.value = true
}

function saveRemark() {
  const text = remarkText.value.trim()
  try {
    if (text) localStorage.setItem(`sg-remark-${profile.value.id}`, text)
    else localStorage.removeItem(`sg-remark-${profile.value.id}`)
  } catch (e) {
    // 隐私模式等场景忽略
  }
  remark.value = text
  remarkOpen.value = false
  ElMessage.success(text ? '备注已保存' : '备注已清除')
}

async function shareProfile() {
  closeDrawer()
  const url = `${location.origin}/user/${profile.value.id}`
  const text = `${profile.value.nickname || '拾光用户'} 的拾光主页`
  if (navigator.share) {
    try {
      await navigator.share({ title: text, text, url })
      return
    } catch (e) {
      if (e && e.name === 'AbortError') return
      // 降级为复制链接
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('主页链接已复制')
  } catch (e) {
    ElMessage.error('分享失败')
  }
}

watch(drawerOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

let scrollHandler = null

onMounted(async () => {
  const userId = await resolveUserId()
  if (userId == null) return
  profile.value = { id: userId }
  loadRemark(userId)
  if (auth.isLoggedIn && meId.value === null) {
    try {
      const me = await fetchMe()
      meId.value = me.id
    } catch (e) {
      // 未登录时忽略
    }
  }
  await loadProfile()
  await loadPosts()
  scrollHandler = () => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 400) {
      if (activeTab.value === 'likes') loadLikes()
      else loadPosts()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
  window.removeEventListener('resize', syncIsPc)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: #0b0b0e;
  color: #fff;
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 20px);
}

.pf-top {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(11, 11, 14, 0.85);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.pf-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 14px 0 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  transition: background 0.2s, border-color 0.2s;
}

.pf-back:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.22);
}

.pf-back-text {
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

/* PC：返回按钮改为胶囊样式，带文字并留出边距，不再孤零零贴在角落 */
@media (min-width: 768px) {
  .pf-top {
    padding: 12px 28px;
  }

  .pf-back {
    width: auto;
    height: 38px;
    padding: 0 18px 0 10px;
    border-radius: 999px;
    gap: 6px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(255, 255, 255, 0.06);
    font-size: 13px;
    font-weight: 600;
    transition: background 0.2s, border-color 0.2s;
  }

  .pf-back:hover {
    background: rgba(255, 255, 255, 0.14);
    border-color: rgba(255, 255, 255, 0.22);
  }

  .pf-back-text {
    font-size: 13px;
    font-weight: 600;
    line-height: 1;
  }

  .pf-top-title {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    max-width: 40vw;
  }

}

.pf-top-title {
  flex: 1;
  text-align: center;
  margin: 0 4px;
  font-size: 15px;
  font-weight: 600;
  max-width: 60vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pf-more-btn {
  display: none;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex: none;
  transition: background 0.2s, border-color 0.2s;
}

.pf-more-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.22);
}

.pf-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 26px 20px 8px;
}

.pf-head-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.pf-head-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
}

.pf-follow {
  display: none;
}

.pf-avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.9);
  background: var(--sg-bg-warm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.pf-nickname {
  margin-top: 14px;
  font-size: 20px;
  font-weight: 700;
}

.pf-bio {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.75);
  text-align: center;
  max-width: 420px;
  word-break: break-word;
}

.pf-join {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-stats {
  display: flex;
  gap: 44px;
  margin-top: 18px;
}

.pf-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.pf-stat b {
  font-size: 17px;
  font-weight: 700;
}

.pf-stat span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.pf-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}

.pf-btn {
  padding: 8px 22px;
  border-radius: var(--sg-radius-full);
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.15s, background 0.2s;
}

.pf-btn:active {
  transform: scale(0.97);
}

.pf-btn-primary {
  background: var(--sg-primary);
  color: #fff;
}

.pf-btn-primary:hover {
  background: var(--sg-primary-deep);
}

.pf-btn-ghost {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.pf-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.18);
}

.pf-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  margin-top: 22px;
  padding: 0 3px;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.pf-cell {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #141210;
  overflow: hidden;
  cursor: pointer;
}

.pf-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.25s ease;
}

.pf-cell:hover img {
  transform: scale(1.04);
}

.pf-cell-mark {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  font-size: 10px;
  display: flex;
  align-items: center;
  gap: 3px;
}

.pf-cell-video {
  color: #fff;
}

.pf-cell-multi {
  color: #fff;
}

.pf-cell-like {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
  pointer-events: none;
}

.pf-cell-ov {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.28);
  opacity: 0;
  transition: opacity 0.25s;
}

.pf-cell:hover .pf-cell-ov {
  opacity: 1;
}

.pf-cell-play {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.pf-cell-play svg {
  margin-left: 2px;
}

.pf-more {
  padding: 18px 0 30px;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-empty {
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.pf-modal-panel {
  width: 100%;
  max-width: 380px;
  background: #1c1b20;
  border-radius: var(--sg-radius-lg);
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.pf-modal-title {
  font-size: 17px;
  font-weight: 700;
  text-align: center;
}

.pf-edit-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.pf-edit-avatar img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.pf-edit-avatar-tip {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.pf-input {
  width: 100%;
  height: 42px;
  border-radius: var(--sg-radius);
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  padding: 0 14px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.pf-input:focus {
  border-color: var(--sg-primary);
}

.pf-remark-tip {
  font-size: 12px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.45);
}

.pf-remark-badge {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 92, 92, 0.16);
  border: 1px solid rgba(255, 92, 92, 0.35);
  color: #ff9a9a;
  font-size: 11px;
  font-weight: 600;
  vertical-align: middle;
}

.pf-textarea {
  height: auto;
  padding: 10px 14px;
  resize: none;
  line-height: 1.5;
}

.pf-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

/* ===== 移动端：方案D 个人主页 ===== */
@media (max-width: 767px) {
  .pf-top {
    padding: 10px 14px;
  }

  .pf-more-btn {
    display: flex;
  }

  .pf-banner {
    display: block;
    height: 150px;
    position: relative;
    overflow: hidden;
    background: linear-gradient(160deg, #3a1c2a 0%, #571f33 46%, #7c2d3e 100%);
  }

  .pf-banner::before {
    content: '';
    position: absolute;
    width: 340px;
    height: 340px;
    left: -110px;
    top: -160px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255, 143, 143, 0.32), transparent 65%);
  }

  .pf-banner::after {
    content: '';
    position: absolute;
    width: 300px;
    height: 300px;
    right: -90px;
    bottom: -180px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255, 92, 92, 0.4), transparent 65%);
  }

  .pf-banner-shine {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent 55%);
  }

  .pf-head {
    position: relative;
    align-items: stretch;
    padding: 0 18px;
  }

  .pf-head-row {
    flex-direction: row;
    align-items: flex-start;
  }

  .pf-avatar {
    width: 88px;
    height: 88px;
    margin-top: -44px;
    border: 4px solid #17161b;
    flex: none;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
  }

  .pf-head-side {
    align-items: flex-start;
    flex: 1;
    padding-left: 16px;
    margin-top: -20px;
  }

  .pf-nickname {
    margin-top: 0;
    font-size: 19px;
    text-align: left;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pf-follow {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 34px;
    padding: 0 26px;
    margin-top: 4px;
    border-radius: 12px;
    border: 1px solid rgba(255, 255, 255, 0.16);
    background: #ff5c5c;
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s, transform 0.15s;
  }

  .pf-follow:active {
    transform: scale(0.96);
  }

  .pf-follow-on {
    background: rgba(255, 255, 255, 0.1);
  }

  .pf-bio {
    text-align: left;
    max-width: 100%;
  }

  .pf-join {
    text-align: left;
  }

  .pf-stats {
    width: 100%;
    gap: 0;
    margin-top: 16px;
    padding: 14px 16px;
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.08);
  }

  .pf-stat {
    flex: 1;
  }

  .pf-stat + .pf-stat {
    border-left: 1px solid rgba(255, 255, 255, 0.08);
  }

  .pf-actions {
    display: none;
  }

  .pf-tabs {
    position: sticky;
    top: 57px;
    z-index: 20;
    display: flex;
    margin-top: 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    background: rgba(11, 11, 14, 0.85);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
  }

  .pf-tab {
    position: relative;
    flex: 1;
    height: 46px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.5);
    cursor: pointer;
    transition: color 0.2s;
    -webkit-user-select: none;
    user-select: none;
  }

  .pf-tab svg {
    width: 17px;
    height: 17px;
  }

  .pf-tab.on {
    color: #fff;
  }

  .pf-tab .pf-tab-bar {
    position: absolute;
    left: 50%;
    bottom: 0;
    transform: translateX(-50%);
    width: 0;
    height: 3px;
    border-radius: 3px;
    background: linear-gradient(90deg, #ff8a5c, #ff5c5c);
    transition: width 0.25s;
  }

  .pf-tab.on .pf-tab-bar {
    width: 28px;
  }

  .pf-grid {
    margin-top: 2px;
  }

  .pf-empty-box {
    grid-column: 1 / -1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
    padding: 70px 20px 90px;
    color: rgba(255, 255, 255, 0.35);
  }

  .pf-empty-ico {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.05);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .pf-empty-ico svg {
    width: 28px;
    height: 28px;
    fill: rgba(255, 255, 255, 0.3);
  }

  .pf-empty-box p {
    font-size: 13px;
    text-align: center;
  }

  /* 右侧抽屉 */
  .pf-mask {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.25s;
    z-index: 90;
  }

  .pf-mask.pf-mask-show {
    opacity: 1;
    pointer-events: auto;
  }

  .pf-drawer {
    position: fixed;
    top: 0;
    right: 0;
    height: 100%;
    width: min(330px, 86%);
    background: #16151b;
    z-index: 91;
    transform: translateX(106%);
    transition: transform 0.32s cubic-bezier(0.32, 0.72, 0.31, 1);
    display: flex;
    flex-direction: column;
    border-radius: 20px 0 0 20px;
    box-shadow: -12px 0 40px rgba(0, 0, 0, 0.45);
  }

  .pf-drawer.pf-drawer-open {
    transform: translateX(0);
  }

  .pf-drawer-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 18px 20px 14px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }

  .pf-drawer-title {
    font-size: 16px;
    font-weight: 700;
  }

  .pf-drawer-close {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    border: none;
    background: rgba(255, 255, 255, 0.08);
    color: #fff;
    font-size: 15px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;
  }

  .pf-drawer-close:hover {
    background: rgba(255, 255, 255, 0.16);
  }

  .pf-drawer-body {
    flex: 1;
    padding: 10px 0;
    overflow-y: auto;
  }

  .pf-drawer-item {
    display: flex;
    align-items: center;
    gap: 13px;
    padding: 15px 20px;
    font-size: 14px;
    font-weight: 500;
    color: rgba(255, 255, 255, 0.9);
    cursor: pointer;
    transition: background 0.15s;
    -webkit-user-select: none;
    user-select: none;
  }

  .pf-drawer-item:active {
    background: rgba(255, 255, 255, 0.1);
  }

  .pf-drawer-ico {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: rgba(255, 92, 92, 0.13);
    display: flex;
    align-items: center;
    justify-content: center;
    flex: none;
  }

  .pf-drawer-ico svg {
    width: 18px;
    height: 18px;
    fill: #ff7a7a;
  }

  .pf-drawer-arr {
    margin-left: auto;
    color: rgba(255, 255, 255, 0.25);
    display: flex;
  }

  .pf-drawer-arr svg {
    width: 18px;
    height: 18px;
    fill: currentColor;
  }

  .pf-drawer-foot {
    padding: 16px 20px;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.3);
    border-top: 1px solid rgba(255, 255, 255, 0.05);
  }
}
</style>

<!-- PC 端（方案 A）样式 -->
<style scoped>
.profile-page.pc-mode {
  background: #f7f7f5;
  color: #26221f;
  padding-bottom: 0;
  min-height: 100vh;
}

.pc-shell {
  display: flex;
  min-height: 100vh;
}

/* 左侧图标导航 */
.pc-side {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 76px;
  background: #fff;
  border-right: 1px solid rgba(38, 34, 31, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 18px 0;
  gap: 8px;
  z-index: 40;
}

.pc-logo {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ff8a6b, #ff5c5c);
  color: #fff;
  font-size: 19px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  box-shadow: 0 6px 18px rgba(255, 92, 92, 0.35);
  cursor: pointer;
}

.pc-side-btn {
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 16px;
  background: transparent;
  color: #8a837d;
  font-size: 11px;
  font-family: inherit;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.pc-side-btn svg {
  width: 21px;
  height: 21px;
}

.pc-side-btn:hover {
  background: #ffe9e6;
  color: #ff5c5c;
}

.pc-side-btn.on {
  background: #ffe9e6;
  color: #ff5c5c;
  font-weight: 600;
}

.pc-side-grow {
  flex: 1;
}

/* 主内容 */
.pc-main {
  margin-left: 76px;
  flex: 1;
  min-width: 0;
  position: relative;
}

.pc-back {
  position: absolute;
  top: 18px;
  left: 24px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 18px 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(38, 34, 31, 0.08);
  color: #26221f;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(38, 34, 31, 0.1);
  transition: all 0.2s;
}

.pc-back:hover {
  background: #fff;
  color: #ff5c5c;
  border-color: rgba(255, 92, 92, 0.35);
}

.pc-banner {
  height: 220px;
  background: linear-gradient(135deg, #fde8d8 0%, #f8cfc3 52%, #f3b7b6 100%);
  position: relative;
  overflow: hidden;
}

.pc-banner::before {
  content: '';
  position: absolute;
  width: 420px;
  height: 420px;
  left: -120px;
  top: -190px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.55), transparent 62%);
}

.pc-banner::after {
  content: '';
  position: absolute;
  width: 360px;
  height: 360px;
  right: -100px;
  bottom: -200px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 92, 92, 0.22), transparent 60%);
}

.pc-head {
  max-width: 1180px;
  margin: -34px auto 0;
  padding: 0 32px;
  display: flex;
  align-items: flex-start;
  gap: 28px;
  position: relative;
}

.pc-avatar {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  object-fit: cover;
  border: 6px solid #f7f7f5;
  background: #fff;
  box-shadow: 0 12px 32px rgba(38, 34, 31, 0.18);
  flex: none;
}

.pc-head-info {
  flex: 1;
  padding-bottom: 18px;
  min-width: 0;
}

.pc-hrow {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pc-nick {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: #26221f;
}

.pc-remark {
  padding: 4px 12px;
  border-radius: 999px;
  background: #ffe9e6;
  color: #e84b4b;
  font-size: 12px;
  font-weight: 600;
}

.pc-uid {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 92, 92, 0.1);
  color: #e84b4b;
  font-size: 12px;
  font-weight: 600;
}

.pc-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
  align-items: center;
}

.pc-btn {
  height: 42px;
  padding: 0 26px;
  border-radius: 14px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: inherit;
}

.pc-btn-primary {
  background: #ff5c5c;
  color: #fff;
  box-shadow: 0 6px 16px rgba(255, 92, 92, 0.32);
}

.pc-btn-primary:hover {
  background: #e84b4b;
  transform: translateY(-1px);
}

.pc-btn-on {
  background: #fff;
  color: #26221f;
  border: 1px solid rgba(38, 34, 31, 0.14);
}

.pc-btn-on:hover {
  border-color: #ff5c5c;
  color: #ff5c5c;
}

.pc-btn-ghost {
  background: #fff;
  color: #26221f;
  border: 1px solid rgba(38, 34, 31, 0.14);
}

.pc-btn-ghost:hover {
  border-color: #ff5c5c;
  color: #ff5c5c;
}

.pc-bio {
  margin-top: 10px;
  font-size: 15px;
  color: #5d5751;
  line-height: 1.6;
}

.pc-join {
  margin-top: 4px;
  font-size: 12px;
  color: #b8b2ab;
}

.pc-stats {
  display: flex;
  gap: 36px;
  margin-top: 16px;
}

.pc-stat b {
  font-size: 21px;
  font-weight: 800;
  display: block;
  color: #26221f;
  transition: color 0.2s;
}

.pc-stat span {
  font-size: 12px;
  color: #8a837d;
}

.pc-stat-link {
  cursor: pointer;
}

.pc-stat-link:hover b {
  color: #ff5c5c;
}

.pf-stat-link {
  cursor: pointer;
}

/* Tab */
.pc-tabs {
  max-width: 1180px;
  margin: 30px auto 0;
  padding: 0 32px;
  display: flex;
  gap: 6px;
  border-bottom: 1px solid rgba(38, 34, 31, 0.08);
}

.pc-tab {
  padding: 14px 22px;
  font-size: 15px;
  font-weight: 600;
  color: #8a837d;
  cursor: pointer;
  position: relative;
  transition: color 0.2s;
}

.pc-tab:hover {
  color: #26221f;
}

.pc-tab.on {
  color: #26221f;
}

.pc-tab.on::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: -1px;
  height: 3px;
  border-radius: 3px;
  background: #ff5c5c;
}

/* 网格 */
.pc-grid-wrap {
  max-width: 1180px;
  margin: 26px auto 60px;
  padding: 0 32px;
}

.pc-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.pc-cell {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 3/4;
  background: #ece2d6;
  cursor: pointer;
}

.pc-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.35s ease;
}

.pc-cell:hover img {
  transform: scale(1.06);
}

.pc-cell-ov {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.02) 55%, rgba(0, 0, 0, 0.42));
  opacity: 0;
  transition: opacity 0.25s;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 12px;
  gap: 8px;
  pointer-events: none;
}

.pc-cell:hover .pc-cell-ov {
  opacity: 1;
}

.pc-ov-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.pc-ov-play,
.pc-ov-multi {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
}

.pc-ov-play {
  display: none;
}

.pc-cell:hover .pc-ov-play {
  display: inline-flex;
}

.pc-ov-like {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
}

.pc-empty {
  padding: 90px 0 120px;
  text-align: center;
  color: #b8b2ab;
  font-size: 15px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.pc-empty-ico {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 8px 24px rgba(38, 34, 31, 0.07);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #d8d2ca;
}

.pc-empty-ico svg {
  width: 30px;
  height: 30px;
}

.pc-more {
  padding: 22px 0 10px;
  text-align: center;
  color: #b8b2ab;
  font-size: 13px;
}
</style>
