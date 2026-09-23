<template>
  <div class="login-page">
    <div class="card login-card">
      <span class="glow-orb login-glow" />
      <h2 class="page-title login-title"><span class="brand">Link-Life</span> 登录</h2>
      <p class="hint">请输入小程序"我的页"生成的 6 位绑定码</p>
      <div class="code-wrap">
        <CodeInput v-model="code" :length="6" @keyup.enter="submit" />
      </div>
      <button class="btn btn-primary login-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request, saveTokens } from '../api/request'
import { showToast } from '../utils/toast'
import CodeInput from '../components/CodeInput.vue'

export default {
  components: { CodeInput },
  setup() {
    const router = useRouter()
    const code = ref('')
    const submitting = ref(false)

    async function submit() {
      if (submitting.value) return
      const value = code.value.trim()
      if (!/^\d{6}$/.test(value)) {
        showToast('请输入 6 位数字绑定码')
        return
      }
      submitting.value = true
      try {
        const data = await request('/api/auth/bind', { method: 'POST', data: { code: value } })
        saveTokens(data)
        showToast('登录成功')
        router.push('/circles')
      } catch (err) {
        showToast((err && err.message) || '登录失败')
      } finally {
        submitting.value = false
      }
    }

    return { code, submitting, submit }
  },
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 160px);
  padding: var(--space-4);
}
.login-card {
  position: relative;
  width: 100%;
  max-width: 400px;
  border-radius: var(--radius-lg);
  padding: var(--space-6) var(--space-4);
  text-align: center;
}
.login-glow {
  width: 180px;
  height: 180px;
  top: -40px;
  right: -40px;
}
.login-title {
  position: relative;
  z-index: 1;
  font-size: var(--text-2xl);
}
.brand {
  color: var(--primary-deep);
}
.hint {
  position: relative;
  z-index: 1;
  margin: 0 0 var(--space-4);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}
.code-wrap {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  margin-bottom: var(--gap);
}
@media (max-width: 374px) {
  .code-wrap :deep(.code-input) { gap: var(--space-1); }
}
.login-btn {
  position: relative;
  z-index: 1;
  width: 100%;
}
</style>
