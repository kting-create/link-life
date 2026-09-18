<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRecipe, listPhotos, analyzePhoto, applyPhoto, uploadPhoto, deletePhoto } from '../api/recipe'
import { showToast } from '../utils/toast'

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

function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
  counting.value = false
}

function startTimer() {
  if (timer || remainSec.value <= 0) return
  counting.value = true
  timer = setInterval(() => {
    remainSec.value = Math.max(0, remainSec.value - 1)
    if (remainSec.value === 0) { stopTimer(); beep() }
  }, 1000)
}

function enterStep(idx) {
  stopTimer()
  current.value = idx
  remainSec.value = step.value.durationSec || 0
  if (remainSec.value > 0) startTimer()
}

function toggleTimer() { counting.value ? stopTimer() : startTimer() }
function skipTimer() { stopTimer(); remainSec.value = 0 }
function prevStep() { if (current.value > 0) enterStep(current.value - 1) }
function nextStep() { if (current.value < steps.value.length - 1) enterStep(current.value + 1) }

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
  } catch (e) { showToast(e.message || '加载失败') }
}
load()

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

onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="cook" v-if="recipe">
    <div class="bar"><div class="bar-inner" :style="{ width: progressPct + '%' }"></div></div>
    <p class="head">步骤 {{ step.no }} / {{ steps.length }}</p>
    <p class="step-text">{{ step.text }}</p>
    <div v-if="hasDuration" class="timer">
      <p class="remain">{{ remainText }}</p>
      <button @click="toggleTimer">{{ counting ? '暂停' : '继续' }}</button>
      <button @click="skipTimer">跳过</button>
    </div>
    <p v-else class="meta">本步骤无需计时</p>
    <div class="nav">
      <button :disabled="current === 0" @click="prevStep">上一步</button>
      <label class="upload-btn">
        拍照
        <input type="file" accept="image/*" capture="environment" hidden
               @change="onFileChange($event, step.no)" />
      </label>
      <button :disabled="current === steps.length - 1" @click="nextStep">下一步</button>
    </div>

    <div class="photos" v-if="(photosByStep[step.no] || []).length">
      <div class="photo" v-for="p in photosByStep[step.no]" :key="p.id">
        <img :src="p.url" />
        <a href="#" @click.prevent="askAi(p.id)">{{ p.analyzed ? '重新分析' : '问 AI' }}</a>
        <a href="#" class="del" @click.prevent="doDelete(p.id)">删除</a>
      </div>
    </div>

    <div class="advice" v-if="advice">
      <h3>AI 建议</h3>
      <p>{{ advice.advice }}</p>
      <p v-for="(c, i) in advice.changes" :key="i" class="change">
        步骤 {{ c.stepNo }}：{{ c.text || '仅调整时长' }}
      </p>
      <button @click="doApply">应用修改</button>
      <button @click="advice = null">忽略</button>
    </div>
  </div>
  <div v-else>加载中…</div>
</template>

<style scoped>
.cook { max-width: 720px; margin: 0 auto; padding: 16px; background: #111; color: #fff; min-height: 100vh; box-sizing: border-box; }
.bar { height: 6px; background: #333; border-radius: 3px; overflow: hidden; }
.bar-inner { height: 100%; background: #07c160; transition: width .5s; }
.head { text-align: center; color: #aaa; }
.step-text { font-size: 24px; line-height: 1.6; margin: 32px 0; }
.timer { text-align: center; }
.remain { font-size: 64px; font-weight: 700; color: #07c160; }
.nav { display: flex; gap: 12px; justify-content: center; margin: 16px 0; }
.upload-btn { background: #07c160; color: #fff; padding: 6px 14px; border-radius: 6px; cursor: pointer; }
.photos { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
.photo img { width: 100px; height: 100px; object-fit: cover; border-radius: 8px; display: block; }
.photo a { color: #07c160; font-size: 12px; margin-right: 8px; }
.photo .del { color: #e66; }
.advice { background: #1e1e1e; border-radius: 12px; padding: 16px; margin-top: 16px; }
.change { color: #ccc; font-size: 13px; }
</style>
