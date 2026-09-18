import { request } from './request'

export const getRecipe = (id) => request('/api/recipes/' + id)
export const getVersions = (id) => request('/api/recipes/' + id + '/versions')
export const getByDish = (circleId, dishName) =>
  request('/api/recipes/by-dish?circleId=' + circleId + '&dishName=' +
    encodeURIComponent(dishName))
export const submitFeedback = (id, score, comment) =>
  request('/api/recipes/' + id + '/feedback', { method: 'POST', data: { score, comment } })
export const editRecipe = (id, data) =>
  request('/api/recipes/' + id, { method: 'PUT', data })
export const rollback = (id, version) =>
  request('/api/recipes/' + id + '/rollback', { method: 'POST', data: { version } })
export const listPantry = () => request('/api/me/pantry')
export const addPantry = (data) => request('/api/me/pantry', { method: 'POST', data })
export const deletePantry = (id) => request('/api/me/pantry/' + id, { method: 'DELETE' })
export const listPhotos = (id) => request('/api/recipes/' + id + '/photos')
export const deletePhoto = (photoId) =>
  request('/api/photos/' + photoId, { method: 'DELETE' })
export const analyzePhoto = (photoId) =>
  request('/api/photos/' + photoId + '/analysis', { method: 'POST' })
export const applyPhoto = (photoId) =>
  request('/api/photos/' + photoId + '/apply', { method: 'POST' })
export async function uploadPhoto(id, stepNo, file) {
  const fd = new FormData()
  fd.append('file', file)
  const res = await fetch(`/api/recipes/${id}/steps/${stepNo}/photos`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + localStorage.getItem('accessToken') },
    body: fd,
  })
  const body = await res.json()
  if (body.code === 0) return body.data
  throw body
}
