import { reactive } from 'vue'

export const toast = reactive({ text: '', visible: false })
let timer = null

export function showToast(text) {
  toast.text = text
  toast.visible = true
  clearTimeout(timer)
  timer = setTimeout(() => {
    toast.visible = false
  }, 2200)
}
