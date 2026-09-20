<template>
  <div class="circles-page">
    <div class="card">
      <div class="row section-head">
        <h3>我的圈子</h3>
        <div class="row">
          <button class="btn btn-secondary btn-small" @click="showCreate = !showCreate">建圈</button>
          <button class="btn btn-secondary btn-small" @click="showJoin = !showJoin">加入</button>
        </div>
      </div>
      <div v-if="showCreate" class="inline-form">
        <input v-model="newName" class="input" placeholder="圈子名称" />
        <button class="btn btn-primary btn-small" :disabled="acting" @click="createCircle">创建</button>
      </div>
      <div v-if="showJoin" class="inline-form">
        <input v-model="inviteCode" class="input" placeholder="邀请码" />
        <button class="btn btn-primary btn-small" :disabled="acting" @click="joinCircle">加入</button>
      </div>
      <p v-if="!circles.length && !loading" class="empty">还没有圈子，创建或加入一个吧</p>
      <div
        v-for="c in circles"
        :key="c.id"
        class="card clickable circle-card"
        :class="{ active: selectedId === c.id }"
        @click="selectCircle(c.id)"
      >
        <div class="row circle-row">
          <span class="avatar">{{ c.name.charAt(0) }}</span>
          <strong>{{ c.name }}</strong>
          <span class="meta">邀请码：{{ c.inviteCode }}</span>
        </div>
      </div>
    </div>

    <div v-if="selectedId" class="card">
      <div class="row section-head">
        <h3>成员</h3>
        <button class="btn btn-secondary btn-small" @click="loadMembers">刷新</button>
      </div>
      <div v-for="m in members" :key="m.userId" class="member-row">
        <span>{{ m.nickname }}</span>
        <span class="badge-pill" :class="m.role === 'OWNER' ? 'role-owner' : 'role-member'">
          {{ m.role === 'OWNER' ? '圈主' : '成员' }}
        </span>
      </div>
      <p v-if="!members.length" class="empty">暂无成员</p>
    </div>

    <div v-if="selectedId" class="card">
      <div class="row section-head">
        <h3>点单清单</h3>
        <button class="btn btn-secondary btn-small" @click="showCreateSheet = !showCreateSheet">发起点单</button>
      </div>

      <div v-if="showCreateSheet" class="sheet-form">
        <input v-model="sheetTitle" class="input" placeholder="点单标题" />
        <div v-for="(item, idx) in sheetItems" :key="idx" class="row item-row">
          <input v-model="item.dishName" class="input" placeholder="菜名" />
          <input v-model="item.note" class="input" placeholder="备注" />
          <button class="btn btn-secondary btn-small" @click="removeRow(idx)">删除</button>
        </div>
        <div class="row">
          <button class="btn btn-secondary btn-small" @click="addRow">加一道菜</button>
          <button class="btn btn-primary btn-small" :disabled="creating" @click="submitSheet">创建清单</button>
        </div>
      </div>

      <p v-if="!sheets.length && !loadingSheets" class="empty">该圈子暂无清单</p>
      <div
        v-for="s in sheets"
        :key="s.id"
        class="card clickable sheet-card"
        @click="$router.push('/sheets/' + s.id)"
      >
        <div class="row">
          <strong>{{ s.title }}</strong>
          <span class="badge-pill" :class="'is-' + String(s.status || '').toLowerCase()">
            {{ sheetStatusText[s.status] || s.status }}
          </span>
        </div>
        <div class="meta">
          {{ (s.items || []).filter((it) => it.claimantId).length }}/{{ (s.items || []).length }} 已认领
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api/request'
import { showToast } from '../utils/toast'
import { sheetStatusText } from '../utils/sheet'

export default {
  setup() {
    const router = useRouter()
    const circles = ref([])
    const members = ref([])
    const sheets = ref([])
    const selectedId = ref(null)
    const loading = ref(false)
    const loadingSheets = ref(false)
    const acting = ref(false)
    const showCreate = ref(false)
    const showJoin = ref(false)
    const showCreateSheet = ref(false)
    const newName = ref('')
    const inviteCode = ref('')
    const sheetTitle = ref('')
    const sheetItems = ref([{ dishName: '', note: '' }])
    const creating = ref(false)

    async function loadCircles() {
      loading.value = true
      try {
        circles.value = (await request('/api/circles')) || []
      } catch (err) {
        showToast((err && err.message) || '加载圈子失败')
      } finally {
        loading.value = false
      }
    }

    async function loadMembers() {
      if (!selectedId.value) return
      try {
        members.value = (await request('/api/circles/' + selectedId.value + '/members')) || []
      } catch (err) {
        showToast((err && err.message) || '加载成员失败')
      }
    }

    async function loadSheets() {
      if (!selectedId.value) return
      loadingSheets.value = true
      try {
        sheets.value =
          (await request('/api/order/sheets?circleId=' + selectedId.value)) || []
      } catch (err) {
        showToast((err && err.message) || '加载清单失败')
      } finally {
        loadingSheets.value = false
      }
    }

    function selectCircle(id) {
      selectedId.value = id
      loadMembers()
      loadSheets()
    }

    async function createCircle() {
      const name = newName.value.trim()
      if (!name) {
        showToast('圈子名称不能为空')
        return
      }
      acting.value = true
      try {
        const circle = await request('/api/circles', { method: 'POST', data: { name } })
        showToast('创建成功')
        newName.value = ''
        showCreate.value = false
        circles.value = circles.value.concat([circle])
        selectCircle(circle.id)
      } catch (err) {
        showToast((err && err.message) || '创建失败')
      } finally {
        acting.value = false
      }
    }

    async function joinCircle() {
      const code = inviteCode.value.trim()
      if (!code) {
        showToast('邀请码不能为空')
        return
      }
      acting.value = true
      try {
        const circle = await request('/api/circles/join', {
          method: 'POST',
          data: { inviteCode: code },
        })
        showToast('加入成功')
        inviteCode.value = ''
        showJoin.value = false
        if (!circles.value.some((c) => c.id === circle.id)) {
          circles.value = circles.value.concat([circle])
        }
        selectCircle(circle.id)
      } catch (err) {
        showToast((err && err.message) || '加入失败')
      } finally {
        acting.value = false
      }
    }

    function addRow() {
      sheetItems.value = sheetItems.value.concat([{ dishName: '', note: '' }])
    }

    function removeRow(idx) {
      if (sheetItems.value.length <= 1) {
        showToast('至少保留一道菜')
        return
      }
      sheetItems.value = sheetItems.value.filter((_, i) => i !== idx)
    }

    async function submitSheet() {
      if (creating.value) return
      const title = sheetTitle.value.trim()
      if (!title) {
        showToast('请填写点单标题')
        return
      }
      const items = sheetItems.value
        .map((it) => ({ dishName: (it.dishName || '').trim(), note: (it.note || '').trim() }))
        .filter((it) => it.dishName)
      if (!items.length) {
        showToast('请至少填写一道菜名')
        return
      }
      creating.value = true
      try {
        const sheet = await request('/api/order/sheets', {
          method: 'POST',
          data: { circleId: selectedId.value, title, items },
        })
        showToast('创建成功')
        router.push('/sheets/' + sheet.id)
      } catch (err) {
        showToast((err && err.message) || '创建失败')
      } finally {
        creating.value = false
      }
    }

    loadCircles()

    return {
      circles,
      members,
      sheets,
      selectedId,
      loading,
      loadingSheets,
      acting,
      showCreate,
      showJoin,
      showCreateSheet,
      newName,
      inviteCode,
      sheetTitle,
      sheetItems,
      creating,
      sheetStatusText,
      selectCircle,
      loadMembers,
      createCircle,
      joinCircle,
      addRow,
      removeRow,
      submitSheet,
    }
  },
}
</script>

<style scoped>
.circles-page {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-head {
  justify-content: space-between;
  margin-bottom: 8px;
}
.section-head h3 {
  margin: 0;
}
.inline-form {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.circle-card {
  margin-top: 8px;
}
.circle-card.active {
  border-color: var(--primary);
}
.clickable {
  cursor: pointer;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary-weak);
  color: var(--primary-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}
.circle-row strong {
  flex-shrink: 0;
}
.meta {
  margin-left: auto;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: right;
}
.member-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--border);
}
.member-row:last-child {
  border-bottom: none;
}
.role-owner {
  background: var(--primary-weak);
  color: var(--primary-deep);
}
.role-member {
  background: #f1f5f9;
  color: var(--text-secondary);
}
.sheet-form {
  margin-bottom: 12px;
}
.item-row {
  margin-top: 8px;
}
.sheet-form > .row:last-child {
  margin-top: 12px;
}
.sheet-card {
  margin-top: 8px;
}
.sheet-card .row {
  justify-content: space-between;
}
.badge-pill.is-shared,
.badge-pill.is-in_progress {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge-pill.is-completed {
  background: var(--accent-weak);
  color: var(--accent-deep);
}
</style>
