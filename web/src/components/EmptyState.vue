<template>
  <div class="empty">
    <svg class="empty-art" viewBox="0 0 96 96" fill="none">
      <rect x="18" y="30" width="60" height="42" rx="10" stroke="#c2410c" stroke-width="2.5" opacity=".5" />
      <path d="M28 30c0-8 8-14 20-14s20 6 20 14" stroke="#c2410c" stroke-width="2.5" stroke-linecap="round" opacity=".5" />
      <circle cx="48" cy="52" r="6" fill="#ea580c" opacity=".25" />
      <path d="M48 46v-6M42 52h-6M54 52h6M48 58v6" stroke="#ea580c" stroke-width="2" stroke-linecap="round" opacity=".55" />
    </svg>
    <h5 class="empty-title">{{ title }}</h5>
    <p class="muted">{{ desc }}</p>
    <slot name="action" />
  </div>
</template>
<script setup>
defineProps({ title: { type: String, required: true }, desc: { type: String, default: '' } })
</script>
<style scoped>
.empty-title { margin: 0 0 var(--space-1); font-size: var(--text-md); font-weight: 800; }
</style>
