export const sheetStatusText = {
  SHARED: '分享中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已收单',
}

export function claimedCount(items) {
  return (items || []).filter((it) => it.claimantId).length
}

export function sheetBadgeClass(status) {
  return String(status || '') === 'COMPLETED' ? 'is-done' : 'is-cooking'
}
