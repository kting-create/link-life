<template>
  <DialogRoot :open="state.open" @update:open="onOpen">
    <DialogPortal>
      <DialogOverlay class="ovl" />
      <DialogContent class="dlg" aria-describedby="confirm-desc">
        <DialogTitle class="dlg-title">{{ state.title }}</DialogTitle>
        <DialogDescription id="confirm-desc" class="dlg-desc">{{ state.message }}</DialogDescription>
        <div class="dlg-acts">
          <button class="btn btn-secondary" @click="resolve(false)">取消</button>
          <button class="btn" :class="state.danger ? 'btn-danger' : 'btn-primary'" @click="resolve(true)">
            {{ state.confirmText || '确认' }}
          </button>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<script setup>
import { reactive } from 'vue'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription } from 'reka-ui'
const state = reactive({ open: false, title: '', message: '', danger: false, confirmText: '' })
let resolver = null
function resolve(v) { state.open = false; resolver?.(v); resolver = null }
function onOpen(v) { if (!v) resolve(false) }
function confirm(opts) {
  Object.assign(state, { open: true, title: opts.title, message: opts.message, danger: !!opts.danger, confirmText: opts.confirmText })
  return new Promise((r) => { resolver = r })
}
defineExpose({ confirm })
</script>
<style scoped>
.ovl { position: fixed; inset: 0; background: rgba(28, 25, 23, 0.35); backdrop-filter: blur(4px); z-index: 200; }
.dlg {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
  width: min(320px, 92vw); z-index: 201;
  background: var(--glass-bg-strong); backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border); border-radius: 20px;
  box-shadow: var(--shadow-lift), var(--glass-highlight); padding: var(--space-5);
  animation: dlg-in 0.4s var(--ease-spring-tap);
}
@keyframes dlg-in { from { opacity: 0; transform: translate(-50%, calc(-50% + 12px)) scale(0.9); } to { opacity: 1; transform: translate(-50%, -50%) scale(1); } }
.dlg-title { margin: 0 0 var(--space-2); font-size: var(--text-md); font-weight: 800; }
.dlg-desc { margin: 0 0 var(--space-5); font-size: var(--text-sm); color: var(--text-secondary); }
.dlg-acts { display: flex; gap: var(--space-3); justify-content: flex-end; }
</style>
