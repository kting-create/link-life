const { request } = require('../../utils/request');

const SOURCE_TEXT = {
  AI_GENERATE: 'AI 生成',
  AI_ITERATE: 'AI 迭代',
  MANUAL_EDIT: '手动编辑',
};

Page({
  data: {
    recipe: null,
    versions: [],
    myScore: 0,
    myComment: '',
    nameInput: '',
    atLimit: false,
  },

  onLoad(options) {
    this.recipeId = options.id;
  },

  onShow() {
    this.load();
  },

  load() {
    const self = this;
    request('/api/recipes/' + this.recipeId).then((recipe) => {
      (recipe.content.steps || []).forEach((s) => {
        s.durationText = s.durationSec >= 60
          ? Math.round(s.durationSec / 60) + ' 分钟'
          : (s.durationSec || 0) + ' 秒';
      });
      self.setData({
        recipe,
        nameInput: recipe.customName || '',
        atLimit: recipe.versions.length >= 5,
      });
      return request('/api/recipes/' + this.recipeId + '/versions');
    }).then((versions) => {
      versions.forEach((v) => {
        v.sourceText = SOURCE_TEXT[v.source] || v.source;
        v.createdAtText = (v.createdAt || '').replace('T', ' ').slice(0, 16);
      });
      self.setData({ versions });
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' });
    });
  },

  setScore(e) {
    this.setData({ myScore: Number(e.currentTarget.dataset.score) });
  },

  onCommentInput(e) {
    this.setData({ myComment: e.detail.value });
  },

  submitFeedback() {
    const { myScore } = this.data;
    if (!myScore) {
      wx.showToast({ title: '先点星星评分', icon: 'none' });
      return;
    }
    const self = this;
    request('/api/recipes/' + this.recipeId + '/feedback', {
      method: 'POST',
      data: { score: myScore, comment: this.data.myComment || null },
    }).then(() => {
      wx.showToast({ title: '已提交', icon: 'success' });
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '提交失败', icon: 'none' });
    });
  },

  iterate() {
    wx.navigateTo({
      url: '/pages/recipe-generate/recipe-generate?recipeId=' + this.recipeId,
    });
  },

  rollback(e) {
    const version = Number(e.currentTarget.dataset.version);
    const self = this;
    wx.showModal({
      title: '回滚确认',
      content: '回滚到 v' + version + '？历史版本不会删除',
      success(res) {
        if (!res.confirm) return;
        request('/api/recipes/' + self.recipeId + '/rollback', {
          method: 'POST',
          data: { version },
        }).then(() => self.load())
          .catch((err) => wx.showToast({
            title: (err && err.message) || '回滚失败', icon: 'none' }));
      },
    });
  },

  onNameInput(e) {
    this.setData({ nameInput: e.detail.value });
  },

  saveName() {
    const self = this;
    request('/api/recipes/' + this.recipeId, {
      method: 'PUT',
      data: { customName: this.data.nameInput || null },
    }).then(() => {
      wx.showToast({ title: '已保存', icon: 'success' });
      self.load();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '保存失败', icon: 'none' });
    });
  },
});
