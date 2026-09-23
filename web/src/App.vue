<template>
  <div class="app">
    <header v-if="showNav" class="topbar">
      <span class="brand">Link-Life</span>
      <nav class="topbar-nav">
        <router-link to="/circles" class="nav-link">首页</router-link>
        <router-link v-if="user" to="/notifications" class="nav-link">
          通知<span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
        <router-link v-if="user" to="/me" class="nav-link">我的</router-link>
      </nav>
      <span class="topbar-right">
        <span v-if="user" class="nickname">{{ user.nickname }}</span>
        <button v-if="user" class="btn btn-secondary btn-small" @click="logout">退出</button>
      </span>
    </header>
    <main class="main">
      <PageTransition>
        <router-view :key="route.name || route.path" />
      </PageTransition>
    </main>
    <nav v-if="showNav" class="tabbar" :style="{ '--tab-i': tabIndex }">
      <span v-if="user && tabIndex !== null" class="tabbar-ind"></span>
      <router-link to="/circles" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        <span>首页</span>
      </router-link>
      <router-link v-if="user" to="/notifications" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
        <span>通知</span>
        <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      </router-link>
      <router-link v-if="user" to="/me" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        <span>我的</span>
      </router-link>
    </nav>
    <AppToast />
    <ConfirmDialog ref="cdRef" />
  </div>
</template>

<script>
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, clearTokens } from './api/request'
import { fetchUnreadCount } from './api/notifications'
import { registerConfirm } from './utils/confirm'
import PageTransition from './components/PageTransition.vue'
import AppToast from './components/AppToast.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'

export default {
  components: { PageTransition, AppToast, ConfirmDialog },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const user = ref(null)
    const cdRef = ref(null)
    onMounted(() => {
      registerConfirm(cdRef.value.confirm)
    })

    const showNav = computed(() => route.meta.requiresAuth === true)

    async function loadUser() {
      try {
        user.value = await request('/api/me')
      } catch (err) {
        user.value = null
      }
    }

    const unread = ref(0)
    let pollTimer = null

    const tabIndex = ref(0)
    watch(
      () => route.path,
      () => {
        const p = route.path
        tabIndex.value = p.startsWith('/notifications') ? 1 : p.startsWith('/me') ? 2 : p.startsWith('/circles') ? 0 : null
      },
      { immediate: true }
    )

    async function refreshUnread() {
      if (document.hidden || !localStorage.getItem('accessToken')) return
      try {
        const data = await fetchUnreadCount()
        unread.value = data.unreadCount
      } catch (err) {
        // 401 等错误静默，请求封装已处理刷新/跳登录
      }
    }

    function startPolling() {
      if (pollTimer) return
      refreshUnread()
      pollTimer = setInterval(refreshUnread, 30000)
    }

    function stopPolling() {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
      unread.value = 0
    }

    async function logout() {
      try { await request('/api/auth/logout', { method: 'POST' }) } catch (e) { /* 已失效也算登出 */ }
      clearTokens()
      stopPolling()
      user.value = null
      router.push('/login')
    }

    watch(
      () => route.path,
      () => {
        if (route.meta.requiresAuth && localStorage.getItem('accessToken')) {
          if (!user.value) loadUser()
          startPolling()
        } else {
          stopPolling()
        }
      },
      { immediate: true }
    )

    return { route, user, showNav, unread, logout, tabIndex, cdRef }
  },
}
</script>

<style>
.app { min-height: 100%; display: flex; flex-direction: column; }
.main { flex: 1; width: 100%; max-width: 1024px; margin: 0 auto; padding: 24px 16px calc(24px + env(safe-area-inset-bottom)); }

.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  background: rgba(255, 251, 235, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}
.brand { font-weight: 700; font-size: 18px; color: var(--primary-deep); }
.topbar-nav { display: flex; gap: 8px; }
.nav-link {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 999px;
  color: var(--text-secondary); font-size: 14px; font-weight: 600;
  position: relative;
  transition: background 200ms ease-out, color 200ms ease-out;
}
.nav-link::after {
  content: '';
  position: absolute;
  left: 14px; right: 14px; bottom: -3px;
  height: 1px;
  border-radius: 1px;
  background: var(--grad-flame);
  opacity: 0;
  transform: scaleX(0.4);
  transition: opacity var(--duration-exit) var(--ease-out-soft), transform var(--duration-exit) var(--ease-out-soft);
}
.nav-link:hover { background: var(--primary-weak); color: var(--primary-deep); }
.nav-link.router-link-active { background: var(--primary); color: #fff; }
.nav-link.router-link-active::after { opacity: 1; transform: scaleX(1); }
.topbar-right { display: flex; align-items: center; gap: 12px; }
.nickname { color: var(--text-secondary); font-size: 14px; }
.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 999px; background: var(--danger); color: #fff;
  font-size: 11px; font-weight: 700; line-height: 1;
}

.tabbar { display: none; }

@media (max-width: 767px) {
  .topbar { display: none; }
  .main { padding: 16px 16px calc(88px + env(safe-area-inset-bottom)); }
  .tabbar {
    position: fixed;
    left: 0; right: 0; bottom: 0;
    z-index: 100;
    display: flex;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(8px);
    border-top: 1px solid var(--border);
    padding-bottom: env(safe-area-inset-bottom);
  }
  .tabbar-ind {
    position: absolute;
    top: 0;
    left: 0;
    width: calc(100% / 3);
    height: 3px;
    pointer-events: none;
    transform: translateX(calc(var(--tab-i) * 100%));
    transition: transform var(--duration-page) var(--ease-out-soft);
  }
  .tabbar-ind::after {
    content: '';
    display: block;
    width: 22px;
    height: 3px;
    margin: 0 auto;
    border-radius: 0 0 3px 3px;
    background: var(--grad-flame);
  }
  .tabbar-item {
    flex: 1;
    display: flex; flex-direction: column; align-items: center; gap: 2px;
    padding: 8px 0 6px;
    color: var(--text-tertiary); font-size: 11px; position: relative;
  }
  .tabbar-item.router-link-active { color: var(--primary); }
  .tabbar-item .badge { position: absolute; top: 4px; right: calc(50% - 20px); }
}
</style>
