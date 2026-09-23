<!-- web/src/components/Icon.vue -->
<template>
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
    stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'camera'"><path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z" /><circle cx="12" cy="13" r="3" /></template>
    <template v-else-if="name === 'trash'"><path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></template>
    <template v-else-if="name === 'timer'"><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2 2" /><path d="M9 2h6" /></template>
    <template v-else-if="name === 'sparkle'"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z" /></template>
    <template v-else-if="name === 'rotate'"><path d="M3 7v6h6" /><path d="M21 17a9 9 0 0 0-15-6.7L3 13" /></template>
    <template v-else-if="name === 'plus'"><path d="M12 5v14" /><path d="M5 12h14" /></template>
    <template v-else-if="name === 'check'"><path d="M20 6L9 17l-5-5" /></template>
    <template v-else-if="name === 'x'"><path d="M18 6L6 18" /><path d="M6 6l12 12" /></template>
  </svg>
</template>
<script setup>
defineProps({ name: { type: String, required: true } })
</script>
