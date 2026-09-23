const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    items: [],
    allDone: true,
    loading: false,
    listRun: false,
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true });
    return request('/api/notifications?size=20')
      .then((data) => {
        this.setData({
          items: data.items,
          allDone: data.unreadCount === 0,
          listRun: true,
        });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '加载通知失败', 'err');
      })
      .then(() => {
        this.setData({ loading: false });
      });
  },

  loadMore() {
    const items = this.data.items;
    const last = items.length ? items[items.length - 1].id : null;
    request('/api/notifications?size=20&afterId=' + last)
      .then((data) => {
        this.setData({ items: items.concat(data.items || []) });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '加载通知失败', 'err');
      });
  },

  openItem(e) {
    const { id, sheetid, read } = e.currentTarget.dataset;
    if (!read) {
      request('/api/notifications/' + id + '/read', { method: 'POST' }).catch(() => {});
    }
    if (sheetid) {
      wx.navigateTo({ url: '/pages/sheet-detail/sheet-detail?id=' + sheetid });
    }
  },

  readAll() {
    request('/api/notifications/read-all', { method: 'POST' })
      .then(() => this.load())
      .catch((err) => {
        showGlassToast((err && err.message) || '操作失败', 'err');
      });
  },
});
