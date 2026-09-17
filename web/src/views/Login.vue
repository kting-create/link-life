<template>
  <div class="login-page">
    <div class="card login-card">
      <h2>Link-Life 登录</h2>
      <p class="muted">请输入小程序"我的页"生成的 6 位绑定码</p>
      <input
        v-model="code"
        class="input code-input"
        maxlength="6"
        inputmode="numeric"
        placeholder="6 位绑定码"
        @keyup.enter="submit"
      />
      <button class="btn login-btn" :disabled="submitting" @click="submit">登录</button>
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
  justify-content: center;
  padding-top: 80px;
}
.login-card {
  width: 320px;
  text-align: center;
}
.code-input {
  text-align: center;
  font-size: 20px;
  letter-spacing: 6px;
  margin: 12px 0;
}
.login-btn {
  width: 100%;
}
</style>
