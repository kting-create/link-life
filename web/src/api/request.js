import axios from 'axios'
import router from '../router'
import { showToast } from '../utils/toast'

const http = axios.create({
  baseURL: '',
  timeout: 15000,
  validateStatus: () => true,
})

export function saveTokens(data) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
}

async function raw(path, method, data, token) {
  const res = await http.request({
    url: path,
    method,
    data,
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  })
  return res.data
}

async function refresh() {
  const refreshToken = localStorage.getItem('refreshToken')
  const body = await raw('/api/auth/refresh', 'POST', { refreshToken })
  if (body.code !== 0) throw body
  saveTokens(body.data)
  return body.data.accessToken
}

export async function publicRequest(path, options = {}) {
  const method = options.method || 'GET'
  const body = await raw(path, method, options.data, null)
  if (body.code === 0) return body.data
  throw body
}

export async function request(path, options = {}) {
  const method = options.method || 'GET'
  const token = localStorage.getItem('accessToken')
  const body = await raw(path, method, options.data, token)
  if (body.code === 0) return body.data
  if (body.code !== 2002 && body.code !== 3007) throw body
  let newToken
  try {
    newToken = await refresh()
  } catch (err) {
    clearTokens()
    showToast('登录状态已失效，请重新登录')
    router.push('/login')
    throw err
  }
  const retryBody = await raw(path, method, options.data, newToken)
  if (retryBody.code === 0) return retryBody.data
  throw retryBody
}
