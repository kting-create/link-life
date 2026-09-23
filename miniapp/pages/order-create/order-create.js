const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    circleId: null,
    title: '',
    items: [{ dishName: '', note: '' }],
    submitting: false,
    listRun: false,
  },

  onLoad(options) {
    const current = wx.getStorageSync('currentCircle');
    const circleId = options.circleId || (current && current.id) || null;
    this.setData({ circleId });
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  onShow() {
    this.setData({ listRun: true });
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
      showGlassToast('至少保留一道菜', 'err');
      return;
    }
    const items = this.data.items.filter((_, i) => i !== idx);
    this.setData({ items });
  },

  submit() {
    if (this.data.submitting) return;
    if (!this.data.circleId) {
      showGlassToast('缺少圈子信息，请从清单页进入', 'err');
      return;
    }
    const title = (this.data.title || '').trim();
    if (!title) {
      showGlassToast('请填写点单标题', 'err');
      return;
    }
    const items = this.data.items
      .map((it) => ({ dishName: (it.dishName || '').trim(), note: (it.note || '').trim() }))
      .filter((it) => it.dishName);
    if (!items.length) {
      showGlassToast('请至少填写一道菜名', 'err');
      return;
    }
    this.setData({ submitting: true });
    request('/api/order/sheets', { method: 'POST', data: { circleId: this.data.circleId, title, items } })
      .then((sheet) => {
        showGlassToast('创建成功', 'ok');
        wx.redirectTo({ url: '/pages/sheet-detail/sheet-detail?id=' + sheet.id });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '创建失败', 'err');
      })
      .then(() => {
        this.setData({ submitting: false });
      });
  },
});
