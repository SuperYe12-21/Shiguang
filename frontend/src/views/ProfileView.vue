<template>
  <div class="profile-page" :class="{ 'pc-mode': isPc }">
    <template v-if="isPc">
      <div class="pc-shell">
        <AppSideNav :active="isMe ? 'me' : ''" :user="auth.user" :unread-count="unreadTotal" />
        <div class="pc-main">
          <header class="pc-profile-top">
            <div class="pc-profile-title"><button class="pc-back" aria-label="返回" @click="goBack"><svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg></button><span>个人主页</span></div>
            <button class="profile-search" aria-label="搜索" @click="router.push('/search')"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/></svg><span>搜索你感兴趣的内容</span></button>
          </header>
          <div class="pc-banner" aria-hidden="true"></div>
          <div class="pc-profile-inner">
          <header class="pc-head">
            <img class="pc-avatar" :src="profile.avatarUrl || fallbackAvatar" alt="头像" title="查看头像大图" @click="openAvatarView" />
            <div class="pc-head-info">
              <div class="pc-hrow">
                <h1 class="pc-nick">{{ displayName || '拾光用户' }}<small class="pc-uid">拾光号 {{ profile.id }}</small></h1>
                <span v-if="remark && !isMe" class="pc-remark">备注</span>
              </div>
              <p v-if="profile.bio" class="pc-bio">{{ profile.bio }}</p>
            </div>
            <div class="pc-actions">
              <button v-if="isMe" class="pc-btn pc-btn-ghost" @click="openEdit">编辑资料</button>
              <template v-else>
                <button class="pc-btn pc-btn-ghost" @click="openChat">私信</button>
                <button class="pc-btn pc-btn-primary" :class="{ 'pc-btn-on': profile.followedByMe }" @click="toggleFollow">{{ followText }}</button>
              </template>
              <button class="pc-btn pc-btn-primary" aria-label="更多" @click="drawerOpen = true">更多 ···</button>
            </div>
          </header>
          <div class="pc-stats">
            <div class="pc-stat"><b>{{ profile.postCount ?? 0 }}</b><span>作品</span></div>
            <div class="pc-stat"><b>{{ formatCount(profile.likeCount) }}</b><span>获赞</span></div>
            <div class="pc-stat" :class="{ 'pc-stat-link': canSeeFollowing }" @click="goFollowing"><b>{{ formatCount(profile.followingCount) }}</b><span>关注</span></div>
            <div class="pc-stat" :class="{ 'pc-stat-link': canSeeFollower }" @click="goFollowers"><b>{{ formatCount(profile.followerCount) }}</b><span>粉丝</span></div>
          </div>
          <nav class="pc-tabs">
            <div class="pc-tab" :class="{ on: activeTab === 'posts' }" @click="switchTab('posts')">作品<small>{{ profile.postCount ?? 0 }}</small></div>
            <div v-if="canSeeLike" class="pc-tab" :class="{ on: activeTab === 'likes' }" @click="switchTab('likes')">点赞</div>
            <div v-if="canSeeFavorite" class="pc-tab" :class="{ on: activeTab === 'favorites' }" @click="switchTab('favorites')">收藏</div>
          </nav>
          <section class="pc-grid-wrap">
            <div v-if="activeTab === 'favorites' && !favorites.length && !favoritesLoading" class="pc-empty">
              <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg></span>
              <p>还没有收藏的作品</p>
            </div>
            <template v-else-if="activeTab === 'likes' && !likes.length && !likesLoading">
              <div class="pc-empty">
                <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg></span>
                <p>还没有点赞的作品</p>
              </div>
            </template>
            <div v-else-if="!gridItems.length && activeTab === 'posts' && !loading && !loadingMore" class="pc-empty">
              <span class="pc-empty-ico"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></span>
              <p>{{ isMe ? '还没有作品，去发布第一条拾光吧' : '该用户还没有发布作品' }}</p>
            </div>
            <div v-else class="pc-grid">
              <div v-for="p in gridItems" :key="p.id" class="pc-cell" @click="openGridPost(p)">
                <div class="pc-cell-cover">
                  <img :src="coverOf(p)" :alt="p.title || '作品'" loading="lazy" />
                  <span class="pc-cell-ov">
                    <span v-if="p.type === 'VIDEO'" class="pc-ov-tag pc-ov-play">视频</span>
                    <span v-else class="pc-ov-tag pc-ov-multi">图文{{ (p.images || []).length > 1 ? ' · ' + p.images.length : '' }}</span>
                    <span class="pc-ov-stats">
                      <span v-if="p.type === 'VIDEO'" class="pc-ov-tag pc-ov-view"><svg viewBox="0 0 24 24" width="11" height="11" fill="#fff"><path d="M8 5v14l11-7z"/></svg>{{ formatCount(p.viewCount) }}</span>
                      <span class="pc-ov-tag pc-ov-like"><svg viewBox="0 0 24 24" width="10" height="10" fill="#fff"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>{{ formatCount(p.likeCount) }}</span>
                    </span>
                  </span>
                  <span v-if="p.status === 'BLOCKED'" class="pc-cell-blocked" title="已被管理员下架">已下架</span>
                  <span v-else-if="p.visibility === 'PRIVATE'" class="pc-cell-lock" title="仅自己可见">
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="#fff"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm3 12c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/></svg>
                  </span>
                </div>
                <p class="pc-cell-title">{{ p.title || '未命名作品' }}</p>
                <time class="pc-cell-date" :datetime="p.createdAt">{{ formatDate(p.createdAt) }}</time>
              </div>
            </div>
            <div class="pc-more">
              <span v-if="loadingMore || likesLoading || favoritesLoading">加载中…</span>
              <span v-else-if="activeTab === 'posts' && posts.length && !hasMore">— 没有更多了 —</span>
              <span v-else-if="activeTab === 'likes' && likes.length && !likesHasMore">— 没有更多了 —</span>
              <span v-else-if="activeTab === 'favorites' && favorites.length && !favoritesHasMore">— 没有更多了 —</span>
            </div>
          </section>
          </div>
        </div>
      </div>
    </template>

    <!-- 移动端 -->
    <template v-else>
    <!-- 顶栏 -->
    <header class="pf-top">
      <button class="pf-back" aria-label="返回" @click="goBack"><svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg></button>
      <span class="pf-top-title">个人主页</span>
      <button class="pf-more-btn" aria-label="更多" @click="drawerOpen = true"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg></button>
    </header>

    <!-- 移动端横幅 -->
    <div v-if="!isPc" class="pf-banner" aria-hidden="true"></div>

    <!-- 用户信息 -->
    <section class="pf-head">
      <div class="pf-head-row" :class="{ 'pf-head-visitor': !isMe }">
        <img class="pf-avatar" :src="profile.avatarUrl || fallbackAvatar" alt="头像" @click="openAvatarView" />
        <div class="pf-head-side">
          <h2 class="pf-nickname"><span class="pf-name" :title="displayName">{{ displayName || '拾光用户' }}<span v-if="remark && !isMe" class="pf-remark-badge">备注</span></span><small class="pf-uid">拾光号 {{ profile.id }}</small></h2>
          <p v-if="profile.bio" class="pf-bio">{{ profile.bio }}</p>
        </div>
        <div class="pf-side-btns">
          <button v-if="isMe" class="pf-msg" @click="openEdit">编辑资料</button>
          <template v-else>
            <button class="pf-follow" :class="{ 'pf-follow-on': profile.followedByMe }" @click="toggleFollow">{{ followText }}</button>
            <button class="pf-msg" @click="openChat">私信</button>
          </template>
        </div>
      </div>
      <div class="pf-stats">
        <div class="pf-stat"><b>{{ profile.postCount ?? 0 }}</b><span>作品</span></div>
        <div class="pf-stat"><b>{{ formatCount(profile.likeCount) }}</b><span>获赞</span></div>
        <div class="pf-stat" :class="{ 'pf-stat-link': canSeeFollowing }" @click="goFollowing"><b>{{ formatCount(profile.followingCount) }}</b><span>关注</span></div>
        <div class="pf-stat" :class="{ 'pf-stat-link': canSeeFollower }" @click="goFollowers"><b>{{ formatCount(profile.followerCount) }}</b><span>粉丝</span></div>
      </div>
    </section>

    <!-- 移动端 Tab 栏 -->
    <nav v-if="!isPc" class="pf-tabs">
      <div class="pf-tab" :class="{ on: activeTab === 'posts' }" @click="switchTab('posts')">
        作品<small>{{ profile.postCount ?? 0 }}</small><span class="pf-tab-bar" />
      </div>
      <div v-if="canSeeLike" class="pf-tab" :class="{ on: activeTab === 'likes' }" @click="switchTab('likes')">
        点赞<span class="pf-tab-bar" />
      </div>
      <div v-if="canSeeFavorite" class="pf-tab" :class="{ on: activeTab === 'favorites' }" @click="switchTab('favorites')">
        收藏<span class="pf-tab-bar" />
      </div>
    </nav>

    <!-- 作品网格 -->
    <section class="pf-grid">
      <template v-if="activeTab === 'favorites' && !favorites.length && !favoritesLoading">
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
        <div v-for="p in gridItems" :key="p.id" class="pf-work" @click="openGridPost(p)">
          <div class="pf-cell">
          <img :src="coverOf(p)" :alt="p.title || '作品'" loading="lazy" />
          <span v-if="p.type === 'VIDEO'" class="pf-cell-ov"><span class="pf-cell-play"><svg viewBox="0 0 24 24" width="14" height="14" fill="#fff"><path d="M8 5v14l11-7z"/></svg></span></span>
          <span v-if="p.type === 'VIDEO'" class="pf-cell-mark pf-cell-video">视频</span>
          <span v-else class="pf-cell-mark pf-cell-multi">图文{{ (p.images || []).length > 1 ? ' · ' + p.images.length : '' }}</span>
          <span class="pf-cell-stats">
            <span v-if="p.type === 'VIDEO'" class="pf-cell-like pf-cell-view">
              <svg viewBox="0 0 24 24" width="9" height="9" fill="#fff"><path d="M8 5v14l11-7z"/></svg>
              {{ formatCount(p.viewCount) }}
            </span>
            <span class="pf-cell-like">
              <svg viewBox="0 0 24 24" width="9" height="9" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
              {{ formatCount(p.likeCount) }}
            </span>
          </span>
          <span v-if="p.status === 'BLOCKED'" class="pf-cell-blocked" title="已被管理员下架">已下架</span>
          <span v-else-if="p.visibility === 'PRIVATE'" class="pf-cell-lock" title="仅自己可见">
            <svg viewBox="0 0 24 24" width="11" height="11" fill="#fff"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm3 12c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/></svg>
          </span>
          </div>
          <p class="pf-cell-title">{{ p.title || '未命名作品' }}</p>
        </div>
      </template>
    </section>
    <div v-if="activeTab === 'posts'">
      <div v-if="loadingMore" class="pf-more">加载中…</div>
      <div v-else-if="posts.length && !hasMore" class="pf-more">— 没有更多了 —</div>
      <div v-if="!posts.length && !loading && !loadingMore" class="pf-empty">{{ isMe ? '还没有作品，去发布第一条拾光吧' : '该用户还没有发布作品' }}</div>
    </div>
    <div v-else-if="activeTab === 'likes'">
      <div v-if="likesLoading" class="pf-more">加载中…</div>
      <div v-else-if="likes.length && !likesHasMore" class="pf-more">— 没有更多了 —</div>
    </div>
    <div v-else-if="activeTab === 'favorites'">
      <div v-if="favoritesLoading" class="pf-more">加载中…</div>
      <div v-else-if="favorites.length && !favoritesHasMore" class="pf-more">— 没有更多了 —</div>
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

    <!-- 更多菜单 -->
    <div class="profile-more">
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
          <div v-if="isMe" class="pf-drawer-item" @click="onDrawerHistory">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/></svg></span>
            <span>观看历史</span>
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
          <div v-if="isMe && isAdmin" class="pf-drawer-item" @click="onDrawerAdmin">
            <span class="pf-drawer-ico"><svg viewBox="0 0 24 24"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/></svg></span>
            <span>管理后台</span>
            <span class="pf-drawer-arr"><svg viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg></span>
          </div>
        </div>
        <button v-if="isMe" class="pf-drawer-logout" @click="logout">退出登录</button>
        <div class="pf-drawer-foot">加入拾光 · {{ formatDate(profile.createdAt) }}<br>拾光 · v1.0.0</div>
      </aside>
    </div>

    <BottomNav v-if="!isPc" :active="isMe ? 'me' : ''" />

    <!-- 分享降级：无法自动复制时弹窗手动复制 -->
    <div v-if="shareFallbackOpen" class="pf-modal" @click.self="shareFallbackOpen = false">
      <div class="pf-modal-panel">
        <h3 class="pf-modal-title">分享主页</h3>
        <p class="pf-remark-tip">当前浏览器无法自动复制，请长按下方链接手动复制：</p>
        <input
          class="pf-input"
          readonly
          :value="shareFallbackUrl"
          @focus="onShareInputFocus"
        />
        <div class="pf-modal-actions">
          <button class="pf-btn pf-btn-ghost" @click="shareFallbackOpen = false">关闭</button>
          <button class="pf-btn pf-btn-primary" @click="retryShareCopy">复制链接</button>
        </div>
      </div>
    </div>

    <!-- 头像大图查看 -->
    <div v-if="avatarViewOpen" class="pf-avatar-view" @click.self="closeAvatarView">
      <img class="pf-avatar-img" :src="profile.avatarUrl" alt="头像大图" />
      <button class="pf-avatar-close" aria-label="关闭" @click="closeAvatarView">×</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMe, fetchProfile, fetchUserFavorites, fetchUserLikes, fetchUserPosts, updateMe, followUser, unfollowUser } from '../api/user'
import { fetchAdminMe } from '../api/admin'
import { presignUpload } from '../api/posts'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import { useMessageStore } from '../stores/message'
import BottomNav from '../components/mobile/BottomNav.vue'
import AppSideNav from '../components/AppSideNav.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()
const message = useMessageStore()
// 消息入口同时承载通知与私信，红点合并计算
const unreadTotal = computed(() => notification.unread.total + message.unread)

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
const favorites = ref([])
const favoritesLoading = ref(false)
const favoritesCursor = ref(null)
const favoritesHasMore = ref(true)
const drawerOpen = ref(false)
const isAdmin = ref(false)
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
const avatarViewOpen = ref(false)
const shareFallbackUrl = ref('')
const shareFallbackOpen = ref(false)

const isMe = computed(() => meId.value !== null && meId.value === profile.value.id)

const followText = computed(() => {
  if (!profile.value.followedByMe) return '+ 关注'
  return profile.value.matched ? '互相关注' : '已关注'
})

// 四项列表可见性：自己永远可看，他人按隐私设置（缺省公开）
const canSeeLike = computed(() => isMe.value || (profile.value.viewerCanSee?.like ?? true))
const canSeeFavorite = computed(() => isMe.value || (profile.value.viewerCanSee?.favorite ?? true))
const canSeeFollower = computed(() => isMe.value || (profile.value.viewerCanSee?.follower ?? true))
const canSeeFollowing = computed(() => isMe.value || (profile.value.viewerCanSee?.following ?? true))

const gridItems = computed(() => {
  if (activeTab.value === 'likes') return likes.value
  if (activeTab.value === 'favorites') return favorites.value
  return posts.value
})

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
    if (activeTab.value === 'likes' && !canSeeLike.value) activeTab.value = 'posts'
    if (activeTab.value === 'favorites' && !canSeeFavorite.value) activeTab.value = 'posts'
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
  if (tab === 'likes' && !canSeeLike.value) return
  if (tab === 'favorites' && !canSeeFavorite.value) return
  activeTab.value = tab
  if (tab === 'likes' && !likes.value.length && !likesLoading.value) loadLikes()
  if (tab === 'favorites' && !favorites.value.length && !favoritesLoading.value) loadFavorites()
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

async function loadFavorites() {
  if (favoritesLoading.value || !favoritesHasMore.value) return
  favoritesLoading.value = true
  try {
    const data = await fetchUserFavorites(profile.value.id, favoritesCursor.value, 12)
    const items = data.items || []
    const seen = new Set(favorites.value.map((p) => p.id))
    for (const item of items) {
      if (!seen.has(item.id)) {
        favorites.value.push(item)
        seen.add(item.id)
      }
    }
    favoritesCursor.value = data.nextCursor || null
    favoritesHasMore.value = !!data.hasMore
  } catch (e) {
    // 静默，滚动可重试
  } finally {
    favoritesLoading.value = false
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
    if (typeof data.matched === 'boolean') profile.value.matched = data.matched
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

function openChat() {
  const id = profile.value.id
  if (id) router.push('/chat/' + id)
}

function goFollowers() {
  if (!canSeeFollower.value) return
  router.push(`/user/${profile.value.id}/followers`)
}

function goFollowing() {
  if (!canSeeFollowing.value) return
  router.push(`/user/${profile.value.id}/following`)
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

function openAvatarView() {
  if (!profile.value.avatarUrl) return
  avatarViewOpen.value = true
  document.body.style.overflow = 'hidden'
}

function closeAvatarView() {
  avatarViewOpen.value = false
  document.body.style.overflow = ''
}

function avatarViewKeydown(e) {
  if (e.key === 'Escape' && avatarViewOpen.value) closeAvatarView()
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
  message.stop()
  notification.stop()
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
  router.push('/settings')
}

function onDrawerHistory() {
  closeDrawer()
  router.push('/history')
}

function onDrawerAdmin() {
  closeDrawer()
  router.push('/admin')
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
  // 优先调起系统分享面板（微信/QQ/系统分享等）
  if (navigator.share) {
    try {
      await navigator.share({ title: text, text, url })
      return
    } catch (e) {
      if (e && e.name === 'AbortError') return
      // 分享面板异常时继续降级为复制链接
    }
  }
  if (await copyTextToClipboard(url)) {
    ElMessage.success('主页链接已复制')
    return
  }
  // 手机通过局域网 IP 访问（非 https）时无系统分享与剪贴板 API，弹窗提供手动复制
  shareFallbackUrl.value = url
  shareFallbackOpen.value = true
}

async function copyTextToClipboard(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch (e) {
    // 权限被拒等，继续降级
  }
  // 非安全上下文降级：隐藏输入框 + execCommand 复制
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.top = '-1000px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    ta.setSelectionRange(0, ta.value.length)
    let ok = false
    try {
      ok = document.execCommand('copy')
    } catch (e) {
      ok = false
    }
    document.body.removeChild(ta)
    return ok
  } catch (e) {
    return false
  }
}

function onShareInputFocus(e) {
  const el = e.target
  el.select()
  try {
    el.setSelectionRange(0, el.value.length)
  } catch (err) {
    // 个别浏览器不支持，忽略
  }
}

async function retryShareCopy() {
  if (await copyTextToClipboard(shareFallbackUrl.value)) {
    shareFallbackOpen.value = false
    ElMessage.success('主页链接已复制')
    return
  }
  ElMessage.warning('自动复制失败，请长按链接手动复制')
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
  if (auth.isLoggedIn) {
    try {
      const adminData = await fetchAdminMe()
      isAdmin.value = !!adminData.admin
    } catch (e) {
      // 取不到就当作普通用户
    }
  }
  await loadProfile()
  await loadPosts()
  scrollHandler = () => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 400) {
      if (activeTab.value === 'likes') loadLikes()
      else if (activeTab.value === 'favorites') loadFavorites()
      else loadPosts()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
  window.addEventListener('resize', syncIsPc)
  window.addEventListener('keydown', avatarViewKeydown)
})

onBeforeUnmount(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
  window.removeEventListener('resize', syncIsPc)
  window.removeEventListener('keydown', avatarViewKeydown)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--sg-bg, #f4f1e9);
  color: var(--sg-text, #3b4239);
  line-height: normal;
  padding-bottom: calc(var(--sg-nav-h, 64px) + env(safe-area-inset-bottom) + 20px);
  --profile-muted: #99998d;
}

.profile-page button {
  font-family: inherit;
  cursor: pointer;
}

.profile-page button:focus-visible {
  outline: 2px solid var(--sg-primary, #b66c4d);
  outline-offset: 3px;
}

.pf-top {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 58px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sg-bg, #f4f1e9);
  border-bottom: 1px solid var(--sg-line, #e6e3d9);
}

.pf-back,
.pf-more-btn,
.pf-drawer-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  height: 34px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 6px;
  background: transparent;
  color: inherit;
}

.pf-back {
  width: 24px;
  height: 28px;
  border: 0;
  padding: 0;
}

.pf-top-title {
  flex: 1;
  min-width: 0;
  margin: 0 0 0 8px;
  font-size: 17px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-search {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 20px;
  background: var(--sg-card, #faf8f2);
  color: var(--profile-muted);
  font-size: 11px;
}

.pf-more-btn {
  width: 34px;
}

.pf-banner,
.pc-banner {
  position: relative;
  overflow: hidden;
  background: linear-gradient(110deg, #dadfd2, #e3ddca, #ddd4c0);
}

.pf-banner {
  height: 105px;
}

.pf-banner::after,
.pc-banner::after {
  content: 'SHIGUANG / EVERYDAY MOMENTS';
  position: absolute;
  right: 30px;
  top: 48px;
  color: #faf8f2cc;
  font-size: 11px;
  letter-spacing: 5px;
}

.pf-banner::after {
  right: 16px;
  top: 40px;
  font-size: 8px;
  letter-spacing: 3px;
}

.pf-head {
  position: relative;
  padding: 0 18px;
}

.pf-head-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: -31px;
}

.pf-avatar {
  width: 82px;
  height: 82px;
  flex: none;
  border: 4px solid var(--sg-bg, #f4f1e9);
  border-radius: 25px;
  background: var(--sg-primary-soft, #eee4d8);
  object-fit: cover;
}

.pf-head-side {
  flex: 1;
  min-width: 0;
  padding-top: 27px;
}

.pf-nickname {
  margin: 0 0 3px;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 31px;
  letter-spacing: 1px;
  overflow-wrap: anywhere;
}

.pf-uid {
  display: block;
  margin: 4px 0;
  color: var(--profile-muted);
  font-size: 8px;
  font-weight: 400;
  letter-spacing: 0;
}

.pf-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pf-remark-badge,
.pc-remark {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-primary, #b66c4d);
  font-family: sans-serif;
  font-size: 10px;
  font-weight: 400;
  vertical-align: middle;
}

.pf-side-btns {
  position: absolute;
  right: 0;
  top: 81px;
  display: flex;
  gap: 6px;
}

.pf-follow,
.pf-msg,
.pf-btn,
.pc-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 7px 14px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 5px;
  background: var(--sg-card, #faf8f2);
  color: inherit;
  font-size: 12px;
  line-height: 1.4;
}

.pf-follow,
.pf-btn-primary,
.pc-btn-primary {
  border-color: var(--sg-primary, #b66c4d);
  background: var(--sg-primary, #b66c4d);
  color: #fff;
}

.pf-follow-on,
.pc-btn-on {
  border-color: var(--sg-line, #e6e3d9);
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-text, #3b4239);
}

.pf-side-btns button {
  min-height: 0;
  padding: 7px 12px;
  font-size: 10px;
  line-height: normal;
  white-space: nowrap;
}

.pf-msg {
  border-radius: 10px;
}

.pf-follow:hover,
.pf-msg:hover,
.pf-btn:hover,
.pc-btn:hover {
  filter: brightness(0.96);
}

.pf-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.pf-bio,
.pc-bio {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}

.pf-bio {
  margin: 0;
  color: var(--profile-muted);
  font-size: 10px;
}

.pf-bio::before {
  content: '';
  float: right;
  width: 80px;
  max-width: calc(100% - 14px);
  height: 20px;
}

.pf-head-visitor .pf-bio::before {
  width: 128px;
}

.pf-stats {
  display: flex;
  justify-content: space-between;
  margin: 33px 0 23px;
}

.pf-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}

.pf-stat b {
  font-size: 17px;
  font-weight: 700;
}

.pf-stat span {
  color: var(--profile-muted);
  font-size: 9px;
}

.pf-stat-link,
.pc-stat-link {
  cursor: pointer;
}

.pf-stat-link:hover b,
.pc-stat-link:hover b {
  color: var(--sg-primary, #b66c4d);
}

.pf-tabs {
  position: sticky;
  top: 58px;
  z-index: 19;
  display: flex;
  gap: 28px;
  margin: 0 18px 14px;
  border-bottom: 1px solid var(--sg-line, #e6e3d9);
  background: var(--sg-bg, #f4f1e9);
}

.pf-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 0 13px;
  font-size: 12px;
  color: var(--profile-muted);
  cursor: pointer;
}

.pf-tab small,
.pc-tab small {
  color: var(--profile-muted);
  font-size: 10px;
  font-weight: 400;
}

.pc-tab small {
  margin-left: 6px;
}

.pf-tab.on,
.pc-tab.on {
  color: var(--sg-text, #3b4239);
  font-weight: 600;
}

.pf-tab-bar,
.pc-tab.on::after {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  border-radius: 3px;
  background: var(--sg-primary, #b66c4d);
}

.pf-tab-bar {
  display: none;
}

.pf-tab.on .pf-tab-bar {
  display: block;
}

.pf-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  padding: 0 18px;
}

.pf-cell {
  position: relative;
  min-width: 0;
  aspect-ratio: 3 / 4;
  border-radius: 6px;
  background: var(--sg-primary-soft, #eee4d8);
  overflow: hidden;
  cursor: pointer;
}

.pf-work {
  min-width: 0;
  cursor: pointer;
}

.pf-cell-title {
  margin: 5px 2px 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 9px;
}

.pf-cell img,
.pc-cell-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.25s;
}

.pf-cell:hover img,
.pc-cell:hover img {
  transform: scale(1.035);
}

.pf-cell-mark,
.pf-cell-like,
.pf-cell-lock,
.pf-cell-blocked,
.pc-cell-lock,
.pc-cell-blocked,
.pc-ov-play,
.pc-ov-multi {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 3px 5px;
  border-radius: 4px;
  background: #252a236b;
  color: #fff;
  font-size: 9px;
}

.pf-cell-mark {
  position: absolute;
  top: 7px;
  right: 7px;
}

.pf-cell-stats {
  position: absolute;
  z-index: 1;
  left: 7px;
  bottom: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pf-cell-like {
  padding: 0;
  background: transparent;
  text-shadow: 0 1px 4px #0009;
}

.pf-cell::after {
  content: '';
  position: absolute;
  inset: 65% 0 0;
  background: linear-gradient(transparent, #0006);
  pointer-events: none;
}

.pf-cell-ov {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.pf-cell:hover .pf-cell-ov {
  opacity: 1;
}

.pf-cell-play {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #252a236b;
}

.pf-cell-lock,
.pf-cell-blocked,
.pc-cell-lock,
.pc-cell-blocked {
  position: absolute;
  left: 7px;
  top: 7px;
}

.pf-cell-blocked,
.pc-cell-blocked {
  color: #ffe0b1;
}

.pf-empty,
.pf-empty-box,
.pc-empty {
  padding: 64px 18px;
  color: var(--profile-muted);
  text-align: center;
  font-size: 13px;
}

.pf-empty-box {
  grid-column: 1 / -1;
}

.pf-empty-ico,
.pc-empty-ico {
  display: inline-grid;
  place-items: center;
  width: 60px;
  height: 60px;
  margin-bottom: 14px;
  border-radius: 50%;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--profile-muted);
}

.pf-empty-ico svg,
.pc-empty-ico svg {
  width: 26px;
  height: 26px;
  fill: currentColor;
}

.pf-more,
.pc-more {
  padding: 22px 0;
  color: var(--profile-muted);
  text-align: center;
  font-size: 11px;
}

.pf-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #292e2766;
}

.pf-modal-panel {
  width: 100%;
  max-width: 380px;
  max-height: 85dvh;
  overflow-y: auto;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 12px;
  background: var(--sg-card, #faf8f2);
  box-shadow: 0 20px 60px #292e2726;
}

.pf-modal-title {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 20px;
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
  border: 3px solid var(--sg-line, #e6e3d9);
  border-radius: 20px;
  object-fit: cover;
}

.pf-edit-avatar-tip,
.pf-remark-tip {
  font-size: 12px;
  line-height: 1.7;
  color: var(--profile-muted);
}

.pf-input {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 6px;
  background: var(--sg-bg, #f4f1e9);
  color: inherit;
  font: inherit;
  font-size: 13px;
}

.pf-input:focus {
  outline: 1px solid var(--sg-primary, #b66c4d);
}

.pf-textarea {
  resize: vertical;
}

.pf-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pf-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: #292e2766;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s;
}

.pf-mask-show {
  opacity: 1;
  pointer-events: auto;
}

.pf-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  z-index: 91;
  width: min(330px, 86%);
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--sg-line, #e6e3d9);
  background: var(--sg-card, #faf8f2);
  transform: translateX(106%);
  visibility: hidden;
  transition: transform 0.3s, visibility 0.3s;
}

.pf-drawer-open {
  transform: translateX(0);
  visibility: visible;
}

.pf-drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: calc(18px + env(safe-area-inset-top)) 20px 14px;
  border-bottom: 1px solid var(--sg-line, #e6e3d9);
}

.pf-drawer-title {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 20px;
}

.pf-drawer-close {
  width: 32px;
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
  color: inherit;
  font-size: 14px;
  cursor: pointer;
}

.pf-drawer-item:active {
  background: var(--sg-primary-soft, #eee4d8);
}

.pf-drawer-ico {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-primary, #b66c4d);
}

.pf-drawer-ico svg,
.pf-drawer-arr svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.pf-drawer-arr {
  display: flex;
  margin-left: auto;
  color: var(--profile-muted);
}

.pf-drawer-foot {
  padding: 16px 20px calc(16px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--sg-line, #e6e3d9);
  color: var(--profile-muted);
  font-size: 11px;
}

.pf-drawer-logout {
  margin: 12px 20px 20px;
  padding: 11px;
  border: 1px solid var(--sg-line, #e6e3d9);
  border-radius: 5px;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-primary, #b66c4d);
  font-size: 13px;
}

.profile-page.pc-mode {
  padding-bottom: 0;
  background: var(--sg-bg, #f4f1e9);
}

.pc-shell {
  min-height: 100vh;
}

.pc-main {
  min-height: 100vh;
  margin-left: var(--sg-sidebar-w, 188px);
  background: var(--sg-bg, #f4f1e9);
}

.pc-profile-top {
  height: 77px;
  padding: 0 35px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pc-profile-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
}

.pc-back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 30px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 12px;
}

.pc-back:hover {
  color: var(--sg-primary, #b66c4d);
}

.pc-banner {
  height: 100px;
}

.pc-profile-inner {
  padding: 0 35px 30px;
}

.pc-head {
  position: relative;
  margin: -36px 0 0;
  display: flex;
  align-items: center;
  gap: 23px;
}

.pc-avatar {
  width: 100px;
  height: 100px;
  flex: none;
  border: 5px solid var(--sg-bg, #f4f1e9);
  border-radius: 25px;
  background: var(--sg-primary-soft, #eee4d8);
  object-fit: cover;
  box-shadow: 0 5px 12px #3b423909;
}

.pc-head-info {
  flex: 1;
  min-width: 0;
  padding-top: 37px;
}

.pc-hrow {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
}

.pc-nick {
  margin: 0 0 6px;
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 31px;
  font-weight: 700;
  letter-spacing: 1px;
  overflow-wrap: anywhere;
}

.pc-remark {
  margin-left: 0;
}

.pc-uid {
  margin-left: 12px;
  color: var(--profile-muted);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0;
}

.pc-actions {
  display: flex;
  flex: none;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 44px;
}

.pc-btn {
  min-height: 0;
  padding: 10px 18px;
  border-radius: 10px;
  line-height: normal;
  white-space: nowrap;
}

.pc-btn-primary {
  padding: 11px 22px;
  border: 0;
  border-radius: 5px;
  font-size: 14px;
  font-weight: 600;
}

.pc-bio {
  margin: 0;
  color: var(--profile-muted);
  font-size: 12px;
}

.pc-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 32px;
  margin: 20px 0 25px 124px;
}

.pc-stat {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.pc-stat b {
  font-size: 18px;
  font-weight: 700;
}

.pc-stat span {
  color: var(--profile-muted);
  font-size: 11px;
}

.pc-tabs {
  display: flex;
  gap: 28px;
  width: 100%;
  margin: 0 0 21px;
  border-bottom: 1px solid var(--sg-line, #e6e3d9);
}

.pc-tab {
  position: relative;
  padding: 0 0 13px;
  color: var(--profile-muted);
  font-size: 13px;
  cursor: pointer;
}

.pc-tab.on::after {
  content: '';
}

.pc-grid-wrap {
  padding: 0 0 38px;
}

.pc-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.pc-cell {
  min-width: 0;
  cursor: pointer;
}

.pc-cell-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 5px;
  overflow: hidden;
  background: var(--sg-primary-soft, #eee4d8);
}

.pc-cell-title {
  margin: 12px 2px 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.pc-cell-date {
  margin-left: 2px;
  color: var(--profile-muted);
  font-size: 9px;
}

.pc-cell-ov {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  padding: 11px 12px;
  background: linear-gradient(transparent 65%, #0007);
  pointer-events: none;
}

.pc-ov-tag,
.pc-ov-stats {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  font-size: 10px;
}

.pc-ov-stats {
  gap: 12px;
}

.pc-ov-play,
.pc-ov-multi {
  position: absolute;
  top: 10px;
  right: 10px;
}

.pc-avatar,
.pf-avatar {
  cursor: zoom-in;
}

.pf-avatar-view {
  position: fixed;
  inset: 0;
  z-index: 3100;
  display: grid;
  place-items: center;
  background: #141a14eb;
}

.pf-avatar-img {
  max-width: min(88vw, 640px);
  max-height: 82vh;
  border-radius: 12px;
  object-fit: contain;
}

.pf-avatar-close {
  position: fixed;
  top: calc(16px + env(safe-area-inset-top));
  right: 16px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #ffffff24;
  color: #fff;
  font-size: 26px;
}

@media (min-width: 768px) and (max-width: 1100px) {
  .pc-profile-top {
    padding: 0 25px;
  }

  .pc-profile-inner {
    padding-left: 25px;
    padding-right: 25px;
  }

  .pc-actions {
    gap: 5px;
  }

  .pc-btn {
    padding: 9px 12px;
  }

  .pc-btn-primary {
    padding: 9px 14px;
  }
}

</style>
