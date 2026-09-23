<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ovl" @click="$emit('update:open', false)" />
      <DialogContent ref="contentRef" class="lbx" aria-label="图片预览">
        <img :src="src" alt="" class="lbx-img" />
        <div class="lbx-close">
          <IconButton name="x" title="关闭" @click="$emit('update:open', false)" />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<script setup>
import { nextTick, ref, watch } from 'vue'
import { animate } from 'motion-v'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent } from 'reka-ui'
import { SPRING_BOUNCE, prefersReducedMotion } from '../styles/motion.js'
import IconButton from './IconButton.vue'

const props = defineProps({
  src: { type: String, required: true },
  open: { type: Boolean, default: false },
})
defineEmits(['update:open'])

const contentRef = ref(null)

watch(
  () => props.open,
  async (v) => {
    if (!v) return
    await nextTick()
    const el = contentRef.value && contentRef.value.$el instanceof Element ? contentRef.value.$el : null
    if (!el || prefersReducedMotion()) return
    el.style.opacity = '0'
    el.style.transform = 'translate(-50%, calc(-50% + 12px)) scale(0.9)'
    animate(el, { opacity: 1, transform: 'translate(-50%, -50%) scale(1)' }, { ...SPRING_BOUNCE })
  }
)
</script>
<style scoped>
.ovl {
  position: fixed;
  inset: 0;
  background: rgba(28, 25, 23, 0.45);
  backdrop-filter: blur(4px);
  z-index: 200;
}
.lbx {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: min(880px, 94vw);
  max-height: 88vh;
  z-index: 201;
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  overflow: visible;
}
.lbx-img {
  display: block;
  max-width: 100%;
  max-height: 88vh;
  border-radius: var(--radius);
  box-shadow: var(--shadow-lift);
  object-fit: contain;
}
.lbx-close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
}
</style>
