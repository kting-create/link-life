const { request } = require('../../utils/request');

const SHEET_STATUS_TEXT = {
  SHARED: '分享中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已收单',
};

const ITEM_STATUS_TEXT = {
  OPEN: '待认领',
  CLAIMED: '已认领',
  COOKING: '烹饪中',
  DONE: '已完成',
};

Page({
  data: {
    readonly: true,
    sheet: null,
    items: [],
    statusText: '',
    claimedCount: 0,
    totalCount: 0,
    showComplete: false,
    acting: false,
  },

  onLoad(options) {
    if (options.token) {
      this.loadByToken(options.token);
    } else if (options.id) {
      this.loadInteractive(options.id);
    } else {
      wx.showToast({ title: '缺少清单参数', icon: 'none' });
    }
  },

  onShow() {
    if (!this.data.readonly && this.sheetId) {
      this.reload();
    }
  },

  onShareAppMessage() {
    const sheet = this.data.sheet;
    if (sheet && sheet.shareToken) {
      return {
        title: sheet.title,
        path: '/pages/sheet-detail/sheet-detail?token=' + sheet.shareToken,
      };
    }
    return { title: 'Link-Life 点单', path: '/pages/circle/circle' };
  },

  loadByToken(token) {
    request('/api/share/' + token)
      .then((sheet) => {
        this.sheetId = sheet.id;
        this.applySheet(sheet, true);
        this.tryUpgrade(sheet.id);
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '分享内容加载失败', icon: 'none' });
      });
  },

  tryUpgrade(id) {
    if (!wx.getStorageSync('accessToken')) return;
    request('/api/me')
      .then((user) => {
        return request('/api/order/sheets/' + id).then((sheet) => {
          this.applySheet(sheet, false, user.id);
        });
      })
      .catch(() => {});
  },

  loadInteractive(id) {
    request('/api/me')
      .then((user) => {
        return request('/api/order/sheets/' + id).then((sheet) => {
          this.sheetId = id;
          this.applySheet(sheet, false, user.id);
        });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载清单失败', icon: 'none' });
      });
  },

  reload() {
    request('/api/order/sheets/' + this.sheetId)
      .then((sheet) => {
        return request('/api/me').then((user) => {
          this.applySheet(sheet, false, user.id);
        });
      })
      .catch(() => {});
  },

  applySheet(sheet, readonly, userId) {
    const actionsEnabled = !readonly && sheet.status !== 'COMPLETED';
    const items = (sheet.items || []).map((it) => ({
      ...it,
      statusText: ITEM_STATUS_TEXT[it.itemStatus] || it.itemStatus,
      mine: userId && it.claimantId === userId,
      canClaim: actionsEnabled && it.itemStatus === 'OPEN',
      canCook: actionsEnabled && it.claimantId === userId && it.itemStatus === 'CLAIMED',
      canFinish: actionsEnabled && it.claimantId === userId && it.itemStatus === 'COOKING',
      canRelease:
        actionsEnabled &&
        it.claimantId === userId &&
        (it.itemStatus === 'CLAIMED' || it.itemStatus === 'COOKING'),
    }));
    const claimed = items.filter((it) => it.claimantId).length;
    this.setData({
      readonly,
      sheet,
      items,
      statusText: SHEET_STATUS_TEXT[sheet.status] || sheet.status,
      claimedCount: claimed,
      totalCount: items.length,
      showComplete: actionsEnabled && userId && sheet.creatorId === userId,
    });
  },

  openRecipe(e) {
    const dishName = e.currentTarget.dataset.name;
    const circleId = this.data.sheet.circleId;
    request('/api/recipes/by-dish?circleId=' + circleId + '&dishName=' +
        encodeURIComponent(dishName))
      .then((recipe) => {
        wx.navigateTo({ url: '/pages/recipe-detail/recipe-detail?id=' + recipe.id });
      })
      .catch(() => {
        wx.navigateTo({
          url: '/pages/recipe-generate/recipe-generate?circleId=' + circleId +
            '&dishName=' + encodeURIComponent(dishName),
        });
      });
  },

  claimItem(e) {
    this.act('/api/order/items/' + e.currentTarget.dataset.id + '/claim', 'POST', '认领成功');
  },

  startCook(e) {
    this.act(
      '/api/order/items/' + e.currentTarget.dataset.id + '/status',
      'POST',
      '开始烹饪',
      { itemStatus: 'COOKING' }
    );
  },

  finishItem(e) {
    this.act(
      '/api/order/items/' + e.currentTarget.dataset.id + '/status',
      'POST',
      '已完成',
      { itemStatus: 'DONE' }
    );
  },

  releaseItem(e) {
    this.act('/api/order/items/' + e.currentTarget.dataset.id + '/release', 'POST', '已释放');
  },

  completeSheet() {
    wx.showModal({
      title: '收单确认',
      content: '收单后所有人不能再操作菜品，确定收单吗？',
      success: (res) => {
        if (!res.confirm) return;
        this.act('/api/order/sheets/' + this.sheetId + '/complete', 'POST', '已收单');
      },
    });
  },

  act(path, method, successText, data) {
    if (this.data.acting) return;
    this.setData({ acting: true });
    request(path, { method, data })
      .then(() => {
        wx.showToast({ title: successText, icon: 'success' });
        this.reload();
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
      })
      .then(() => {
        this.setData({ acting: false });
      });
  },
});
