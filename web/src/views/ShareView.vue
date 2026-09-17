<template>
  <div class="share-page">
    <div v-if="sheet" class="card">
      <div class="row section-head">
        <h3>{{ sheet.title }}</h3>
        <span class="tag">{{ sheetStatusText[sheet.status] || sheet.status }}</span>
      </div>
      <div class="muted">已认领 {{ claimed }}/{{ items.length }}</div>
    </div>

    <div v-for="it in items" :key="it.id" class="card">
      <div class="row">
        <strong>{{ it.dishName }}</strong>
        <span class="tag">{{ itemStatusText[it.itemStatus] || it.itemStatus }}</span>
      </div>
      <div v-if="it.note" class="muted">备注：{{ it.note }}</div>
      <div class="muted">认领人：{{ it.claimantNickname || '暂无' }}</div>
    </div>

    <p v-if="invalid" class="muted invalid-tip">清单不存在或已失效</p>

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
  padding-bottom: 70px;
}
.section-head {
  justify-content: space-between;
}
.section-head h3 {
  margin: 0;
}
.invalid-tip {
  text-align: center;
  margin-top: 40px;
}
.share-footer-hint {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px;
  text-align: center;
  background: #fff;
  color: #888;
  font-size: 13px;
  border-top: 1px solid #eee;
}
</style>
