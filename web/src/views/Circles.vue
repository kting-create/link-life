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
        <Field v-model="newName" placeholder="圈子名称" />
        <button class="btn btn-primary btn-small" :disabled="acting" @click="createCircle">创建</button>
      </div>
      <div v-if="showJoin" class="inline-form">
        <Field v-model="inviteCode" placeholder="邀请码" />
        <button class="btn btn-primary btn-small" :disabled="acting" @click="joinCircle">加入</button>
      </div>
      <Skeleton v-if="loading" :rows="3" />
      <EmptyState v-else-if="!circles.length" title="还没有圈子，创建或加入一个吧" />
      <div v-else class="stagger-list" :class="{ run: circlesRun }">
        <div
          v-for="(c, index) in circles"
          :key="c.id"
          class="card clickable circle-card stagger-item"
          :class="{ active: selectedId === c.id }"
          :style="{ '--i': index }"
          @click="selectCircle(c.id)"
        >
          <div class="row circle-row">
            <span class="avatar">{{ c.name.charAt(0) }}</span>
            <strong>{{ c.name }}</strong>
            <span class="meta">邀请码：{{ c.inviteCode }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selectedId" class="card">
      <div class="row section-head">
        <h3>成员</h3>
        <button class="btn btn-secondary btn-small" @click="loadMembers">刷新</button>
      </div>
      <Skeleton v-if="loadingMembers" :rows="2" />
      <EmptyState v-else-if="!members.length" title="暂无成员" />
      <div v-else class="stagger-list" :class="{ run: membersRun }">
        <div v-for="(m, index) in members" :key="m.userId" class="member-row stagger-item" :style="{ '--i': index }">
          <span>{{ m.nickname }}</span>
          <span class="badge-pill" :class="m.role === 'OWNER' ? 'role-owner' : 'role-member'">
            {{ m.role === 'OWNER' ? '圈主' : '成员' }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="selectedId" class="card">
      <div class="row section-head">
        <h3>点单清单</h3>
        <button class="btn btn-primary btn-small" @click="showCreateSheet = !showCreateSheet">
          <Icon name="plus" /> 发起点单
        </button>
      </div>

      <div v-if="showCreateSheet" class="sheet-form">
        <Field v-model="sheetTitle" placeholder="点单标题" />
        <div v-for="(item, idx) in sheetItems" :key="idx" class="row item-row">
          <Field v-model="item.dishName" placeholder="菜名" />
          <Field v-model="item.note" placeholder="备注" />
          <button class="btn btn-secondary btn-small" @click="removeRow(idx)">删除</button>
        </div>
        <div class="row">
          <button class="btn btn-secondary btn-small" @click="addRow">加一道菜</button>
          <button class="btn btn-primary btn-small" :disabled="creating" @click="submitSheet">创建清单</button>
        </div>
      </div>

      <Skeleton v-if="loadingSheets" :rows="2" />
      <EmptyState v-else-if="!sheets.length" title="该圈子暂无清单" />
      <div v-else class="stagger-list" :class="{ run: sheetsRun }">
        <div
          v-for="(s, index) in sheets"
          :key="s.id"
          class="card clickable sheet-card stagger-item"
          :style="{ '--i': index }"
          @click="openSheet(s, $event)"
        >
          <div class="row">
            <strong>{{ s.title }}</strong>
            <span class="badge-pill" :class="sheetBadgeClass(s.status)">
              {{ sheetStatusText[s.status] || s.status }}
            </span>
          </div>
          <div class="meta">
            {{ (s.items || []).filter((it) => it.claimantId).length }}/{{ (s.items || []).length }} 已认领
          </div>
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
import { sheetStatusText, sheetBadgeClass } from '../utils/sheet'
import { navigateWithHero } from '../utils/vtNav'
import EmptyState from '../components/EmptyState.vue'
import Skeleton from '../components/Skeleton.vue'
import Field from '../components/Field.vue'
import Icon from '../components/Icon.vue'

export default {
  components: { EmptyState, Skeleton, Field, Icon },
  setup() {
    const router = useRouter()
    const circles = ref([])
    const members = ref([])
    const sheets = ref([])
    const selectedId = ref(null)
    const loading = ref(false)
    const loadingMembers = ref(false)
    const loadingSheets = ref(false)
    const circlesRun = ref(false)
    const membersRun = ref(false)
    const sheetsRun = ref(false)
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
        circlesRun.value = true
      } catch (err) {
        showToast((err && err.message) || '加载圈子失败')
      } finally {
        loading.value = false
      }
    }

    async function loadMembers() {
      if (!selectedId.value) return
      loadingMembers.value = true
      try {
        members.value = (await request('/api/circles/' + selectedId.value + '/members')) || []
        membersRun.value = true
      } catch (err) {
        showToast((err && err.message) || '加载成员失败')
      } finally {
        loadingMembers.value = false
      }
    }

    async function loadSheets() {
      if (!selectedId.value) return
      loadingSheets.value = true
      try {
        sheets.value =
          (await request('/api/order/sheets?circleId=' + selectedId.value)) || []
        sheetsRun.value = true
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

    function openSheet(s, e) {
      const el = e && e.currentTarget
      return navigateWithHero(el, 'sheet-hero', () => router.push('/sheets/' + s.id))
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
      loadingMembers,
      loadingSheets,
      circlesRun,
      membersRun,
      sheetsRun,
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
      sheetBadgeClass,
      selectCircle,
      openSheet,
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
  gap: var(--space-2);
}
.section-head {
  justify-content: space-between;
  margin-bottom: var(--space-2);
}
.section-head h3 {
  margin: 0;
}
.inline-form {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  align-items: flex-start;
}
.inline-form .field {
  flex: 1;
  margin-bottom: 0;
}
.circle-card {
  margin-top: var(--space-2);
}
.circle-card.active {
  border-color: var(--primary);
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
  font-size: var(--text-xs);
  text-align: right;
}
.member-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-1) 0;
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
  background: var(--bg-card);
  color: var(--text-secondary);
}
.sheet-form {
  margin-bottom: var(--space-3);
}
.item-row {
  margin-top: var(--space-2);
  align-items: flex-start;
}
.item-row .field {
  flex: 1;
  margin-bottom: 0;
}
.sheet-form > .row:last-child {
  margin-top: var(--space-3);
}
.sheet-card {
  margin-top: var(--space-2);
}
.sheet-card .row {
  justify-content: space-between;
}
</style>
