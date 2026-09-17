import { request } from './request'

export function listNotifications(afterId, size = 20) {
  const params = new URLSearchParams()
  if (afterId) params.set('afterId', String(afterId))
  params.set('size', String(size))
  return request('/api/notifications?' + params.toString())
}

export function fetchUnreadCount() {
  return request('/api/notifications/unread-count')
}

export function markRead(id) {
  return request(`/api/notifications/${id}/read`, { method: 'POST' })
}

export function markAllRead() {
  return request('/api/notifications/read-all', { method: 'POST' })
}
