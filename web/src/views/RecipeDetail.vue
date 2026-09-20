<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRecipe, getVersions, submitFeedback, editRecipe, rollback, listPhotos, deletePhoto, uploadPhoto } from '../api/recipe'
import { showToast } from '../utils/toast'

const route = useRoute()
const id = route.params.id
const recipe = ref(null)
const versions = ref([])
const myScore = ref(0)
const myComment = ref('')
const nameInput = ref('')
const atLimit = computed(() => versions.value.length >= 5)

const SOURCE_TEXT = { AI_GENERATE: 'AI 生成', AI_ITERATE: 'AI 迭代', MANUAL_EDIT: '手动编辑', PHOTO_ANALYSIS: '拍照修正' }

const loadFailed = ref(false)

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
</script>

<template>
  <div class="detail" v-if="loadFailed && !recipe">
    <div class="card">
      <p class="meta">加载失败，请稍后再试</p>
      <button class="btn btn-secondary btn-small" @click="load">重试</button>
    </div>
  </div>
  <div class="detail" v-else-if="recipe">
    <div class="card head-card">
      <h2>{{ recipe.customName || recipe.dishName }}</h2>
      <p v-if="recipe.customName" class="meta">原名：{{ recipe.dishName }}</p>
      <p class="meta">版本 v{{ recipe.currentVersion }} ·
        约 {{ recipe.content.totalMinutes }} 分钟 · {{ recipe.content.servings }} 人食</p>
      <div class="btns head-btns">
        <button class="btn btn-secondary btn-small" @click="editMode ? (editMode = false) : startEdit()">
          {{ editMode ? '取消编辑' : '编辑菜谱' }}</button>
        <button class="btn btn-primary btn-small" @click="$router.push(`/recipes/${id}/cook`)">开始烹饪</button>
      </div>
    </div>

    <div class="card" v-if="editMode && editForm">
      <h3>编辑菜谱内容</h3>
      <div class="edit-grid">
        <label>份数 <input class="input" type="number" v-model.number="editForm.servings" /></label>
        <label>总分钟 <input class="input" type="number" v-model.number="editForm.totalMinutes" /></label>
      </div>
      <h4>食材 <a class="add" href="#" @click.prevent="addIngredientRow(editForm.ingredients)">+ 加一行</a></h4>
      <div class="edit-row" v-for="(r, idx) in editForm.ingredients" :key="'ig' + idx">
        <input class="input" v-model="r.name" placeholder="名称" />
        <input class="input" v-model="r.amount" placeholder="数量" />
        <a class="del" href="#" @click.prevent="editForm.ingredients.splice(idx, 1)">删除</a>
      </div>
      <h4>调料 <a class="add" href="#" @click.prevent="addIngredientRow(editForm.seasonings)">+ 加一行</a></h4>
      <div class="edit-row" v-for="(r, idx) in editForm.seasonings" :key="'se' + idx">
        <input class="input" v-model="r.name" placeholder="名称" />
        <input class="input" v-model="r.amount" placeholder="数量" />
        <a class="del" href="#" @click.prevent="editForm.seasonings.splice(idx, 1)">删除</a>
      </div>
      <h4>步骤 <a class="add" href="#" @click.prevent="addStepRow()">+ 加一步</a></h4>
      <div class="edit-row step-row" v-for="(s, idx) in editForm.steps" :key="'st' + idx">
        <span class="no">{{ idx + 1 }}</span>
        <input class="input" v-model="s.text" placeholder="做法" />
        <input class="input dur" type="number" v-model.number="s.durationSec" placeholder="秒" />
        <a class="del" href="#" @click.prevent="editForm.steps.splice(idx, 1)">删除</a>
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
        <span class="no">{{ s.no }}</span>
        <div>
          <p>{{ s.text }}</p>
          <p class="duration" v-if="s.durationSec">约
            {{ s.durationSec >= 60 ? Math.round(s.durationSec / 60) + ' 分钟' : s.durationSec + ' 秒' }}</p>
        </div>
        <div class="photos" v-if="photosOf(s.no).length">
          <div class="photo-item" v-for="p in photosOf(s.no)" :key="p.id">
            <img :src="p.url" />
            <a href="#" @click.prevent="doDeletePhoto(p.id)">删</a>
          </div>
        </div>
        <label class="photo-upload">
          + 拍照
          <input type="file" accept="image/*" capture="environment" hidden
                 @change="onPhotoFile($event, s.no)" />
        </label>
      </div>
      <p v-if="recipe.content.tips" class="meta">小贴士：{{ recipe.content.tips }}</p>
    </div>

    <div class="card">
      <h3>我的反馈</h3>
      <div class="stars">
        <span v-for="n in 5" :key="n" :class="['star', { on: n <= myScore }]"
              @click="myScore = n">★</span>
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
        <a v-if="v.version !== recipe.currentVersion"
           @click.prevent="doRollback(v.version)" href="#">回滚到此版</a>
      </div>
    </div>

    <div class="card">
      <h3>个性化命名</h3>
      <input class="input" v-model="nameInput" placeholder="如：我妈的红烧肉" maxlength="64" />
      <div class="btns name-btns">
        <button class="btn btn-primary" @click="saveName">保存命名</button>
      </div>
    </div>

    <div class="cta-bar">
      <button class="btn btn-primary cta" :disabled="atLimit"
              @click="$router.push({ path: '/recipes/generate', query: { recipeId: id } })">
        按反馈优化菜谱</button>
    </div>
  </div>
</template>

<style scoped>
.detail { max-width: 720px; margin: 0 auto; padding: 16px; padding-bottom: 96px; }
.head-card { border-radius: var(--radius-lg); }
.detail h2 { margin: 0 0 8px; font-size: 24px; }
.meta { color: var(--text-tertiary); font-size: 13px; }
.row { display: flex; justify-content: space-between; padding: 4px 0; }
.amount { color: var(--text-secondary); }
.step { display: flex; gap: 12px; margin: 12px 0; padding: 12px;
  border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--bg-card); }
.step:last-of-type { margin-bottom: 0; }
.no { width: 24px; height: 24px; border-radius: 50%; background: var(--primary-weak); color: var(--primary-deep);
  text-align: center; line-height: 24px; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.duration { color: var(--text-tertiary); font-size: 12px; }
.stars { font-size: 28px; color: var(--text-tertiary); cursor: pointer; }
.star.on { color: var(--warning); }
textarea.input { width: 100%; min-height: 80px; margin: 12px 0; box-sizing: border-box; }
.btns { display: flex; gap: 12px; }
.head-btns { margin-top: 12px; }
.name-btns { margin-top: 12px; }
.version { display: flex; justify-content: space-between; font-size: 13px;
  color: var(--text-secondary); padding: 4px 0; }
.version a { color: var(--primary); cursor: pointer; }
.edit-grid { display: flex; gap: 16px; margin-bottom: 8px; }
.edit-grid label { font-size: 13px; color: var(--text-secondary); }
.edit-grid input { width: 80px; }
.edit-row { display: flex; gap: 8px; align-items: center; margin: 6px 0; }
.edit-row .input { flex: 1; min-width: 0; width: auto; }
.edit-row .dur { flex: 0 0 70px; }
.edit-row .no { width: 22px; text-align: center; color: var(--primary-deep); flex-shrink: 0; }
.edit-row .del, h4 .add { color: var(--primary); font-size: 12px; flex-shrink: 0; }
h4 { margin: 12px 0 4px; }
.photos { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.photo-item img { width: 80px; height: 80px; object-fit: cover; border-radius: var(--radius-sm); display: block; }
.photo-item a { color: var(--danger); font-size: 12px; }
.photo-upload { color: var(--primary); font-size: 12px; cursor: pointer; align-self: center; }
.cta-bar { position: sticky; bottom: 16px; display: flex; justify-content: center; margin-top: 16px; }
.cta { padding: 14px 32px; box-shadow: var(--shadow-card); }
</style>
