<template>
  <Transition name="toast">
    <div v-if="toast.visible" class="toast" :class="`toast-${toast.type}`">
      <Icon v-if="toast.type === 'ok'" name="check" />
      <Icon v-if="toast.type === 'err'" name="x" />
      <span>{{ toast.text }}</span>
    </div>
  </Transition>
</template>
<script setup>
import { toast } from '../utils/toast.js'
import Icon from './Icon.vue'
</script>
<style scoped>
.toast { display: inline-flex; align-items: center; gap: var(--space-2); }
.toast-enter-active { animation: t-pop 0.5s var(--ease-spring-tap); }
.toast-leave-active { transition: opacity var(--duration-exit), transform var(--duration-exit); }
.toast-leave-to { opacity: 0; transform: translate(-50%, -6px) scale(0.96); }
@keyframes t-pop { from { opacity: 0; transform: translate(-50%, 16px) scale(0.9); } to { opacity: 1; transform: translateX(-50%); } }
</style>
