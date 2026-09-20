<template>
  <div class="notifications-page">
    <div class="row page-head">
      <h3 class="page-title">我的通知</h3>
      <button class="btn btn-secondary btn-small" @click="onReadAll"
              :disabled="allDone">全部已读</button>
    </div>
    <p v-if="items.length === 0" class="empty">暂无通知</p>
    <div v-for="n in items" :key="n.id" class="card clickable notif-card" @click="open(n)">
      <div class="row">
        <span class="unread-bar" v-if="!n.read"></span>
        <span class="title" :class="{ unread: !n.read }">{{ n.title }}</span>
        <span class="time">{{ n.createdAt }}</span>
      </div>
      <div class="content">{{ n.content }}</div>
    </div>
    <button v-if="items.length >= 20" class="btn btn-secondary load-more"
            @click="loadMore">加载更多</button>
  </div>
</template>

<script>
import { listNotifications, markAllRead, markRead } from '../api/notifications'
import { showToast } from '../utils/toast'

export default {
  data() {
    return { items: [], allDone: false }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const data = await listNotifications(null)
        this.items = data.items
        this.allDone = data.unreadCount === 0
      } catch (e) {
        console.error('load notifications failed', e)
        showToast('加载通知失败')
      }
    },
    async loadMore() {
      const last = this.items.length ? this.items[this.items.length - 1].id : null
      const data = await listNotifications(last)
      this.items = this.items.concat(data.items)
    },
    async open(n) {
      if (!n.read) {
        try {
          await markRead(n.id)
          n.read = true
        } catch (e) {
          console.error('mark notification read failed', e)
        }
      }
      if (n.sheetId) this.$router.push('/sheets/' + n.sheetId)
    },
    async onReadAll() {
      try {
        await markAllRead()
        this.items.forEach((n) => { n.read = true })
        this.allDone = true
      } catch (e) {
        console.error('mark all read failed', e)
        showToast('操作失败')
      }
    },
  },
}
</script>

<style scoped>
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-head {
  justify-content: space-between;
}
.page-head .page-title {
  margin: 0;
}
.notif-card .row {
  min-width: 0;
}
.unread-bar {
  width: 6px;
  align-self: stretch;
  border-radius: 3px;
  background: var(--primary);
  flex-shrink: 0;
}
.title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.unread {
  font-weight: 600;
  color: var(--text-primary);
}
.time {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-size: 12px;
}
.content {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}
.load-more {
  width: 100%;
}
</style>
