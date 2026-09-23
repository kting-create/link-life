<template>
  <div class="share-page">
    <Skeleton v-if="loading && !sheet && !invalid" :rows="3" />

    <template v-else>
      <div v-if="sheet" class="card sheet-card">
        <span class="glow-orb share-glow" />
        <div class="row section-head">
          <h3>{{ sheet.title }}</h3>
          <span class="badge-pill" :class="sheetBadgeClass(sheet.status)">
            {{ sheetStatusText[sheet.status] || sheet.status }}
          </span>
        </div>
        <div class="meta">已认领 {{ claimed }}/{{ items.length }}</div>
      </div>

      <div v-if="items.length" class="card list-card">
        <div class="stagger-list" :class="{ run: listRun }">
          <div v-for="(it, index) in items" :key="it.id" class="list-item stagger-item" :style="{ '--i': index }">
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
      </div>

      <div v-if="invalid">
        <EmptyState title="清单不存在或已失效" desc="链接可能已过期，请联系分享人重新获取" />
      </div>
    </template>

    <div class="glass-card share-footer-hint">微信内搜索小程序 Link-Life 可认领菜品</div>
  </div>
</template>

<script>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { publicRequest } from '../api/request'
import { sheetStatusText, claimedCount, sheetBadgeClass } from '../utils/sheet'
import EmptyState from '../components/EmptyState.vue'
import Skeleton from '../components/Skeleton.vue'

const itemStatusText = {
  OPEN: '待认领',
  CLAIMED: '已认领',
  COOKING: '烹饪中',
  DONE: '已完成',
}

export default {
  components: { EmptyState, Skeleton },
  setup() {
    const route = useRoute()
    const sheet = ref(null)
    const items = ref([])
    const invalid = ref(false)
    const loading = ref(false)
    const listRun = ref(false)

    async function load() {
      loading.value = true
      try {
        const data = await publicRequest('/api/share/' + route.params.token)
        sheet.value = data
        items.value = data.items || []
        listRun.value = true
      } catch (err) {
        invalid.value = true
      } finally {
        loading.value = false
      }
    }

    load()

    const claimed = computed(() => claimedCount(items.value))

    return {
      sheet,
      items,
      invalid,
      loading,
      listRun,
      claimed,
      sheetStatusText,
      itemStatusText,
      sheetBadgeClass,
    }
  },
}
</script>

<style scoped>
.share-page {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
  padding-bottom: calc(var(--space-7) + var(--space-5));
}
.sheet-card {
  position: relative;
  border-radius: var(--radius-lg);
}
.share-glow {
  width: 150px;
  height: 150px;
  top: -32px;
  right: -32px;
}
.section-head {
  position: relative;
  z-index: 1;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}
.section-head h3 {
  margin: 0;
}
.sheet-card .meta {
  position: relative;
  z-index: 1;
}
.meta {
  color: var(--text-secondary);
  font-size: var(--text-sm);
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
  justify-content: space-between;
  margin-bottom: var(--space-1);
}
.share-footer-hint {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: var(--space-3) var(--space-4);
  padding-bottom: calc(var(--space-3) + env(safe-area-inset-bottom));
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--text-xs);
  border-radius: 0;
  border-left: none;
  border-right: none;
  border-bottom: none;
}
</style>
