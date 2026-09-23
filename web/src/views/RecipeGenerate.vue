<script setup>
import { computed, ref } from 'vue'
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

const circleId = computed(() => route.query.circleId || null)
const dishName = computed(() => route.query.dishName || null)
const recipeId = computed(() => route.query.recipeId || null)
const comment = computed(() => route.query.comment || '')
const isIterate = computed(() => !!recipeId.value)

function startGenerate() {
  segs.value = []
  streaming.value = true
  failed.value = false
  errorText.value = ''
  const path = isIterate.value
    ? '/api/recipes/' + recipeId.value + '/iterate'
    : '/api/recipes/generate'
  const dish = Array.isArray(dishName.value) ? dishName.value[0] : dishName.value
  const body = isIterate.value
    ? { comment: comment.value }
    : { circleId: Number(circleId.value), dishName: dish }
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

if (!isIterate.value && !(circleId.value && dishName.value)) {
  showToast('缺少菜谱参数', 'err')
  router.replace('/circles')
} else {
  startGenerate()
}
</script>

<template>
  <div class="generate">
    <pre ref="streamEl" class="card stream" :class="{ breathing: !segs.length && !failed }"><template v-if="segs.length"><span v-for="(seg, i) in segs" :key="i" class="seg">{{ seg }}</span><span v-if="streaming" class="cursor">▍</span></template><template v-else-if="!failed">正在生成菜谱…<span v-if="streaming" class="cursor">▍</span></template></pre>
    <p v-if="errorText" class="error">{{ errorText }}</p>
    <button v-if="failed" class="btn btn-primary retry" @click="startGenerate">
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
