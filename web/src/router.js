import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Circles from './views/Circles.vue'
import SheetDetail from './views/SheetDetail.vue'
import Notifications from './views/Notifications.vue'
import ShareView from './views/ShareView.vue'
import RecipeGenerate from './views/RecipeGenerate.vue'
import RecipeDetail from './views/RecipeDetail.vue'
import CookMode from './views/CookMode.vue'
import Pantry from './views/Pantry.vue'
import Profile from './views/Profile.vue'

const routes = [
  { path: '/', redirect: '/circles' },
  { path: '/login', component: Login, meta: { depth: 0 } },
  { path: '/circles', component: Circles, meta: { requiresAuth: true, depth: 1 } },
  { path: '/sheets/:id', component: SheetDetail, meta: { requiresAuth: true, depth: 2 } },
  { path: '/notifications', component: Notifications, meta: { requiresAuth: true, depth: 1 } },
  { path: '/recipes/generate', component: RecipeGenerate, meta: { requiresAuth: true, depth: 2 } },
  { path: '/recipes/:id/cook', component: CookMode, meta: { requiresAuth: true, depth: 3 } },
  { path: '/recipes/:id', component: RecipeDetail, meta: { requiresAuth: true, depth: 2 } },
  { path: '/pantry', component: Pantry, meta: { requiresAuth: true, depth: 1 } },
  { path: '/me', component: Profile, meta: { requiresAuth: true, depth: 1 } },
  { path: '/s/:token', component: ShareView, meta: { depth: 0 } },
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

router.afterEach((to, from) => {
  const toDepth = to.meta.depth ?? 1
  const fromDepth = from.meta.depth ?? 1
  to.meta.transition = toDepth === fromDepth ? 'fade' : toDepth > fromDepth ? 'forward' : 'back'
})

export default router
