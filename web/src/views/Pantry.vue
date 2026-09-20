<script setup>
import { onMounted, ref } from 'vue'
import { addPantry, deletePantry, listPantry } from '../api/recipe'
import { showToast } from '../utils/toast'

const items = ref([])
const type = ref('SEASONING')
const name = ref('')

async function load() {
  items.value = await listPantry()
}
onMounted(load)

async function add() {
  if (!name.value.trim()) { showToast('请输入名称'); return }
  try {
    await addPantry({ type: type.value, name: name.value.trim() })
    name.value = ''
    await load()
  } catch (e) { showToast(e.message || '添加失败') }
}

async function del(id) {
  try {
    await deletePantry(id)
    await load()
  } catch (e) { showToast(e.message || '删除失败') }
}
</script>

<template>
  <div class="pantry">
    <div class="add">
      <select v-model="type">
        <option value="SEASONING">调料</option>
        <option value="INGREDIENT">食材</option>
      </select>
      <input v-model="name" placeholder="如：生抽 / 五花肉" @keyup.enter="add" />
      <button @click="add">添加</button>
    </div>
    <div class="item" v-for="i in items" :key="i.id">
      <span><b class="tag">{{ i.type === 'SEASONING' ? '调料' : '食材' }}</b> {{ i.name }}</span>
      <a @click.prevent="del(i.id)" href="#">删除</a>
    </div>
    <p v-if="!items.length" class="empty">还没有条目，添加后 AI 会优先使用它们调味</p>
  </div>
</template>

<style scoped>
.pantry { max-width: 640px; margin: 0 auto; padding: 16px; }
.add { display: flex; gap: 8px; margin-bottom: 16px; }
.add input { flex: 1; }
.item { background: #fff; border-radius: 8px; padding: 12px 16px; margin-bottom: 8px;
  display: flex; justify-content: space-between; }
.item a { color: var(--danger); cursor: pointer; }
.tag { font-weight: 400; font-size: 12px; color: #1989fa; margin-right: 8px; }
.empty { color: #999; text-align: center; padding: 48px 0; }
</style>
