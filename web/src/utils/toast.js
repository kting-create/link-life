import { reactive } from 'vue'
export const toast = reactive({ text: '', type: 'info', visible: false })
let timer = null
export function showToast(text, type = 'info') {
  toast.text = text
  toast.type = type
  toast.visible = true
  clearTimeout(timer)
  timer = setTimeout(() => { toast.visible = false }, 2200)
}
