<template>
  <div>
    <div class="row" style="margin-bottom: 12px">
      <h3 style="margin: 0">我的通知</h3>
      <button class="btn btn-ghost btn-small" style="margin-left: auto" @click="onReadAll"
              :disabled="allDone">全部已读</button>
    </div>
    <p v-if="items.length === 0" class="muted">暂无通知</p>
    <div v-for="n in items" :key="n.id" class="card clickable" @click="open(n)">
      <div class="row">
        <span class="dot" v-if="!n.read"></span>
        <span :class="{ unread: !n.read }">{{ n.title }}</span>
      </div>
      <div class="muted">{{ n.content }}</div>
    </div>
    <button v-if="items.length >= 20" class="btn btn-ghost" style="width: 100%"
            @click="loadMore">加载更多</button>
  </div>
</template>

<script>
import { listNotifications, markAllRead, markRead } from '../api/notifications'

export default {
  data() {
    return { items: [], allDone: false }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      const data = await listNotifications(null)
      this.items = data.items
      this.allDone = data.unreadCount === 0
    },
    async loadMore() {
      const last = this.items.length ? this.items[this.items.length - 1].id : null
      const data = await listNotifications(last)
      this.items = this.items.concat(data.items)
    },
    async open(n) {
      if (!n.read) {
        await markRead(n.id)
        n.read = true
      }
      if (n.sheetId) this.$router.push('/sheets/' + n.sheetId)
    },
    async onReadAll() {
      await markAllRead()
      this.items.forEach((n) => { n.read = true })
      this.allDone = true
    },
  },
}
</script>

<style scoped>
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4f7cff;
  display: inline-block;
  margin-right: 6px;
  flex-shrink: 0;
}
.unread {
  font-weight: 600;
}
</style>
