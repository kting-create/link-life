const { BASE_URL } = require('../config');

function arrayBufferToString(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  try {
    return decodeURIComponent(escape(binary));
  } catch (e) {
    // UTF-8 多字节被 chunk 截断时可能失败：退回原文，done 后以服务端数据为准
    return binary;
  }
}

function handleFrame(frame, handlers) {
  let event = 'message';
  let data = '';
  frame.split('\n').forEach((line) => {
    if (line.indexOf('event:') === 0) event = line.slice(6).trim();
    else if (line.indexOf('data:') === 0) data += line.slice(5).trim();
  });
  if (!data) return;
  let payload;
  try {
    payload = JSON.parse(data);
  } catch (e) {
    return;
  }
  if (event === 'delta' && handlers.onDelta) handlers.onDelta(payload.text);
  else if (event === 'done' && handlers.onDone) handlers.onDone(payload);
  else if (event === 'error' && handlers.onError) handlers.onError(payload);
}

/**
 * POST 流式请求（SSE over chunked）。需基础库 >= 2.20.1。
 * handlers: { onDelta(text), onDone({recipeId, version}), onError({code, message}) }
 */
function streamRequest(path, data, handlers) {
  const token = wx.getStorageSync('accessToken');
  const task = wx.request({
    url: BASE_URL + path,
    method: 'POST',
    data,
    enableChunked: true,
    timeout: 120000,
    header: Object.assign(
      { 'Content-Type': 'application/json' },
      token ? { Authorization: 'Bearer ' + token } : {}
    ),
    success: () => {},
    fail: (err) => {
      if (handlers.onError) handlers.onError({ code: -1, message: err.errMsg || '网络错误' });
    },
  });
  let buf = '';
  task.onChunkReceived((res) => {
    buf += arrayBufferToString(res.data);
    let idx;
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const frame = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      handleFrame(frame, handlers);
    }
  });
  return task;
}

module.exports = { streamRequest };
