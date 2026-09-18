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
    editMode: false,
    editForm: null,
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

  toggleEdit() {
    if (this.data.editMode) {
      this.cancelEdit();
    } else {
      this.startEdit();
    }
  },

  startEdit() {
    const c = this.data.recipe.content;
    this.setData({
      editMode: true,
      editForm: {
        servings: c.servings,
        totalMinutes: c.totalMinutes,
        ingredientsText: (c.ingredients || []).map((i) => i.name + ' ' + i.amount).join('\n'),
        seasoningsText: (c.seasonings || []).map((i) => i.name + ' ' + i.amount).join('\n'),
        stepsText: (c.steps || []).map((s) => s.text).join('\n'),
        tips: c.tips || '',
      },
    });
  },

  cancelEdit() {
    this.setData({ editMode: false, editForm: null });
  },

  onEditField(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['editForm.' + field]: e.detail.value });
  },

  /** 一行一项：首个空白前的 token 是名称，其余是数量；空行忽略。 */
  parseItemLines(text) {
    return String(text || '')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const parts = line.split(/\s+/);
        return { name: parts[0], amount: parts.slice(1).join(' ') };
      });
  },

  saveEdit() {
    const f = this.data.editForm;
    const ingredients = this.parseItemLines(f.ingredientsText);
    const seasonings = this.parseItemLines(f.seasoningsText);
    const steps = String(f.stepsText || '')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map((text, idx) => ({ no: idx + 1, text }));
    if (!ingredients.length || !steps.length) {
      wx.showToast({ title: '食材和步骤不能为空', icon: 'none' });
      return;
    }
    const content = {
      servings: Number(f.servings) || 2,
      totalMinutes: Number(f.totalMinutes) || 30,
      ingredients,
      seasonings,
      steps,
      tips: f.tips,
    };
    const self = this;
    request('/api/recipes/' + this.recipeId, {
      method: 'PUT',
      data: { content, changeNote: '手动编辑' },
    }).then(() => {
      wx.showToast({ title: '已保存', icon: 'success' });
      self.cancelEdit();
      self.load();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '保存失败', icon: 'none' });
    });
  },
});
