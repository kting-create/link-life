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
    <div class="card add-card">
      <select v-model="type" class="input type-select">
        <option value="SEASONING">调料</option>
        <option value="INGREDIENT">食材</option>
      </select>
      <input v-model="name" class="input" placeholder="如：生抽 / 五花肉" @keyup.enter="add" />
      <button class="btn btn-primary" @click="add">添加</button>
    </div>
    <div class="item-grid">
      <div class="card item" v-for="i in items" :key="i.id">
        <div class="item-main">
          <span class="badge-pill" :class="i.type === 'SEASONING' ? 'is-seasoning' : 'is-ingredient'">
            {{ i.type === 'SEASONING' ? '调料' : '食材' }}
          </span>
          <span class="name">{{ i.name }}</span>
        </div>
        <a @click.prevent="del(i.id)" href="#" class="delete">删除</a>
      </div>
    </div>
    <p v-if="!items.length" class="empty">还没有条目，添加后 AI 会优先使用它们调味</p>
  </div>
</template>

<style scoped>
.pantry {
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}
.add-card {
  display: flex;
  align-items: center;
  gap: 8px;
}
.type-select {
  width: 96px;
  flex-shrink: 0;
}
.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--gap);
}
.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.name {
  font-size: 14px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge-pill.is-seasoning {
  background: var(--primary-weak);
  color: var(--primary-deep);
}
.badge-pill.is-ingredient {
  background: #eff6ff;
  color: #1d4ed8;
}
/* 临期/过期徽标：后端 pantry_item 暂无过期字段，预留语义类 */
.badge-pill.is-expiring {
  background: #fef3c7;
  color: #b45309;
}
.badge-pill.is-expired {
  background: #fee2e2;
  color: var(--danger);
}
.delete {
  color: var(--danger);
  font-size: 13px;
  flex-shrink: 0;
}
</style>
