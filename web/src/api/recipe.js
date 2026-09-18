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
