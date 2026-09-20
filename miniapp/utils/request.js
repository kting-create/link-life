const { BASE_URL } = require('../config');

function raw(path, method, data, token) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        if (res.data.code === 0) resolve(res.data.data);
        else reject(res.data);
      },
      fail: reject,
    });
  });
}

function refresh() {
  return raw('/api/auth/refresh', 'POST',
    { refreshToken: wx.getStorageSync('refreshToken') })
    .then((d) => {
      wx.setStorageSync('accessToken', d.accessToken);
      wx.setStorageSync('refreshToken', d.refreshToken);
      return d.accessToken;
    });
}

function request(path, options = {}) {
  const token = wx.getStorageSync('accessToken');
  return raw(path, options.method || 'GET', options.data, token).catch((err) => {
    if (err && (err.code === 2002 || err.code === 3007)) {
      return refresh()
        .then((t) => raw(path, options.method || 'GET', options.data, t))
        .catch((refreshErr) => {
          wx.clearStorageSync();
          wx.reLaunch({ url: '/pages/login/login' });
          throw refreshErr;
        });
    }
    throw err;
  });
}

module.exports = { request, BASE_URL, refresh };
