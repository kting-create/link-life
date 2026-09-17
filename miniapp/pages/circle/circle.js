const { request } = require('../../utils/request');

Page({
  data: {
    tab: 'circles',
    circles: [],
    members: [],
    memberCircleId: null,
    loading: false,
  },

  onShow() {
    this.loadCircles();
  },

  loadCircles() {
    this.setData({ loading: true });
    request('/api/circles')
      .then((list) => {
        this.setData({ circles: list || [] });
        const current = wx.getStorageSync('currentCircle');
        if (current && this.data.tab === 'members' && current.id) {
          this.loadMembers(current.id);
        }
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载圈子失败', icon: 'none' });
      })
      .then(() => {
        this.setData({ loading: false });
      });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ tab });
    if (tab === 'members' && !this.data.members.length) {
      const current = wx.getStorageSync('currentCircle');
      if (current && current.id) {
        this.loadMembers(current.id);
      } else if (this.data.circles.length) {
        this.loadMembers(this.data.circles[0].id);
      }
    }
  },

  selectMemberCircle(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ memberCircleId: id });
    this.loadMembers(id);
  },

  loadMembers(id) {
    this.setData({ memberCircleId: id });
    request('/api/circles/' + id + '/members')
      .then((list) => {
        this.setData({ members: list || [] });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载成员失败', icon: 'none' });
      });
  },

  createCircle() {
    wx.showModal({
      title: '创建圈子',
      editable: true,
      placeholderText: '请输入圈子名称',
      success: (res) => {
        if (!res.confirm) return;
        const name = (res.content || '').trim();
        if (!name) {
          wx.showToast({ title: '圈子名称不能为空', icon: 'none' });
          return;
        }
        request('/api/circles', { method: 'POST', data: { name } })
          .then((circle) => {
            wx.showToast({ title: '创建成功', icon: 'success' });
            this.setData({
              circles: this.data.circles.concat([circle]),
              memberCircleId: circle.id,
            });
          })
          .catch((err) => {
            wx.showToast({ title: (err && err.message) || '创建失败', icon: 'none' });
          });
      },
    });
  },

  joinCircle() {
    wx.showModal({
      title: '加入圈子',
      editable: true,
      placeholderText: '请输入邀请码',
      success: (res) => {
        if (!res.confirm) return;
        const inviteCode = (res.content || '').trim();
        if (!inviteCode) {
          wx.showToast({ title: '邀请码不能为空', icon: 'none' });
          return;
        }
        request('/api/circles/join', { method: 'POST', data: { inviteCode } })
          .then((circle) => {
            wx.showToast({ title: '加入成功', icon: 'success' });
            const exists = this.data.circles.some((c) => c.id === circle.id);
            if (!exists) {
              this.setData({ circles: this.data.circles.concat([circle]) });
            }
          })
          .catch((err) => {
            wx.showToast({ title: (err && err.message) || '加入失败', icon: 'none' });
          });
      },
    });
  },

  onCircleTap(e) {
    const circle = this.data.circles.find((c) => c.id === e.currentTarget.dataset.id);
    if (!circle) return;
    wx.setStorageSync('currentCircle', circle);
    wx.navigateTo({ url: '/pages/sheet-list/sheet-list?circleId=' + circle.id });
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' });
  },
});
