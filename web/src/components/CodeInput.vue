<template>
  <div class="code-input" @paste="onPaste">
    <input v-for="i in length" :key="i" class="code-cell input" maxlength="1"
      :value="chars[i - 1] || ''" :aria-label="`第 ${i} 位`"
      @input="onInput(i - 1, $event)" @keydown.backspace="onBack(i - 1, $event)" />
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ length: { type: Number, default: 6 }, modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
const cells = ref(props.modelValue.split(''))
const chars = computed(() => cells.value)
watch(() => props.modelValue, (v) => {
  cells.value = String(v ?? '').split('').slice(0, props.length)
})
function sync() { emit('update:modelValue', cells.value.join('')) }
function onInput(i, e) {
  const v = e.target.value.replace(/\D/g, '').slice(-1)
  cells.value[i] = v
  cells.value = [...cells.value]
  sync()
  const next = e.target.parentElement.children[i + 1]
  if (v && next) next.focus()
}
function onBack(i, e) {
  if (!cells.value[i] && i > 0) e.target.parentElement.children[i - 1].focus()
}
function onPaste(e) {
  const t = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, props.length)
  if (t) { e.preventDefault(); cells.value = t.split(''); sync() }
}
</script>
<style scoped>
.code-input { display: flex; gap: var(--space-2); }
.code-cell {
  width: 44px; height: 54px; text-align: center;
  font-size: var(--text-xl); font-weight: 800;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.18s var(--ease-spring-tap);
}
.code-cell:focus { transform: scale(1.06); border-color: var(--primary); box-shadow: var(--shadow-glow); }
</style>
