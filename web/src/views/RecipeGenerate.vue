<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { streamRequest } from '../api/sse'
import { showToast } from '../utils/toast'

const route = useRoute()
const router = useRouter()
const streamText = ref('')
const failed = ref(false)
const errorText = ref('')

function start() {
  streamText.value = ''
  failed.value = false
  errorText.value = ''
  const isIterate = !!route.query.recipeId
  const path = isIterate
    ? '/api/recipes/' + route.query.recipeId + '/iterate'
    : '/api/recipes/generate'
  const body = isIterate
    ? { comment: route.query.comment || '' }
    : { circleId: Number(route.query.circleId), dishName: route.query.dishName }
  streamRequest(path, body, {
    onDelta: (text) => { streamText.value += text },
    onDone: (payload) => router.replace('/recipes/' + payload.recipeId),
    onError: (err) => {
      failed.value = true
      errorText.value = err.message || '生成失败，请重试'
    },
  })
}

const circleId = route.query.circleId
const dishName = Array.isArray(route.query.dishName) ? route.query.dishName[0] : route.query.dishName
if (!circleId || !dishName) {
  showToast('缺少菜谱参数')
  router.replace('/circles')
} else {
  start()
}
</script>

<template>
  <div class="generate">
    <pre class="stream">{{ streamText || '正在生成菜谱…' }}</pre>
    <p v-if="errorText" class="error">{{ errorText }}</p>
    <button v-if="failed" @click="start">重试</button>
  </div>
</template>

<style scoped>
.generate { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
.stream { background: #fff; border-radius: 12px; padding: 24px; min-height: 320px;
  white-space: pre-wrap; word-break: break-all; font-size: 14px; line-height: 1.8; }
.error { color: #e64340; margin-top: 12px; }
</style>
