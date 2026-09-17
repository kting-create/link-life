const { request } = require('../../utils/request');

Page({
  data: {
    circleId: null,
    title: '',
    items: [{ dishName: '', note: '' }],
    submitting: false,
  },

  onLoad(options) {
    const current = wx.getStorageSync('currentCircle');
    const circleId = options.circleId || (current && current.id) || null;
    this.setData({ circleId });
  },

  onTitleInput(e) {
    this.setData({ title: e.detail.value });
  },

  onDishInput(e) {
    const idx = e.currentTarget.dataset.index;
    const key = 'items[' + idx + '].dishName';
    this.setData({ [key]: e.detail.value });
  },

  onNoteInput(e) {
    const idx = e.currentTarget.dataset.index;
    const key = 'items[' + idx + '].note';
    this.setData({ [key]: e.detail.value });
  },

  addRow() {
    this.setData({ items: this.data.items.concat([{ dishName: '', note: '' }]) });
  },

  removeRow(e) {
    const idx = e.currentTarget.dataset.index;
    if (this.data.items.length <= 1) {
      wx.showToast({ title: '至少保留一道菜', icon: 'none' });
      return;
    }
    const items = this.data.items.filter((_, i) => i !== idx);
    this.setData({ items });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.circleId) {
      wx.showToast({ title: '缺少圈子信息，请从清单页进入', icon: 'none' });
      return;
    }
    const title = (this.data.title || '').trim();
    if (!title) {
      wx.showToast({ title: '请填写点单标题', icon: 'none' });
      return;
    }
    const items = this.data.items
      .map((it) => ({ dishName: (it.dishName || '').trim(), note: (it.note || '').trim() }))
      .filter((it) => it.dishName);
    if (!items.length) {
      wx.showToast({ title: '请至少填写一道菜名', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    request('/api/order/sheets', { method: 'POST', data: { circleId: this.data.circleId, title, items } })
      .then((sheet) => {
        wx.showToast({ title: '创建成功', icon: 'success' });
        wx.redirectTo({ url: '/pages/sheet-detail/sheet-detail?id=' + sheet.id });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '创建失败', icon: 'none' });
      })
      .then(() => {
        this.setData({ submitting: false });
      });
  },
});
