import { defineStore } from 'pinia'
import { fetchFeed, likePost, unlikePost } from '../api/posts'
import { fetchUserPosts } from '../api/user'
import { useAuthStore } from './auth'

export const useFeedStore = defineStore('feed', {
  state: () => ({
    posts: [],
    nextCursor: null,
    hasMore: true,
    loading: false,
    loadingMore: false,
    error: '',
    mode: 'home',
    scopeUserId: null
  }),
  actions: {
    async loadFirstPage() {
      this.mode = 'home'
      this.scopeUserId = null
      this.loading = true
      this.error = ''
      try {
        const data = await fetchFeed('', 10)
        this.posts = data.items || []
        this.nextCursor = data.nextCursor || null
        this.hasMore = !!data.hasMore
      } catch (e) {
        this.error = e.message || '加载失败'
      } finally {
        this.loading = false
      }
    },
    async loadUserFirstPage(userId) {
      this.mode = 'user'
      this.scopeUserId = userId
      this.loading = true
      this.error = ''
      try {
        const data = await fetchUserPosts(userId, '', 12)
        this.posts = data.items || []
        this.nextCursor = data.nextCursor || null
        this.hasMore = !!data.hasMore
      } catch (e) {
        this.error = e.message || '加载失败'
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (this.loadingMore || !this.hasMore || this.loading) return
      this.loadingMore = true
      try {
        const data = this.mode === 'user'
          ? await fetchUserPosts(this.scopeUserId, this.nextCursor, 12)
          : await fetchFeed(this.nextCursor, 10)
        const items = data.items || []
        const seen = new Set(this.posts.map((p) => p.id))
        for (const item of items) {
          if (!seen.has(item.id)) {
            this.posts.push(item)
            seen.add(item.id)
          }
        }
        this.nextCursor = data.nextCursor || null
        this.hasMore = !!data.hasMore
      } catch (e) {
        // 失败静默，滚动可重试
      } finally {
        this.loadingMore = false
      }
    },
    reset() {
      this.posts = []
      this.nextCursor = null
      this.hasMore = true
      this.loading = false
      this.loadingMore = false
      this.error = ''
      this.mode = 'home'
      this.scopeUserId = null
    },
    async toggleLike(post) {
      const auth = useAuthStore()
      if (!auth.isLoggedIn) {
        location.href = '/login'
        return
      }
      const prevLiked = post.liked
      const prevCount = post.likeCount
      post.liked = !prevLiked
      post.likeCount = Math.max(0, prevCount + (prevLiked ? -1 : 1))
      try {
        const data = prevLiked ? await unlikePost(post.id) : await likePost(post.id)
        post.liked = data.liked
        post.likeCount = data.likeCount
      } catch (e) {
        post.liked = prevLiked
        post.likeCount = prevCount
      }
    }
  }
})