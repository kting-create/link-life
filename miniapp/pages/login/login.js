const { request } = require('../../utils/request');

Page({
  data: {
    loading: false,
  },

  login() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: (res) => {
        if (!res.code) {
          this.setData({ loading: false });
          wx.showToast({ title: 'wx.login 未返回 code', icon: 'none' });
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
            wx.showToast({ title: (err && err.message) || '登录失败', icon: 'none' });
          })
          .then(() => {
            this.setData({ loading: false });
          });
      },
      fail: () => {
        this.setData({ loading: false });
        wx.showToast({ title: 'wx.login 调用失败', icon: 'none' });
      },
    });
  },
});
