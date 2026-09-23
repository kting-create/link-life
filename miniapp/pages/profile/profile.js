const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    nickname: '',
    avatar: '',
    bindingCode: '',
    expiresText: '',
    saving: false,
    unread: 0,
    tasteSummary: '',
    tasteTags: [],
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  onShow() {
    request('/api/notifications/unread-count')
      .then((d) => this.setData({ unread: d.unreadCount }))
      .catch(() => {});
    request('/api/me/taste-profile')
      .then((p) => this.setData({
        tasteSummary: p.summary || '',
        tasteTags: p.tags || [],
      }))
      .catch(() => {});
    request('/api/me')
      .then((user) => {
        this.setData({ nickname: user.nickname || '', avatar: user.avatar || '' });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '加载个人信息失败', 'err');
      });
  },

  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value });
  },

  saveProfile() {
    const nickname = (this.data.nickname || '').trim();
    if (!nickname) {
      showGlassToast('昵称不能为空', 'err');
      return;
    }
    if (this.data.saving) return;
    this.setData({ saving: true });
    request('/api/me', { method: 'PUT', data: { nickname } })
      .then((user) => {
        showGlassToast('保存成功', 'ok');
        this.setData({ nickname: user.nickname || nickname });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '保存失败', 'err');
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
        showGlassToast((err && err.message) || '生成绑定码失败', 'err');
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

  goPantry() {
    wx.navigateTo({ url: '/pages/pantry/pantry' });
  },

  goCircle() {
    wx.reLaunch({ url: '/pages/circle/circle' });
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后本机登录状态将失效,确定退出?',
      success: (res) => {
        if (!res.confirm) return;
        request('/api/auth/logout', { method: 'POST' })
          .catch(() => {})
          .then(() => {
            wx.clearStorageSync();
            wx.reLaunch({ url: '/pages/login/login' });
          });
      },
    });
  },
});
