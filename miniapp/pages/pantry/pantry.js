const { request } = require('../../utils/request');

Page({
  data: {
    items: [],
    types: ['调料', '食材'],
    typeIndex: 0,
    name: '',
  },

  onShow() {
    this.load();
  },

  load() {
    const self = this;
    request('/api/me/pantry').then((items) => self.setData({ items }))
      .catch(() => {});
  },

  onTypeChange(e) {
    this.setData({ typeIndex: Number(e.detail.value) });
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  add() {
    const name = (this.data.name || '').trim();
    if (!name) {
      wx.showToast({ title: '请输入名称', icon: 'none' });
      return;
    }
    const self = this;
    request('/api/me/pantry', {
      method: 'POST',
      data: {
        type: this.data.typeIndex === 0 ? 'SEASONING' : 'INGREDIENT',
        name,
      },
    }).then(() => {
      self.setData({ name: '' });
      self.load();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '添加失败', icon: 'none' });
    });
  },

  del(e) {
    const id = e.currentTarget.dataset.id;
    const self = this;
    request('/api/me/pantry/' + id, { method: 'DELETE' })
      .then(() => self.load())
      .catch((err) => wx.showToast({
        title: (err && err.message) || '删除失败', icon: 'none' }));
  },
});
