const { request } = require('../../utils/request');

Page({
  data: {
    nickname: '',
    avatar: '',
    bindingCode: '',
    expiresText: '',
    saving: false,
    unread: 0,
  },

  onShow() {
    request('/api/notifications/unread-count')
      .then((d) => this.setData({ unread: d.unreadCount }))
      .catch(() => {});
    request('/api/me')
      .then((user) => {
        this.setData({ nickname: user.nickname || '', avatar: user.avatar || '' });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载个人信息失败', icon: 'none' });
      });
  },

  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value });
  },

  saveProfile() {
    const nickname = (this.data.nickname || '').trim();
    if (!nickname) {
      wx.showToast({ title: '昵称不能为空', icon: 'none' });
      return;
    }
    if (this.data.saving) return;
    this.setData({ saving: true });
    request('/api/me', { method: 'PUT', data: { nickname } })
      .then((user) => {
        wx.showToast({ title: '保存成功', icon: 'success' });
        this.setData({ nickname: user.nickname || nickname });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '保存失败', icon: 'none' });
      })
      .then(() => {
        this.setData({ saving: false });
      });
  },

  generateBindingCode() {
    request('/api/auth/binding-code', { method: 'POST' })
      .then((data) => {
        this.setData({
          bindingCode: data.code,
          expiresText: this.formatExpiry(data.expiresAt),
        });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '生成绑定码失败', icon: 'none' });
      });
  },

  formatExpiry(expiresAt) {
    if (!expiresAt) return '';
    const s = String(expiresAt);
    const m = s.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
    if (m) {
      return m[1] + '-' + m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5];
    }
    return s;
  },

  goNotifications() {
    wx.navigateTo({ url: '/pages/notifications/notifications' });
  },

  goCircle() {
    wx.reLaunch({ url: '/pages/circle/circle' });
  },
});
