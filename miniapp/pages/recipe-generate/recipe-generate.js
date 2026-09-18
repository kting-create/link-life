const { streamRequest } = require('../../utils/sse');

Page({
  data: {
    streamText: '',
    streaming: false,
    failed: false,
    errorText: '',
  },

  onLoad(options) {
    this.options = options;
    this.start();
  },

  start() {
    const self = this;
    this.setData({ streaming: true, failed: false, errorText: '', streamText: '' });
    const path = this.options.recipeId
      ? '/api/recipes/' + this.options.recipeId + '/iterate'
      : '/api/recipes/generate';
    const body = this.options.recipeId
      ? { comment: this.options.comment || '' }
      : { circleId: Number(this.options.circleId), dishName: this.options.dishName };
    this.task = streamRequest(path, body, {
      onDelta(text) {
        self.setData({ streamText: self.data.streamText + text });
      },
      onDone(payload) {
        self.setData({ streaming: false });
        wx.redirectTo({
          url: '/pages/recipe-detail/recipe-detail?id=' + payload.recipeId,
        });
      },
      onError(err) {
        self.setData({
          streaming: false,
          failed: true,
          errorText: (err && err.message) || '生成失败，请重试',
        });
      },
    });
  },

  retry() {
    this.start();
  },

  onUnload() {
    if (this.task && this.task.abort) this.task.abort();
  },
});
