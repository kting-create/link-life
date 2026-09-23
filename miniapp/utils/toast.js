// miniapp/utils/toast.js — 玻璃 toast 封装（对照 web/src/utils/toast.js）
// 页面用法：wxml 根部挂 <glass-toast id="gtoast" />，onReady 里 bindToast(this, '#gtoast')
// 调用：showGlassToast(text, type) 或 getApp().toastShow(text, type)；type: 'info' | 'ok' | 'err'

let host = null;

function bindToast(page, id) {
  host = page.selectComponent(id);
  const app = typeof getApp === 'function' ? getApp() : null;
  if (app) {
    app.toastShow = function (text, type) {
      showGlassToast(text, type);
    };
  }
}

function showGlassToast(text, type) {
  const t = type || 'info';
  if (host && typeof host.show === 'function') {
    host.show(text, t);
    return;
  }
  wx.showToast({ title: String(text == null ? '' : text), icon: 'none' });
}

module.exports = { bindToast, showGlassToast };
