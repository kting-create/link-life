<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { streamRequest } from '../api/sse'
import { showToast } from '../utils/toast'
import { navigateWithHero } from '../utils/vtNav'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const segs = ref([])
const streaming = ref(false)
const failed = ref(false)
const errorText = ref('')
const streamEl = ref(null)

function start() {
  segs.value = []
  streaming.value = true
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
    onDelta: (text) => { segs.value = segs.value.concat([text]) },
    onDone: (payload) => {
      streaming.value = false
      return navigateWithHero(streamEl.value, 'recipe-hero', () => router.replace('/recipes/' + payload.recipeId))
    },
    onError: (err) => {
      streaming.value = false
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
    <pre ref="streamEl" class="card stream" :class="{ breathing: !segs.length && !failed }"><template v-if="segs.length"><span v-for="(seg, i) in segs" :key="i" class="seg">{{ seg }}</span><span v-if="streaming" class="cursor">▍</span></template><template v-else-if="!failed">正在生成菜谱…<span v-if="streaming" class="cursor">▍</span></template></pre>
    <p v-if="errorText" class="error">{{ errorText }}</p>
    <button v-if="failed" class="btn btn-primary retry" @click="start">
      <Icon name="sparkle" />重试
    </button>
  </div>
</template>

<style scoped>
.generate {
  max-width: 720px;
  margin: 0 auto;
}
.stream {
  margin: 0;
  padding: var(--space-5);
  min-height: 320px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: var(--text-sm);
  line-height: 1.8;
  font-family: inherit;
}
.seg {
  animation: st-in .3s var(--ease-out-soft) both;
}
.cursor {
  display: inline-block;
  color: var(--primary);
  animation: blink 1s infinite;
}
@keyframes blink {
  50% { opacity: 0; }
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
  margin-top: var(--space-3);
  font-size: var(--text-sm);
}
.retry {
  width: 100%;
  margin-top: var(--space-3);
}
</style>
