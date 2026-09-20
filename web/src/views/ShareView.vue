<template>
  <div class="share-page">
    <div v-if="sheet" class="card sheet-card">
      <div class="row section-head">
        <h3>{{ sheet.title }}</h3>
        <span class="badge-pill" :class="'is-' + String(sheet.status || '').toLowerCase()">
          {{ sheetStatusText[sheet.status] || sheet.status }}
        </span>
      </div>
      <div class="meta">已认领 {{ claimed }}/{{ items.length }}</div>
    </div>

    <div v-if="items.length" class="card list-card">
      <div v-for="it in items" :key="it.id" class="list-item">
        <div class="row">
          <strong>{{ it.dishName }}</strong>
          <span class="badge-pill" :class="'is-' + String(it.itemStatus || '').toLowerCase()">
            {{ itemStatusText[it.itemStatus] || it.itemStatus }}
          </span>
        </div>
        <div v-if="it.note" class="meta">备注：{{ it.note }}</div>
        <div class="meta">认领人：{{ it.claimantNickname || '暂无' }}</div>
      </div>
    </div>

    <div v-if="invalid" class="empty">
      <p class="empty-title">清单不存在或已失效</p>
      <p class="empty-sub">链接可能已过期，请联系分享人重新获取</p>
    </div>

    <div class="share-footer-hint">微信内搜索小程序 Link-Life 可认领菜品</div>
  </div>
</template>

<script>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { publicRequest } from '../api/request'
import { sheetStatusText, claimedCount } from '../utils/sheet'

const itemStatusText = {
  OPEN: '待认领',
  CLAIMED: '已认领',
  COOKING: '烹饪中',
  DONE: '已完成',
}

export default {
  setup() {
    const route = useRoute()
    const sheet = ref(null)
    const items = ref([])
    const invalid = ref(false)

    async function load() {
      try {
        const data = await publicRequest('/api/share/' + route.params.token)
        sheet.value = data
        items.value = data.items || []
      } catch (err) {
        invalid.value = true
      }
    }

    load()

    const claimed = computed(() => claimedCount(items.value))

    return {
      sheet,
      items,
      invalid,
      claimed,
      sheetStatusText,
      itemStatusText,
    }
  },
}
</script>

<style scoped>
.share-page {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
  padding-bottom: 70px;
}
.sheet-card {
  border-radius: var(--radius-lg);
}
.section-head {
  justify-content: space-between;
  margin-bottom: 8px;
}
.section-head h3 {
  margin: 0;
}
.badge-pill.is-shared {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge-pill.is-in_progress {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge-pill.is-completed {
  background: var(--accent-weak);
  color: var(--accent);
}
.meta {
  color: var(--text-secondary);
  font-size: 14px;
}
.list-item {
  padding: var(--gap) 0;
}
.list-item:first-child {
  padding-top: 0;
}
.list-item:last-child {
  padding-bottom: 0;
}
.list-item + .list-item {
  border-top: 1px solid var(--border);
}
.list-item .row {
  margin-bottom: 4px;
}
.empty-title,
.empty-sub {
  margin: 0;
}
.empty-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}
.share-footer-hint {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  text-align: center;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  border-top: 1px solid var(--border);
}
</style>
