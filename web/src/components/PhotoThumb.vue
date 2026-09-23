<template>
  <button type="button" class="photo-thumb" :style="{ width: w, height: w }" @click="$emit('open')">
    <img :src="src" alt="" class="photo-thumb-img" />
  </button>
</template>
<script setup>
import { computed } from 'vue'

const props = defineProps({
  src: { type: String, required: true },
  width: { type: [String, Number], default: 72 },
})
defineEmits(['open'])

const w = computed(() => (typeof props.width === 'number' ? `${props.width}px` : props.width))
</script>
<style scoped>
.photo-thumb {
  padding: 0;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--glass-bg);
  cursor: pointer;
  transition: transform 0.18s var(--ease-spring-tap), box-shadow 0.2s ease;
}
.photo-thumb:hover {
  transform: scale(1.08);
  box-shadow: var(--shadow-lift);
}
.photo-thumb:active {
  transform: scale(0.96);
}
.photo-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
