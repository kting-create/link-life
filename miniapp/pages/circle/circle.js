const { request } = require('../../utils/request');
const { bindToast, showGlassToast } = require('../../utils/toast');

Page({
  data: {
    tab: 'circles',
    tabItems: [
      { key: 'circles', label: '圈子' },
      { key: 'members', label: '成员' },
    ],
    circles: [],
    members: [],
    memberCircleId: null,
    loading: false,
    circlesRun: false,
    membersRun: false,
  },

  onReady() {
    bindToast(this, '#gtoast');
  },

  onShow() {
    this.loadCircles();
  },

  loadCircles() {
    this.setData({ loading: true });
    request('/api/circles')
      .then((list) => {
        this.setData({ circles: list || [], circlesRun: true });
        const current = wx.getStorageSync('currentCircle');
        if (current && this.data.tab === 'members' && current.id) {
          this.loadMembers(current.id);
        }
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '加载圈子失败', 'err');
      })
      .then(() => {
        this.setData({ loading: false });
      });
  },

  onTabChange(e) {
    const tab = e.detail.value;
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
        this.setData({ members: list || [], membersRun: true });
      })
      .catch((err) => {
        showGlassToast((err && err.message) || '加载成员失败', 'err');
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
          showGlassToast('圈子名称不能为空', 'err');
          return;
        }
        request('/api/circles', { method: 'POST', data: { name } })
          .then((circle) => {
            showGlassToast('创建成功', 'ok');
            this.setData({
              circles: this.data.circles.concat([circle]),
              memberCircleId: circle.id,
            });
          })
          .catch((err) => {
            showGlassToast((err && err.message) || '创建失败', 'err');
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
          showGlassToast('邀请码不能为空', 'err');
          return;
        }
        request('/api/circles/join', { method: 'POST', data: { inviteCode } })
          .then((circle) => {
            showGlassToast('加入成功', 'ok');
            const exists = this.data.circles.some((c) => c.id === circle.id);
            if (!exists) {
              this.setData({ circles: this.data.circles.concat([circle]) });
            }
          })
          .catch((err) => {
            showGlassToast((err && err.message) || '加入失败', 'err');
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
