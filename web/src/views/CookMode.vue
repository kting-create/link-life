<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { animate } from 'motion-v'
import { getRecipe, listPhotos, analyzePhoto, applyPhoto, uploadPhoto, deletePhoto } from '../api/recipe'
import { showToast } from '../utils/toast'
import { SPRING_PAGE, SPRING_BOUNCE, prefersReducedMotion } from '../styles/motion.js'
import EmptyState from '../components/EmptyState.vue'
import Skeleton from '../components/Skeleton.vue'
import Icon from '../components/Icon.vue'
import IconButton from '../components/IconButton.vue'
import PhotoThumb from '../components/PhotoThumb.vue'
import Lightbox from '../components/Lightbox.vue'

const route = useRoute()
const id = route.params.id
const recipe = ref(null)
const steps = ref([])
const current = ref(0)
const remainSec = ref(0)
const counting = ref(false)
const photosByStep = ref({})
const advice = ref(null)
const advicePhotoId = ref(null)
const loading = ref(true)
const stepEl = ref(null)
const remainEl = ref(null)
const fileInput = ref(null)
const lightboxOpen = ref(false)
const lightboxSrc = ref('')

let timer = null
let audioCtx = null

const step = computed(() => steps.value[current.value] || {})
const hasDuration = computed(() => (step.value.durationSec || 0) > 0)
const remainText = computed(() => {
  const m = Math.floor(remainSec.value / 60)
  const s = remainSec.value % 60
  return `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`
})
const progressPct = computed(() => {
  const total = step.value.durationSec || 1
  return Math.max(0, Math.round(((total - remainSec.value) / total) * 100))
})

function beep() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)()
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.connect(gain)
    gain.connect(audioCtx.destination)
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.4, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.8)
    osc.start()
    osc.stop(audioCtx.currentTime + 0.8)
  } catch (e) { /* 音频不可用则静默 */ }
}

function warmAudio() {
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume()
  } catch (e) { /* 音频不可用则静默 */ }
}

function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
  counting.value = false
}

function pulseRemain() {
  const el = remainEl.value
  if (!el || prefersReducedMotion()) return
  animate(el, { scale: [1, 1.2, 1] }, { ...SPRING_BOUNCE })
}

function startTimer() {
  if (timer || remainSec.value <= 0) return
  counting.value = true
  timer = setInterval(() => {
    remainSec.value = Math.max(0, remainSec.value - 1)
    if (remainSec.value === 0) { stopTimer(); beep(); pulseRemain() }
  }, 1000)
}

function applyStepTiming() {
  stopTimer()
  remainSec.value = step.value.durationSec || 0
  if (remainSec.value > 0) startTimer()
}

function enterStep(idx) {
  if (current.value === idx) {
    applyStepTiming()
  } else {
    current.value = idx
  }
}

const stepDir = ref(1)
function goStep(dir) {
  const el = stepEl.value
  const target = current.value + dir
  if (target < 0) { showToast('已是第一步'); return }
  if (target >= steps.value.length) { showToast('已是最后一步'); return }
  stepDir.value = dir
  current.value = target
  if (prefersReducedMotion()) return
  animate(el,
    { opacity: [0, 1], transform: [`translateX(${dir * 100}%)`, 'translateX(0)'] },
    { ...SPRING_PAGE })
}

watch(current, applyStepTiming)

function toggleTimer() {
  if (counting.value) { stopTimer(); return }
  if (remainSec.value <= 0) { goStep(1); return }
  startTimer()
}
function skipTimer() {
  if (current.value + 1 >= steps.value.length) { showToast('已是最后一步'); return }
  stopTimer()
  goStep(1)
}

async function load() {
  try {
    recipe.value = await getRecipe(id)
    steps.value = (recipe.value.content.steps || []).map((s) => ({ ...s }))
    current.value = Math.min(current.value, Math.max(steps.value.length - 1, 0))
    enterStep(current.value)
    const photos = await listPhotos(id)
    const byStep = {}
    photos.forEach((p) => { (byStep[p.stepNo] = byStep[p.stepNo] || []).push(p) })
    photosByStep.value = byStep
  } catch (e) { showToast(e.message || '加载失败') } finally {
    loading.value = false
  }
}
load()

function triggerFile() {
  if (fileInput.value) fileInput.value.click()
}

async function onFileChange(e, stepNo) {
  const file = e.target.files[0]
  if (!file) return
  try {
    await uploadPhoto(id, stepNo, file)
    showToast('已上传')
    await load()
  } catch (err) { showToast(err.message || '上传失败') }
  e.target.value = ''
}

async function askAi(photoId) {
  try {
    advice.value = await analyzePhoto(photoId)
    advicePhotoId.value = photoId
  } catch (e) { showToast(e.message || '分析失败') }
}

async function doApply() {
  try {
    await applyPhoto(advicePhotoId.value)
    showToast('已生成新版本')
    advice.value = null
    advicePhotoId.value = null
    await load()
  } catch (e) { showToast(e.message || '应用失败') }
}

async function doDelete(photoId) {
  try {
    await deletePhoto(photoId)
    await load()
  } catch (e) { showToast(e.message || '删除失败') }
}

function openLightbox(src) {
  lightboxSrc.value = src
  lightboxOpen.value = true
}

onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="cook" v-if="recipe" @click="warmAudio">
    <div class="bar"><div class="bar-inner" :style="{ width: progressPct + '%' }"></div></div>
    <p class="head">步骤 {{ step.no }} / {{ steps.length }}</p>
    <p ref="stepEl" class="step-text">{{ step.text }}</p>
    <div v-if="hasDuration" class="timer">
      <p ref="remainEl" class="remain">{{ remainText }}</p>
      <div class="timer-btns">
        <button class="btn btn-primary" @click="toggleTimer">{{ counting ? '暂停' : '继续' }}</button>
        <button class="btn btn-secondary" @click="skipTimer">跳过</button>
      </div>
    </div>
    <p v-else class="meta">本步骤无需计时</p>
    <div class="nav">
      <button class="btn btn-secondary btn-lg" @click="goStep(-1)">上一步</button>
      <div class="upload-wrap">
        <IconButton name="camera" title="拍照" @click="triggerFile" />
        <input ref="fileInput" type="file" accept="image/*" capture="environment" hidden
               @change="onFileChange($event, step.no)" />
      </div>
      <button class="btn btn-primary btn-lg" @click="goStep(1)">下一步</button>
    </div>

    <div class="photos" v-if="(photosByStep[step.no] || []).length">
      <div class="photo" v-for="p in photosByStep[step.no]" :key="p.id">
        <PhotoThumb :src="p.url" :width="96" @open="openLightbox(p.url)" />
        <div class="photo-actions">
          <button class="btn btn-secondary btn-small" @click="askAi(p.id)">
            <Icon name="sparkle" />{{ p.analyzed ? '重新分析' : '问 AI' }}
          </button>
          <IconButton name="trash" title="删除" @click="doDelete(p.id)" />
        </div>
      </div>
    </div>

    <div class="advice glass-card" v-if="advice">
      <h3>AI 建议</h3>
      <p>{{ advice.advice }}</p>
      <p v-for="(c, i) in advice.changes" :key="i" class="change">
        步骤 {{ c.stepNo }}：{{ c.text || '仅调整时长' }}
      </p>
      <div class="advice-btns">
        <button class="btn btn-primary" @click="doApply">应用修改</button>
        <button class="btn btn-secondary" @click="advice = null">忽略</button>
      </div>
    </div>

    <Lightbox :src="lightboxSrc" :open="lightboxOpen" @update:open="lightboxOpen = $event" />
  </div>
  <div class="cook" v-else-if="loading">
    <Skeleton :rows="4" />
  </div>
  <EmptyState v-else title="加载失败" />
</template>

<style scoped>
.cook { max-width: 720px; margin: 0 auto; padding: var(--space-4); background: var(--bg-page); color: var(--text-primary); min-height: 100vh; box-sizing: border-box; }
.bar { height: 8px; background: var(--primary-weak); border-radius: 999px; overflow: hidden; }
.bar-inner { height: 100%; background: var(--primary); border-radius: 999px; transition: width .5s var(--ease-out-soft); }
.head { text-align: center; color: var(--text-secondary); font-size: var(--text-sm); }
.step-text { font-size: var(--text-2xl); line-height: 1.6; margin: var(--space-6) 0; text-align: center; will-change: transform, opacity; }
.meta { color: var(--text-secondary); text-align: center; font-size: var(--text-sm); }
.timer { text-align: center; }
.remain { font-family: 'Nunito Sans', 'PingFang SC', system-ui, sans-serif; font-size: var(--text-3xl); font-weight: 700; color: var(--primary); margin: 0 0 var(--space-3); }
.timer-btns { display: flex; gap: var(--space-3); justify-content: center; }
.nav { display: flex; gap: var(--space-3); justify-content: center; align-items: center; margin: var(--space-4) 0; }
.btn-lg { padding: var(--space-3) var(--space-6); font-size: var(--text-md); }
.upload-wrap { display: inline-flex; }
.photos { display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-4); }
.photo { display: flex; flex-direction: column; gap: var(--space-2); }
.photo-actions { display: flex; gap: var(--space-2); align-items: center; }
.advice { margin-top: var(--space-4); }
.advice h3 { margin: 0 0 var(--space-2); }
.advice p { margin: 0 0 var(--space-2); }
.change { color: var(--text-secondary); font-size: var(--text-xs); }
.advice-btns { display: flex; gap: var(--space-3); margin-top: var(--space-3); }
</style>
