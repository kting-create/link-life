const { request } = require('../../utils/request');

Page({
  data: {
    items: [],
    allDone: true,
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return request('/api/notifications?size=20')
      .then((data) => {
        this.setData({ items: data.items, allDone: data.unreadCount === 0 });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载通知失败', icon: 'none' });
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
        wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
      });
  },
});
