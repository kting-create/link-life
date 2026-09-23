<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ovl" />
      <DialogContent class="bs" aria-describedby="bs-desc">
        <div class="handle" />
        <DialogDescription id="bs-desc" class="sr-only">操作面板</DialogDescription>
        <slot />
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<script setup>
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogDescription } from 'reka-ui'
defineProps({ open: Boolean })
defineEmits(['update:open'])
</script>
<style scoped>
.ovl { position: fixed; inset: 0; background: rgba(28, 25, 23, 0.35); backdrop-filter: blur(4px); z-index: 200; }
.bs {
  position: fixed; left: 0; right: 0; bottom: 0; margin: 0 auto; max-width: 420px; z-index: 201;
  background: var(--glass-bg-strong); backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border); border-radius: 22px 22px 0 0;
  padding: var(--space-3) var(--space-5) var(--space-5);
  animation: bs-in 0.45s var(--ease-out-soft);
}
@keyframes bs-in { from { transform: translateY(110%); } to { transform: none; } }
.handle { width: 40px; height: 4px; border-radius: 2px; background: var(--border); margin: 0 auto var(--space-4); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
