App({
  onLaunch() {
    const token = wx.getStorageSync('accessToken');
    if (token) {
      wx.reLaunch({ url: '/pages/circle/circle' });
    }
  },
});
