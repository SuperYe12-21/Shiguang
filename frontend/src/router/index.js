import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/feed' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { requiresAuth: true } },
  { path: '/me', name: 'me', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true } },
  { path: '/user/:id', name: 'user', component: () => import('../views/ProfileView.vue') },
  { path: '/user/:id/followers', name: 'user-followers', component: () => import('../views/FollowListView.vue') },
  { path: '/user/:id/following', name: 'user-following', component: () => import('../views/FollowListView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('sg_token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'feed' }
  }
  return true
})

export default router
