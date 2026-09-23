import { nextTick } from 'vue'
import { prefersReducedMotion } from '../styles/motion.js'

export function navigateWithHero(sourceEl, name, fn) {
  const vt = document.startViewTransition
  if (!vt || prefersReducedMotion()) return Promise.resolve(fn())
  sourceEl.style.viewTransitionName = name
  document.documentElement.dataset.vtHero = '1'
  const t = document.startViewTransition(async () => {
    sourceEl.style.viewTransitionName = ''
    await fn()
    await nextTick()
  })
  return t.finished.finally(() => {
    delete document.documentElement.dataset.vtHero
  })
}
