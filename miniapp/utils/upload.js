const { BASE_URL, refresh } = require('./request');

function rawUpload(path, filePath, token) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + path,
      filePath,
      name: 'file',
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        let body;
        try {
          body = JSON.parse(res.data);
        } catch (e) {
          reject({ message: '上传失败' });
          return;
        }
        if (body.code === 0) resolve(body.data);
        else reject(body);
      },
      fail: () => reject({ message: '上传失败，请检查网络' }),
    });
  });
}

async function upload(path, filePath) {
  const token = wx.getStorageSync('accessToken');
  try {
    return await rawUpload(path, filePath, token);
  } catch (err) {
    if (err && err.code === 2002) {
      let newToken;
      try {
        newToken = await refresh();
      } catch (refreshErr) {
        wx.clearStorageSync();
        wx.reLaunch({ url: '/pages/login/login' });
        throw refreshErr;
      }
      return rawUpload(path, filePath, newToken);
    }
    throw err;
  }
}

module.exports = { upload };
