const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    items: [],
    types: ['调料', '食材'],
    typeIndex: 0,
    name: '',
    loading: false,
    listRun: false,
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  onShow() {
    this.load();
  },

  load() {
    this.setData({ loading: true });
    request('/api/me/pantry')
      .then((items) => {
        this.setData({ items: items || [], listRun: true });
      })
      .catch(() => {})
      .then(() => {
        this.setData({ loading: false });
      });
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
      showGlassToast('请输入名称', 'err');
      return;
    }
    request('/api/me/pantry', {
      method: 'POST',
      data: {
        type: this.data.typeIndex === 0 ? 'SEASONING' : 'INGREDIENT',
        name,
      },
    }).then(() => {
      this.setData({ name: '' });
      this.load();
    }).catch((err) => {
      showGlassToast((err && err.message) || '添加失败', 'err');
    });
  },

  del(e) {
    const id = e.currentTarget.dataset.id;
    request('/api/me/pantry/' + id, { method: 'DELETE' })
      .then(() => this.load())
      .catch((err) => showGlassToast((err && err.message) || '删除失败', 'err'));
  },
});
