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
    <pre class="card stream" :class="{ breathing: !streamText && !failed }">{{ streamText || '正在生成菜谱…' }}</pre>
    <p v-if="errorText" class="error">{{ errorText }}</p>
    <button v-if="failed" class="btn btn-primary retry" @click="start">重试</button>
  </div>
</template>

<style scoped>
.generate {
  max-width: 720px;
  margin: 0 auto;
}
.stream {
  margin: 0;
  padding: 24px;
  min-height: 320px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 14px;
  line-height: 1.8;
  font-family: inherit;
}
.breathing {
  animation: breathe 2000ms ease-in-out infinite;
}
@keyframes breathe {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}
.error {
  color: var(--danger);
  margin-top: 12px;
}
.retry {
  width: 100%;
  margin-top: 12px;
}
</style>
