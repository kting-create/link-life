const { request } = require('../../utils/request');

const SHEET_STATUS_TEXT = {
  SHARED: '分享中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已收单',
};

Page({
  data: {
    circleId: null,
    circleName: '',
    sheets: [],
    loading: false,
  },

  onLoad(options) {
    const current = wx.getStorageSync('currentCircle');
    const circleId = options.circleId || (current && current.id) || null;
    this.setData({
      circleId,
      circleName: (current && current.name) || '',
    });
  },

  onShow() {
    this.loadSheets();
  },

  loadSheets() {
    if (!this.data.circleId) {
      wx.showToast({ title: '缺少圈子信息', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    request('/api/order/sheets?circleId=' + this.data.circleId)
      .then((list) => {
        const sheets = (list || []).map((s) => {
          const items = s.items || [];
          const claimed = items.filter((it) => it.claimantId).length;
          return {
            ...s,
            statusText: SHEET_STATUS_TEXT[s.status] || s.status,
            statusClass: 'badge-' + String(s.status || '').toLowerCase(),
            claimedCount: claimed,
            totalCount: items.length,
          };
        });
        this.setData({ sheets });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载清单失败', icon: 'none' });
      })
      .then(() => {
        this.setData({ loading: false });
      });
  },

  addSheet() {
    wx.navigateTo({
      url: '/pages/order-create/order-create?circleId=' + this.data.circleId,
    });
  },

  openSheet(e) {
    wx.navigateTo({
      url: '/pages/sheet-detail/sheet-detail?id=' + e.currentTarget.dataset.id,
    });
  },
});
