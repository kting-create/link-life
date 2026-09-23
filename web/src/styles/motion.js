// web/src/styles/motion.js
export const SPRING_TAP = { type: 'spring', stiffness: 500, damping: 30 }
export const SPRING_PAGE = { type: 'spring', stiffness: 260, damping: 32 }
export const SPRING_BOUNCE = { type: 'spring', stiffness: 600, damping: 18 }
export const EXIT_MS = 200
export const STAGGER_MS = 60
export const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
