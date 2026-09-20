<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request, clearTokens } from '../api/request'
import { showToast } from '../utils/toast'

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

async function logout() {
  try { await request('/api/auth/logout', { method: 'POST' }) } catch (e) { /* 已失效也算登出 */ }
  clearTokens()
  router.push('/login')
}
</script>

<template>
  <div class="profile">
    <h2 class="page-title">我的</h2>
    <div class="card profile-card" v-if="loaded">
      <h3>口味画像</h3>
      <p v-if="summary">{{ summary }}</p>
      <p v-else class="empty">完成菜谱反馈后沉淀你的口味画像</p>
      <div class="tags" v-if="tags.length">
        <span class="badge-pill is-claimed" v-for="t in tags" :key="t">{{ t }}</span>
      </div>
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
}
.profile-card {
  border-radius: var(--radius-lg);
}
.profile-card h3 {
  margin: 0 0 8px;
}
.profile-card p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}
.profile-card .empty {
  padding: 24px 0;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.logout {
  margin-top: 24px;
  width: 100%;
}
</style>
