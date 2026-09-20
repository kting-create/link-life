function handleFrame(frame, handlers, state) {
  let event = 'message'
  let data = ''
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) {
      if (data) data += '\n'
      data += line.slice(5).trim()
    }
  })
  if (!data) return
  let payload
  try {
    payload = JSON.parse(data)
  } catch (e) {
    return
  }
  if (event === 'delta' && handlers.onDelta) handlers.onDelta(payload.text)
  else if (event === 'done') { state.finished = true; if (handlers.onDone) handlers.onDone(payload) }
  else if (event === 'error') { state.finished = true; if (handlers.onError) handlers.onError(payload) }
}

/**
 * POST SSE。handlers: { onDelta(text), onDone({recipeId, version}), onError({code, message}) }
 */
export async function streamRequest(path, data, handlers) {
  const token = localStorage.getItem('accessToken')
  let res
  try {
    res = await fetch(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      body: JSON.stringify(data),
    })
  } catch (e) {
    handlers.onError({ code: -1, message: '网络错误' })
    return
  }
  if (!res.ok || !res.body) {
    let message = '请求失败(' + res.status + ')'
    try {
      const body = await res.json()
      if (body && body.message) message = body.message
    } catch (e) {
      // 保持默认 message
    }
    handlers.onError({ code: -1, message })
    return
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const state = { finished: false }
  let buf = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const frame = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      handleFrame(frame, handlers, state)
    }
  }
  // 流结束却没收到 done/error 终端帧（如超时被服务端掐断），主动报错避免 UI 悬挂
  if (!state.finished && handlers.onError) {
    handlers.onError({ code: -1, message: '连接中断' })
  }
}
