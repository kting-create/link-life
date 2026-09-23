<template>
  <button class="icon-btn" :title="title" :aria-label="title" @click="$emit('click')">
    <Icon :name="name" />
  </button>
</template>
<script setup>
import Icon from './Icon.vue'
defineProps({ name: { type: String, required: true }, title: { type: String, required: true } })
defineEmits(['click'])
</script>
<style scoped>
.icon-btn {
  width: 40px; height: 40px; border-radius: var(--radius-sm);
  border: 1px solid rgba(194, 65, 12, 0.2); background: rgba(255, 255, 255, 0.75);
  color: var(--primary-deep); display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; transition: transform 0.18s var(--ease-spring-tap), box-shadow 0.2s;
}
.icon-btn:hover { transform: translateY(-2px); box-shadow: var(--shadow-glow); color: var(--primary); }
.icon-btn:active { transform: scale(0.92); }
</style>
