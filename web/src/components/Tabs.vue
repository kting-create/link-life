<template>
  <div class="tabs" role="tablist">
    <span
      class="tab-ind"
      :style="{
        width: `calc(100% / ${items.length || 1})`,
        transform: `translateX(calc(${idx} * 100%))`,
      }"
    />
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      class="tab-btn"
      :class="{ active: modelValue === item.key }"
      role="tab"
      :aria-selected="modelValue === item.key"
      @click="$emit('update:modelValue', item.key)"
    >
      {{ item.label }}
    </button>
  </div>
</template>
<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, required: true },
  modelValue: { type: String, required: true },
})
defineEmits(['update:modelValue'])

const idx = computed(() => {
  const i = props.items.findIndex((it) => it.key === props.modelValue)
  return i < 0 ? 0 : i
})
</script>
<style scoped>
.tabs {
  position: relative;
  display: flex;
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
}
.tab-ind {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  background: var(--grad-flame);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-glow);
  pointer-events: none;
  transition: transform 0.4s var(--ease-out-soft);
}
.tab-btn {
  flex: 1;
  position: relative;
  z-index: 1;
  border: none;
  background: none;
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-secondary);
  transition: color 0.2s ease;
}
.tab-btn.active {
  color: #fff;
}
</style>
