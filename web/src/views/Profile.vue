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
    <h2>我的</h2>
    <div class="card" v-if="loaded">
      <h3>口味画像</h3>
      <p v-if="summary">{{ summary }}</p>
      <p v-else class="empty">完成菜谱反馈后沉淀你的口味画像</p>
      <div class="tags" v-if="tags.length">
        <span class="tag" v-for="t in tags" :key="t">{{ t }}</span>
      </div>
    </div>
    <button class="logout" @click="logout">退出登录</button>
  </div>
</template>

<style scoped>
.profile { max-width: 720px; margin: 0 auto; padding: 16px; }
.card { background: var(--bg-card); border-radius: var(--radius); padding: 16px; }
.empty { color: var(--text-secondary); }
.tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.tag { background: var(--primary-weak); color: var(--primary); padding: 2px 10px; border-radius: 999px; font-size: 13px; }
.logout { margin-top: 24px; width: 100%; padding: 10px; border: none; border-radius: var(--radius); background: #fdecec; color: #d33; cursor: pointer; }
</style>
