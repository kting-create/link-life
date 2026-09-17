import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Circles from './views/Circles.vue'
import SheetDetail from './views/SheetDetail.vue'

const routes = [
  { path: '/', redirect: '/circles' },
  { path: '/login', component: Login },
  { path: '/circles', component: Circles, meta: { requiresAuth: true } },
  { path: '/sheets/:id', component: SheetDetail, meta: { requiresAuth: true } },
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
