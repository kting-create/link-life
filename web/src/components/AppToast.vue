<template>
  <Transition name="toast" :css="!reduced" @enter="onEnter">
    <div v-if="toast.visible" class="toast" :class="`toast-${toast.type}`">
      <Icon v-if="toast.type === 'ok'" name="check" />
      <Icon v-if="toast.type === 'err'" name="x" />
      <span>{{ toast.text }}</span>
    </div>
  </Transition>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { animate } from 'motion-v'
import { toast } from '../utils/toast.js'
import Icon from './Icon.vue'
import { SPRING_TAP, prefersReducedMotion } from '../styles/motion.js'

const reduced = ref(false)
onMounted(() => { reduced.value = prefersReducedMotion() })

function onEnter(el, done) {
  if (reduced.value) return done()
  el.style.opacity = '0'
  el.style.transform = 'translateX(-50%) translateY(16px) scale(0.9)'
  animate(el, { opacity: 1, transform: 'translateX(-50%) translateY(0) scale(1)' }, { ...SPRING_TAP, onComplete: done })
}
</script>
<style scoped>
.toast { display: inline-flex; align-items: center; gap: var(--space-2); }
.toast-leave-active { transition: opacity var(--duration-exit), transform var(--duration-exit); }
.toast-leave-to { opacity: 0; transform: translate(-50%, -6px) scale(0.96); }
</style>
