<template>
  <div class="field">
    <label v-if="label">{{ label }}</label>
    <input class="input" :value="modelValue" :placeholder="placeholder"
      @input="$emit('update:modelValue', $event.target.value)" />
  </div>
</template>
<script setup>
defineProps({ label: String, modelValue: String, placeholder: String })
defineEmits(['update:modelValue'])
</script>
<style scoped>
.field { margin-bottom: var(--space-3); }
.field label { display: block; font-size: var(--text-xs); font-weight: 700; color: var(--text-secondary); margin-bottom: var(--space-1); }
.input { width: 100%; box-sizing: border-box; }
</style>
