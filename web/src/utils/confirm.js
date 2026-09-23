// web/src/utils/confirm.js —— 由 App.vue 中 ConfirmDialog 实例注册 setter
let impl = null
export function registerConfirm(fn) { impl = fn }
export function confirm(opts) { return impl(opts) }
