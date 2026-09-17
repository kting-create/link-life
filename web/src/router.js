import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Circles from './views/Circles.vue'
import SheetDetail from './views/SheetDetail.vue'
import Notifications from './views/Notifications.vue'
import ShareView from './views/ShareView.vue'

const routes = [
  { path: '/', redirect: '/circles' },
  { path: '/login', component: Login },
  { path: '/circles', component: Circles, meta: { requiresAuth: true } },
  { path: '/sheets/:id', component: SheetDetail, meta: { requiresAuth: true } },
  { path: '/notifications', component: Notifications, meta: { requiresAuth: true } },
  { path: '/s/:token', component: ShareView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const hasToken = !!localStorage.getItem('accessToken')
  if (to.meta.requiresAuth && !hasToken) return { path: '/login' }
  if (to.path === '/login' && hasToken) return { path: '/circles' }
  return true
})

export default router
