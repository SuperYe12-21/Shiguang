import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/feed' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { requiresAuth: true } }
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