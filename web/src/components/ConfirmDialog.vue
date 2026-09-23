<template>
  <DialogRoot :open="state.open" @update:open="onOpen">
    <DialogPortal>
      <DialogOverlay class="ovl" />
      <DialogContent ref="contentRef" class="dlg" aria-describedby="confirm-desc">
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
import { nextTick, reactive, ref, watch } from 'vue'
import { animate } from 'motion-v'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription } from 'reka-ui'
import { SPRING_BOUNCE, prefersReducedMotion } from '../styles/motion.js'

const state = reactive({ open: false, title: '', message: '', danger: false, confirmText: '' })
const contentRef = ref(null)
let resolver = null
function resolve(v) { state.open = false; resolver?.(v); resolver = null }
function onOpen(v) { if (!v) resolve(false) }
function confirm(opts) {
  Object.assign(state, { open: true, title: opts.title, message: opts.message, danger: !!opts.danger, confirmText: opts.confirmText })
  return new Promise((r) => { resolver = r })
}
watch(() => state.open, async (v) => {
  if (!v) return
  await nextTick()
  const el = contentRef.value && contentRef.value.$el instanceof Element ? contentRef.value.$el : null
  if (!el || prefersReducedMotion()) return
  el.style.opacity = '0'
  el.style.transform = 'translate(-50%, calc(-50% + 12px)) scale(0.9)'
  animate(el, { opacity: 1, transform: 'translate(-50%, -50%) scale(1)' }, { ...SPRING_BOUNCE })
})
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
}
.dlg-title { margin: 0 0 var(--space-2); font-size: var(--text-md); font-weight: 800; }
.dlg-desc { margin: 0 0 var(--space-5); font-size: var(--text-sm); color: var(--text-secondary); }
.dlg-acts { display: flex; gap: var(--space-3); justify-content: flex-end; }
</style>
