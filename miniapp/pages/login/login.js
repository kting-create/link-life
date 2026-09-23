const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    loading: false,
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  login() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: (res) => {
        if (!res.code) {
          this.setData({ loading: false });
          showGlassToast('wx.login 未返回 code', 'err');
          return;
        }
        request('/api/auth/wx-login', {
          method: 'POST',
          data: { code: res.code },
        })
          .then((data) => {
            wx.setStorageSync('accessToken', data.accessToken);
            wx.setStorageSync('refreshToken', data.refreshToken);
            wx.reLaunch({ url: '/pages/circle/circle' });
          })
          .catch((err) => {
            showGlassToast((err && err.message) || '登录失败', 'err');
          })
          .then(() => {
            this.setData({ loading: false });
          });
      },
      fail: () => {
        this.setData({ loading: false });
        showGlassToast('wx.login 调用失败', 'err');
      },
    });
  },
});
