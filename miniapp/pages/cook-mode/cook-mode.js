const { request, BASE_URL } = require('../../utils/request');
const { upload } = require('../../utils/upload');

Page({
  data: {
    recipe: null,
    steps: [],
    current: 0,
    remainText: '00:00',
    progressPct: 0,
    counting: false,
    hasDuration: false,
    currentPhotos: [],
    advice: null,
    advicePhotoId: null,
  },

  onLoad(options) {
    this.recipeId = options.id;
    this.timer = null;
    this.remainSec = 0;
    this.audio = null;
    this.photosByStep = {};
    this.load();
  },

  onShow() {
    wx.setKeepScreenOn({ keepScreenOn: true });
  },

  onHide() {
    wx.setKeepScreenOn({ keepScreenOn: false });
    this.stopTimer();
  },

  onUnload() {
    wx.setKeepScreenOn({ keepScreenOn: false });
    this.stopTimer();
    if (this.audio) this.audio.destroy();
  },

  load() {
    const self = this;
    request('/api/recipes/' + this.recipeId).then((recipe) => {
      const steps = (recipe.content.steps || []).map((s) => ({
        no: s.no,
        text: s.text,
        durationSec: s.durationSec || 0,
      }));
      self.setData({ recipe, steps });
      self.enterStep(self.data.current);
      return request('/api/recipes/' + this.recipeId + '/photos');
    }).then((photos) => {
      const byStep = {};
      (photos || []).forEach((p) => {
        p.fullUrl = p.url.startsWith('http') ? p.url : BASE_URL + p.url;
        (byStep[p.stepNo] = byStep[p.stepNo] || []).push(p);
      });
      this.photosByStep = byStep;
      self.refreshPhotos();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' });
    });
  },

  refreshPhotos() {
    const step = this.data.steps[this.data.current];
    const list = (step && this.photosByStep && this.photosByStep[step.no]) || [];
    this.setData({ currentPhotos: list });
  },

  enterStep(idx) {
    this.stopTimer();
    const step = this.data.steps[idx];
    const duration = step ? step.durationSec : 0;
    this.setData({
      current: idx,
      remainText: this.fmt(duration),
      progressPct: 0,
      counting: false,
      hasDuration: duration > 0,
    });
    this.remainSec = duration;
    this.refreshPhotos();
    if (duration > 0) this.startTimer();
  },

  fmt(sec) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
  },

  startTimer() {
    if (this.timer || this.remainSec <= 0) return;
    const self = this;
    this.setData({ counting: true });
    this.timer = setInterval(() => {
      self.remainSec = Math.max(0, self.remainSec - 1);
      const total = self.data.steps[self.data.current].durationSec || 1;
      self.setData({
        remainText: self.fmt(self.remainSec),
        progressPct: Math.round(((total - self.remainSec) / total) * 100),
      });
      if (self.remainSec <= 0) self.finishStep();
    }, 1000);
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.data.counting) this.setData({ counting: false });
  },

  finishStep() {
    this.stopTimer();
    this.playDing();
    wx.vibrateLong({ fail: () => {} });
  },

  playDing() {
    try {
      if (!this.audio) {
        this.audio = wx.createInnerAudioContext();
        this.audio.src = '/assets/ding.wav';
      }
      this.audio.stop();
      this.audio.play();
    } catch (e) {
      wx.vibrateLong({ fail: () => {} });
    }
  },

  toggleTimer() {
    if (this.data.counting) this.stopTimer();
    else this.startTimer();
  },

  skipTimer() {
    this.remainSec = 0;
    this.setData({ remainText: this.fmt(0), progressPct: 100 });
    this.stopTimer();
  },

  prevStep() {
    if (this.data.current > 0) this.enterStep(this.data.current - 1);
  },

  nextStep() {
    if (this.data.current < this.data.steps.length - 1) {
      this.enterStep(this.data.current + 1);
    }
  },

  takePhoto() {
    const self = this;
    const stepNo = this.data.steps[this.data.current].no;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      sizeType: ['compressed'],
      success(res) {
        const filePath = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        upload('/api/recipes/' + self.recipeId + '/steps/' + stepNo + '/photos', filePath)
          .then(() => {
            wx.hideLoading();
            self.load();
          })
          .catch((err) => {
            wx.hideLoading();
            wx.showToast({ title: (err && err.message) || '上传失败', icon: 'none' });
          });
      },
    });
  },

  askAi(e) {
    const self = this;
    const photoId = e.currentTarget.dataset.id;
    wx.showLoading({ title: 'AI 分析中' });
    request('/api/photos/' + photoId + '/analysis', { method: 'POST' })
      .then((result) => {
        wx.hideLoading();
        self.setData({ advice: result, advicePhotoId: photoId });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: (err && err.message) || '分析失败', icon: 'none' });
      });
  },

  dismissAdvice() {
    this.setData({ advice: null, advicePhotoId: null });
  },

  applyAdvice() {
    const self = this;
    request('/api/photos/' + this.data.advicePhotoId + '/apply', { method: 'POST' })
      .then(() => {
        wx.showToast({ title: '已生成新版本', icon: 'success' });
        self.setData({ advice: null, advicePhotoId: null });
        self.load();
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '应用失败', icon: 'none' });
      });
  },
});
