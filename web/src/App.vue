<template>
  <div class="app">
    <header v-if="showNav" class="topbar">
      <span class="brand">Link-Life</span>
      <span class="topbar-right">
        <router-link v-if="user" to="/notifications" class="nav-notif">
          通知<span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
        <span v-if="user" class="nickname">{{ user.nickname }}</span>
        <button v-if="user" class="btn btn-small" @click="logout">退出</button>
      </span>
    </header>
    <main class="main">
      <router-view :key="route.fullPath" />
    </main>
    <div v-if="toast.visible" class="toast">{{ toast.text }}</div>
  </div>
</template>

<script>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, clearTokens } from './api/request'
import { fetchUnreadCount } from './api/notifications'
import { toast } from './utils/toast'

export default {
  setup() {
    const route = useRoute()
    const router = useRouter()
    const user = ref(null)

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

    function logout() {
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

    return { route, user, showNav, unread, logout, toast }
  },
}
</script>

<style>
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', sans-serif;
  background: #f5f6f8;
  color: #333;
}
.app {
  min-height: 100vh;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  z-index: 10;
}
.brand {
  font-weight: 600;
  color: #4f7cff;
}
.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.nav-notif {
  color: #333;
  text-decoration: none;
  font-size: 14px;
}
.nav-notif .badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 10px;
  background: #ff4d4f;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}
.nickname {
  color: #666;
}
.btn {
  border: none;
  border-radius: 6px;
  padding: 8px 14px;
  background: #4f7cff;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
.btn:hover {
  opacity: 0.9;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-small {
  padding: 5px 10px;
  font-size: 13px;
}
.btn-ghost {
  background: #fff;
  color: #4f7cff;
  border: 1px solid #4f7cff;
}
.btn-warn {
  background: #ff7a45;
}
.main {
  max-width: 720px;
  margin: 0 auto;
  padding: 16px;
}
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.card.clickable {
  cursor: pointer;
}
.card.active {
  border-color: #4f7cff;
}
.input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9dce3;
  border-radius: 6px;
  font-size: 14px;
}
.row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.toast {
  position: fixed;
  left: 50%;
  bottom: 60px;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  z-index: 100;
}
.muted {
  color: #999;
  font-size: 13px;
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  background: #eef2ff;
  color: #4f7cff;
}
</style>
