<template>
  <div>
    <div v-if="sheet" class="card sheet-head">
      <div class="row section-head">
        <h3>{{ sheet.title }}</h3>
        <span class="badge-pill" :class="'is-' + String(sheet.status || '').toLowerCase()">
          {{ sheetStatusText[sheet.status] || sheet.status }}
        </span>
      </div>
      <div class="muted">
        已认领 {{ claimedCount }}/{{ items.length }}
      </div>
      <button v-if="showComplete" class="btn btn-warn complete-btn" @click="completeSheet">
        收单
      </button>
    </div>

    <div v-for="it in items" :key="it.id" class="card">
      <div class="row">
        <strong>{{ it.dishName }}</strong>
        <a class="recipe-link" @click.prevent="openRecipe(it.dishName)" href="#">菜谱</a>
        <span
          class="badge-pill"
          :class="['is-' + String(it.itemStatus || '').toLowerCase(), { 'is-mine': it.mine }]"
        >
          {{ it.statusText }}
        </span>
      </div>
      <div v-if="it.note" class="muted">备注：{{ it.note }}</div>
      <div class="muted">认领人：{{ it.claimantNickname || '暂无' }}</div>
      <div class="row actions">
        <button v-if="it.canClaim" class="btn btn-primary btn-small" @click="claimItem(it)">认领</button>
        <button v-if="it.canCook" class="btn btn-secondary btn-small" @click="startCook(it)">开始烹饪</button>
        <button v-if="it.canFinish" class="btn btn-primary btn-accent btn-small" @click="finishItem(it)">完成</button>
        <button v-if="it.canRelease" class="btn btn-ghost btn-small" @click="releaseItem(it)">
          释放
        </button>
      </div>
    </div>

    <p v-if="!sheet && !loading" class="muted">清单不存在或加载失败</p>
  </div>
</template>

<script>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../api/request'
import { getByDish } from '../api/recipe'
import { showToast } from '../utils/toast'
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
    const router = useRouter()
    const sheet = ref(null)
    const items = ref([])
    const userId = ref(null)
    const showComplete = ref(false)
    const loading = ref(true)
    const acting = ref(false)

    function applySheet(data, currentUserId) {
      const actionsEnabled = data.status !== 'COMPLETED'
      items.value = (data.items || []).map((it) => ({
        ...it,
        statusText: itemStatusText[it.itemStatus] || it.itemStatus,
        mine: currentUserId && it.claimantId === currentUserId,
        canClaim: actionsEnabled && it.itemStatus === 'OPEN',
        canCook: actionsEnabled && it.claimantId === currentUserId && it.itemStatus === 'CLAIMED',
        canFinish: actionsEnabled && it.claimantId === currentUserId && it.itemStatus === 'COOKING',
        canRelease:
          actionsEnabled &&
          it.claimantId === currentUserId &&
          (it.itemStatus === 'CLAIMED' || it.itemStatus === 'COOKING'),
      }))
      showComplete.value = actionsEnabled && currentUserId && data.creatorId === currentUserId
      sheet.value = data
    }

    async function reload() {
      try {
        const user = await request('/api/me')
        userId.value = user.id
        const data = await request('/api/order/sheets/' + route.params.id)
        applySheet(data, user.id)
      } catch (err) {
        showToast((err && err.message) || '加载清单失败')
      } finally {
        loading.value = false
      }
    }

    async function act(path, successText, data) {
      if (acting.value) return
      acting.value = true
      try {
        await request(path, { method: 'POST', data })
        showToast(successText)
        await reload()
      } catch (err) {
        showToast((err && err.message) || '操作失败')
      } finally {
        acting.value = false
      }
    }

    function claimItem(it) {
      act('/api/order/items/' + it.id + '/claim', '认领成功')
    }

    function startCook(it) {
      act('/api/order/items/' + it.id + '/status', '开始烹饪', { itemStatus: 'COOKING' })
    }

    function finishItem(it) {
      act('/api/order/items/' + it.id + '/status', '已完成', { itemStatus: 'DONE' })
    }

    function releaseItem(it) {
      act('/api/order/items/' + it.id + '/release', '已释放')
    }

    function completeSheet() {
      if (!window.confirm('收单后所有人不能再操作菜品，确定收单吗？')) return
      act('/api/order/sheets/' + route.params.id + '/complete', '已收单')
    }

    async function openRecipe(dishName) {
      try {
        const recipe = await getByDish(sheet.value.circleId, dishName)
        router.push('/recipes/' + recipe.id)
      } catch (err) {
        if (err && err.code === 5001) {
          router.push({ path: '/recipes/generate',
            query: { circleId: sheet.value.circleId, dishName } })
        } else {
          showToast((err && err.message) || '查询菜谱失败')
        }
      }
    }

    reload()

    const claimed = computed(() => claimedCount(items.value))

    return {
      sheet,
      items,
      showComplete,
      loading,
      sheetStatusText,
      claimedCount: claimed,
      claimItem,
      startCook,
      finishItem,
      releaseItem,
      completeSheet,
      openRecipe,
    }
  },
}
</script>

<style scoped>
.sheet-head {
  border-radius: var(--radius-lg);
  margin-bottom: var(--gap);
}
.section-head {
  justify-content: space-between;
}
.section-head h3 {
  margin: 0;
  font-size: 24px;
}
.complete-btn {
  margin-top: 10px;
}
.badge-pill.is-shared,
.badge-pill.is-in_progress {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge-pill.is-completed {
  background: var(--accent-weak);
  color: var(--accent);
}
.badge-pill.is-mine {
  box-shadow: 0 0 0 2px var(--primary-weak);
}
.actions {
  margin-top: 8px;
}
.recipe-link {
  color: var(--primary);
  font-size: 13px;
}
.btn-accent {
  background: var(--accent);
  color: #fff;
}
</style>
