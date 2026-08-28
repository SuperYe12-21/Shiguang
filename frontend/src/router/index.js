import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/feed' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('sg_token')
  if (to.name === 'login' && token) {
    return { name: 'feed' }
  }
  return true
})

export default router