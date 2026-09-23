<template>
  <div class="skeleton-list" :aria-hidden="true">
    <div v-for="n in rows" :key="n" class="skeleton" :style="{ width: widths[(n - 1) % widths.length] }" />
  </div>
</template>
<script setup>
defineProps({ rows: { type: Number, default: 3 } })
const widths = ['80%', '55%', '65%']
</script>
