<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRecipe, getVersions, submitFeedback, editRecipe, rollback, listPhotos, deletePhoto, uploadPhoto } from '../api/recipe'
import { showToast } from '../utils/toast'
import { animate } from 'motion-v'
import { SPRING_BOUNCE, prefersReducedMotion } from '../styles/motion.js'
import EmptyState from '../components/EmptyState.vue'
import Skeleton from '../components/Skeleton.vue'
import Field from '../components/Field.vue'
import Icon from '../components/Icon.vue'
import IconButton from '../components/IconButton.vue'
import PhotoThumb from '../components/PhotoThumb.vue'
import Lightbox from '../components/Lightbox.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const recipe = ref(null)
const versions = ref([])
const myScore = ref(0)
const myComment = ref('')
const nameInput = ref('')
const atLimit = computed(() => versions.value.length >= 5)

const SOURCE_TEXT = { AI_GENERATE: 'AI 生成', AI_ITERATE: 'AI 迭代', MANUAL_EDIT: '手动编辑', PHOTO_ANALYSIS: '拍照修正' }

const loadFailed = ref(false)
const loading = ref(true)

async function load() {
  try {
    recipe.value = await getRecipe(id)
    nameInput.value = recipe.value.customName || ''
    versions.value = (await getVersions(id)).map((v) => ({
      ...v,
      sourceText: SOURCE_TEXT[v.source] || v.source,
      createdAtText: (v.createdAt || '').replace('T', ' ').slice(0, 16),
    }))
    await loadPhotos()
    loadFailed.value = false
  } catch (e) {
    loadFailed.value = true
    showToast(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}
load()

const editMode = ref(false)
const editForm = ref(null)

function startEdit() {
  const c = recipe.value.content
  editForm.value = {
    servings: c.servings,
    totalMinutes: c.totalMinutes,
    ingredients: (c.ingredients || []).map((i) => ({ ...i })),
    seasonings: (c.seasonings || []).map((i) => ({ ...i })),
    steps: (c.steps || []).map((s) => ({ text: s.text, durationSec: s.durationSec || 0 })),
    tips: c.tips || '',
  }
  editMode.value = true
}

function addIngredientRow(list) {
  list.push({ name: '', amount: '' })
}

function addStepRow() {
  editForm.value.steps.push({ text: '', durationSec: 0 })
}

async function saveEdit() {
  const f = editForm.value
  if (!f.ingredients.some((i) => i.name.trim()) || !f.steps.some((s) => s.text.trim())) {
    showToast('食材和步骤不能为空')
    return
  }
  const content = {
    servings: Number(f.servings) || 2,
    totalMinutes: Number(f.totalMinutes) || 30,
    ingredients: f.ingredients.filter((i) => i.name.trim())
      .map((i) => ({ name: i.name.trim(), amount: i.amount })),
    seasonings: f.seasonings.filter((i) => i.name.trim())
      .map((i) => ({ name: i.name.trim(), amount: i.amount })),
    steps: f.steps.filter((s) => s.text.trim())
      .map((s, idx) => ({
        no: idx + 1,
        text: s.text.trim(),
        ...(Number(s.durationSec) ? { durationSec: Number(s.durationSec) } : {}),
      })),
    tips: f.tips,
  }
  try {
    await editRecipe(id, { content, changeNote: '手动编辑' })
    showToast('已保存')
    editMode.value = false
    await load()
  } catch (e) { showToast(e.message || '保存失败') }
}

async function doFeedback() {
  if (!myScore.value) { showToast('先点星星评分'); return }
  try {
    await submitFeedback(id, myScore.value, myComment.value || null)
    showToast('已提交')
  } catch (e) { showToast(e.message || '提交失败') }
}

async function doRollback(v) {
  try {
    await rollback(id, v)
    await load()
  } catch (e) { showToast(e.message || '回滚失败') }
}

async function saveName() {
  try {
    await editRecipe(id, { customName: nameInput.value || null })
    showToast('已保存')
    await load()
  } catch (e) { showToast(e.message || '保存失败') }
}

const photos = ref([])
const lightboxOpen = ref(false)
const lightboxSrc = ref('')

function openLightbox(src) {
  lightboxSrc.value = src
  lightboxOpen.value = true
}

async function loadPhotos() {
  try { photos.value = await listPhotos(id) } catch (e) { /* 静默 */ }
}
loadPhotos()

async function onPhotoFile(e, stepNo) {
  const file = e.target.files[0]
  if (!file) return
  try {
    await uploadPhoto(id, stepNo, file)
    showToast('已上传')
    await loadPhotos()
  } catch (err) { showToast(err.message || '上传失败') }
  e.target.value = ''
}

async function doDeletePhoto(photoId) {
  try {
    await deletePhoto(photoId)
    await loadPhotos()
  } catch (e) { showToast(e.message || '删除失败') }
}

function photosOf(stepNo) { return photos.value.filter((p) => p.stepNo === stepNo) }

function stepNoText(no) { return String(no).padStart(2, '0') }

const fileInputs = ref({})
function setFileRef(el, no) {
  if (el) fileInputs.value[no] = el
}
function triggerFile(no) {
  const el = fileInputs.value[no]
  if (el) el.click()
}

const starEls = ref([])
function setStarRef(el, n) {
  if (el) starEls.value[n - 1] = el
}

function onStar(n) {
  myScore.value = n
  const el = starEls.value[n - 1]
  if (!el || prefersReducedMotion()) return
  animate(el, { scale: [1, 1.45, 1], rotate: [0, -8, 0] }, { ...SPRING_BOUNCE })
}

function openCook() {
  router.push(`/recipes/${id}/cook`)
}

function openGenerate() {
  router.push({ path: '/recipes/generate', query: { recipeId: id } })
}
</script>

<template>
  <div class="detail" v-if="loading && !recipe && !loadFailed">
    <div class="card"><Skeleton :rows="4" /></div>
  </div>
  <div class="detail" v-else-if="loadFailed && !recipe">
    <EmptyState title="加载失败，请稍后再试">
      <template #action>
        <button class="btn btn-secondary btn-small" @click="load">重试</button>
      </template>
    </EmptyState>
  </div>
  <div class="detail" v-else-if="recipe">
    <div class="card hero head-card" style="view-transition-name: recipe-hero">
      <span class="glow-orb head-glow" />
      <h2>{{ recipe.customName || recipe.dishName }}</h2>
      <p v-if="recipe.customName" class="meta">原名：{{ recipe.dishName }}</p>
      <p class="meta">版本 v{{ recipe.currentVersion }} ·
        约 {{ recipe.content.totalMinutes }} 分钟 · {{ recipe.content.servings }} 人食</p>
      <div class="btns head-btns">
        <button class="btn btn-secondary btn-small" @click="editMode ? (editMode = false) : startEdit()">
          {{ editMode ? '取消编辑' : '编辑菜谱' }}</button>
        <button class="btn btn-primary btn-small" @click="openCook">开始烹饪</button>
      </div>
    </div>

    <div class="card" v-if="editMode && editForm">
      <h3>编辑菜谱内容</h3>
      <div class="edit-grid">
        <label class="num-field">份数 <input class="input" type="number" v-model.number="editForm.servings" /></label>
        <label class="num-field">总分钟 <input class="input" type="number" v-model.number="editForm.totalMinutes" /></label>
      </div>
      <h4>食材
        <button type="button" class="add-btn" @click="addIngredientRow(editForm.ingredients)">
          <Icon name="plus" />加一行
        </button>
      </h4>
      <div class="edit-row" v-for="(r, idx) in editForm.ingredients" :key="'ig' + idx">
        <Field v-model="r.name" placeholder="名称" />
        <Field v-model="r.amount" placeholder="数量" />
        <IconButton name="trash" title="删除" @click="editForm.ingredients.splice(idx, 1)" />
      </div>
      <h4>调料
        <button type="button" class="add-btn" @click="addIngredientRow(editForm.seasonings)">
          <Icon name="plus" />加一行
        </button>
      </h4>
      <div class="edit-row" v-for="(r, idx) in editForm.seasonings" :key="'se' + idx">
        <Field v-model="r.name" placeholder="名称" />
        <Field v-model="r.amount" placeholder="数量" />
        <IconButton name="trash" title="删除" @click="editForm.seasonings.splice(idx, 1)" />
      </div>
      <h4>步骤
        <button type="button" class="add-btn" @click="addStepRow()">
          <Icon name="plus" />加一步
        </button>
      </h4>
      <div class="edit-row step-row" v-for="(s, idx) in editForm.steps" :key="'st' + idx">
        <span class="no">{{ stepNoText(idx + 1) }}</span>
        <Field v-model="s.text" placeholder="做法" />
        <input class="input dur" type="number" v-model.number="s.durationSec" placeholder="秒" />
        <IconButton name="trash" title="删除" @click="editForm.steps.splice(idx, 1)" />
      </div>
      <h4>小贴士</h4>
      <textarea class="input" v-model="editForm.tips" placeholder="可选" maxlength="255" />
      <div class="btns">
        <button class="btn btn-primary" @click="saveEdit">保存编辑</button>
        <button class="btn btn-ghost" @click="editMode = false">取消</button>
      </div>
    </div>

    <div class="card">
      <h3>食材</h3>
      <div class="row" v-for="i in recipe.content.ingredients" :key="i.name">
        <span>{{ i.name }}</span><span class="amount">{{ i.amount }}</span>
      </div>
      <h3>调料</h3>
      <div class="row" v-for="i in recipe.content.seasonings" :key="i.name">
        <span>{{ i.name }}</span><span class="amount">{{ i.amount }}</span>
      </div>
    </div>

    <div class="card">
      <h3>步骤</h3>
      <div class="step" v-for="s in recipe.content.steps" :key="s.no">
        <span class="no">{{ stepNoText(s.no) }}</span>
        <div class="step-body">
          <p class="step-text">{{ s.text }}</p>
          <p class="duration" v-if="s.durationSec">约
            {{ s.durationSec >= 60 ? Math.round(s.durationSec / 60) + ' 分钟' : s.durationSec + ' 秒' }}</p>
          <div class="photos" v-if="photosOf(s.no).length">
            <div class="photo-item" v-for="p in photosOf(s.no)" :key="p.id">
              <PhotoThumb :src="p.url" :width="72" @open="openLightbox(p.url)" />
              <IconButton name="trash" title="删除" @click="doDeletePhoto(p.id)" />
            </div>
          </div>
          <div class="photo-upload">
            <IconButton name="camera" title="拍照" @click="triggerFile(s.no)" />
            <input :ref="(el) => setFileRef(el, s.no)" type="file" accept="image/*" capture="environment" hidden
                   @change="onPhotoFile($event, s.no)" />
          </div>
        </div>
      </div>
      <p v-if="recipe.content.tips" class="meta">小贴士：{{ recipe.content.tips }}</p>
    </div>

    <div class="card">
      <h3>我的反馈</h3>
      <div class="stars">
        <button
          v-for="n in 5"
          :key="n"
          type="button"
          class="star-btn"
          :class="{ on: n <= myScore }"
          :ref="(el) => setStarRef(el, n)"
          :aria-label="'评 ' + n + ' 星'"
          @click="onStar(n)"
        >
          <svg viewBox="0 0 24 24" width="28" height="28" :fill="n <= myScore ? 'currentColor' : 'none'"
               stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 2.5l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.3l-5.8 3.1 1.1-6.5L2.6 9.3l6.5-.9L12 2.5z" />
          </svg>
        </button>
      </div>
      <textarea class="input" v-model="myComment" placeholder="口感如何？（如：偏淡了）" maxlength="512" />
      <div class="btns">
        <button class="btn btn-primary" @click="doFeedback">提交反馈</button>
      </div>
      <p v-if="atLimit" class="meta">已达版本上限，请先回滚旧版本</p>
    </div>

    <div class="card">
      <h3>历史版本（{{ versions.length }}）</h3>
      <div class="version" v-for="v in versions" :key="v.version">
        <span>v{{ v.version }} · {{ v.sourceText }} · {{ v.createdAtText }}
          <template v-if="v.changeNote">（{{ v.changeNote }}）</template></span>
        <IconButton
          v-if="v.version !== recipe.currentVersion"
          name="rotate"
          title="回滚到此版"
          @click="doRollback(v.version)"
        />
      </div>
    </div>

    <div class="card">
      <h3>个性化命名</h3>
      <Field v-model="nameInput" placeholder="如：我妈的红烧肉" />
      <div class="btns name-btns">
        <button class="btn btn-primary" @click="saveName">保存命名</button>
      </div>
    </div>

    <div class="cta-bar">
      <button class="btn btn-primary cta" :disabled="atLimit" @click="openGenerate">
        <Icon name="sparkle" />按反馈优化菜谱
      </button>
    </div>

    <Lightbox :src="lightboxSrc" :open="lightboxOpen" @update:open="lightboxOpen = $event" />
  </div>
</template>

<style scoped>
.detail { max-width: 720px; margin: 0 auto; padding: var(--space-4); padding-bottom: calc(var(--space-7) * 2); display: flex; flex-direction: column; gap: var(--gap); }
.head-glow { width: 170px; height: 170px; top: -40px; right: -40px; }
.detail h2 { margin: 0 0 var(--space-2); font-size: var(--text-2xl); letter-spacing: -0.01em; }
.detail h3 { margin: 0 0 var(--space-2); }
.meta { color: var(--text-secondary); font-size: var(--text-xs); }
.row { display: flex; justify-content: space-between; padding: var(--space-1) 0; }
.amount { color: var(--text-secondary); }
.step { display: flex; gap: var(--space-3); margin: var(--space-3) 0; padding: var(--space-3);
  border: 1px solid var(--glass-border); border-radius: var(--radius-sm); background: var(--glass-bg); }
.step:last-of-type { margin-bottom: 0; }
.step-body { flex: 1; min-width: 0; }
.step-text { margin: 0; font-size: var(--text-md); line-height: 1.6; }
.no { width: 28px; flex-shrink: 0; font-size: var(--text-xs); font-weight: 800; color: var(--primary);
  letter-spacing: 0.06em; line-height: 1.6; }
.duration { color: var(--text-secondary); font-size: var(--text-xs); margin: var(--space-1) 0 0; }
.stars { display: flex; gap: var(--space-1); margin-bottom: var(--space-2); }
.star-btn {
  padding: var(--space-1); border: none; background: transparent; cursor: pointer;
  color: var(--text-tertiary); line-height: 0; border-radius: var(--radius-sm);
  transition: color 0.2s ease;
}
.star-btn.on { color: var(--warning); }
textarea.input { width: 100%; min-height: 80px; margin: var(--space-3) 0; box-sizing: border-box; }
.btns { display: flex; gap: var(--space-3); }
.head-btns { margin-top: var(--space-3); }
.name-btns { margin-top: var(--space-3); }
.version { display: flex; justify-content: space-between; align-items: center; gap: var(--space-2);
  font-size: var(--text-xs); color: var(--text-secondary); padding: var(--space-1) 0; }
.edit-grid { display: flex; gap: var(--space-4); margin-bottom: var(--space-2); }
.edit-grid .num-field { flex: 1; font-size: var(--text-xs); color: var(--text-secondary); }
.edit-grid .num-field .input { margin-top: var(--space-1); }
.edit-row { display: flex; gap: var(--space-2); align-items: flex-end; margin: var(--space-2) 0; }
.edit-row .field { flex: 1; min-width: 0; margin-bottom: 0; }
.edit-row .dur { flex: 0 0 70px; }
.edit-row .no { width: 24px; text-align: center; color: var(--primary); flex-shrink: 0; line-height: 40px; }
.add-btn {
  display: inline-flex; align-items: center; gap: var(--space-1);
  border: none; background: transparent; color: var(--primary);
  font-size: var(--text-xs); font-weight: 700; cursor: pointer; padding: 0;
}
h4 { margin: var(--space-3) 0 var(--space-1); display: flex; align-items: center; gap: var(--space-2); }
.photos { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-2); align-items: center; }
.photo-item { display: flex; align-items: center; gap: var(--space-1); }
.photo-upload { margin-top: var(--space-2); }
.cta-bar { position: sticky; bottom: var(--space-4); display: flex; justify-content: center; margin-top: var(--space-4); z-index: 5; }
.cta { padding: var(--space-3) var(--space-6); box-shadow: var(--shadow-glow); }
</style>
