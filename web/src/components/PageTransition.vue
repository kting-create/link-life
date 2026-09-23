<!-- web/src/components/PageTransition.vue -->
<template>
  <Transition :name="name" mode="out-in" :css="!reduced" @enter="onEnter" @leave="onLeave">
    <slot />
  </Transition>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { animate } from 'motion-v'
import { SPRING_PAGE, EXIT_MS, prefersReducedMotion } from '../styles/motion.js'
const route = useRoute()
const reduced = ref(false)
onMounted(() => { reduced.value = prefersReducedMotion() })
const name = computed(() => (reduced.value ? 'pg-fade' : `pg-${route.meta.transition || 'fade'}`))
function onEnter(el, done) {
  if (reduced.value) return done()
  const from = route.meta.transition === 'back' ? [-28, 28] : route.meta.transition === 'forward' ? [28, -28] : [0, 0]
  animate(el, { opacity: [0, 1], transform: [`translateX(${from[0]}px) scale(.98)`, 'translateX(0) scale(1)'] },
    { ...SPRING_PAGE, onComplete: done })
}
function onLeave(el, done) {
  if (reduced.value) return done()
  animate(el, { opacity: 0, transform: 'translateX(-16px) scale(.98)' }, { duration: EXIT_MS / 1000, onComplete: done })
}
</script>
<style>
.pg-fade-enter-active, .pg-fade-leave-active { transition: opacity 0.3s ease; }
.pg-fade-enter-from, .pg-fade-leave-to { opacity: 0; }
</style>
