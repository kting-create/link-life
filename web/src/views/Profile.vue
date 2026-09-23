<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request, clearTokens } from '../api/request'
import { showToast } from '../utils/toast'
import EmptyState from '../components/EmptyState.vue'
import Skeleton from '../components/Skeleton.vue'

const router = useRouter()
const summary = ref('')
const tags = ref([])
const loaded = ref(false)

request('/api/me/taste-profile')
  .then((p) => {
    summary.value = p.summary || ''
    tags.value = p.tags || []
    loaded.value = true
  })
  .catch((e) => showToast(e.message || '加载失败'))

let loggingOut = false
async function logout() {
  if (loggingOut) return
  loggingOut = true
  try {
    try { await request('/api/auth/logout', { method: 'POST' }) } catch (e) { /* 已失效也算登出 */ }
    clearTokens()
    await router.replace('/login')
  } finally {
    loggingOut = false
  }
}
</script>

<template>
  <div class="profile">
    <h2 class="page-title">我的</h2>
    <Skeleton v-if="!loaded" :rows="3" />
    <div class="card profile-card" v-else>
      <span class="glow-orb profile-glow" />
      <h3>口味画像</h3>
      <p v-if="summary" class="summary">{{ summary }}</p>
      <EmptyState v-else title="完成菜谱反馈后沉淀你的口味画像" />
      <div class="tags" v-if="tags.length">
        <span class="badge-pill is-open" v-for="t in tags" :key="t">{{ t }}</span>
      </div>
    </div>
    <div class="card nav-card">
      <router-link to="/notifications" class="nav-link">我的通知</router-link>
      <router-link to="/pantry" class="nav-link">调料架</router-link>
      <router-link to="/circles" class="nav-link">返回圈子列表</router-link>
    </div>
    <button class="btn btn-danger logout" @click="logout">退出登录</button>
  </div>
</template>

<style scoped>
.profile {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}
.profile-card {
  position: relative;
  border-radius: var(--radius-lg);
}
.profile-glow {
  width: 160px;
  height: 160px;
  top: -36px;
  right: -36px;
}
.profile-card h3 {
  position: relative;
  z-index: 1;
  margin: 0 0 var(--space-2);
}
.summary {
  position: relative;
  z-index: 1;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.6;
}
.tags {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.profile-card :deep(.empty) {
  position: relative;
  z-index: 1;
}
.nav-card {
  padding: var(--space-2);
}
.nav-link {
  display: block;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  transition: background 0.2s ease;
}
.nav-link:hover {
  background: var(--primary-weak);
}
.nav-link + .nav-link {
  margin-top: var(--space-1);
}
.logout {
  margin-top: var(--space-2);
  width: 100%;
}
</style>
