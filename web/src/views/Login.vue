<template>
  <div class="login-page">
    <div class="card login-card">
      <h2 class="page-title"><span class="brand">Link-Life</span> 登录</h2>
      <p class="hint">请输入小程序"我的页"生成的 6 位绑定码</p>
      <input
        v-model="code"
        class="input code-input"
        maxlength="6"
        inputmode="numeric"
        placeholder="6 位绑定码"
        @keyup.enter="submit"
      />
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

export default {
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
  padding: 16px;
}
.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: var(--radius-lg);
  padding: 32px 24px;
  text-align: center;
}
.brand {
  color: var(--primary-deep);
}
.hint {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 14px;
}
.code-input {
  margin-bottom: var(--gap);
  text-align: center;
  font-size: 20px;
  letter-spacing: 6px;
}
.login-btn {
  width: 100%;
}
</style>
