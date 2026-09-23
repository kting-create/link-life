import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'
import './styles/tokens.css'
import { showToast } from './utils/toast'
import { confirm } from './utils/confirm'

createApp(App).use(router).mount('#app')

if (import.meta.env.DEV) {
  window.__qaShowToast = showToast
  window.__qaConfirm = () => confirm({ title: '删除这道菜？', message: '无法恢复', danger: true })
}
