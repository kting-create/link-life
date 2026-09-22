# Warm Glass UI 升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把双端 UI 升级为「暖玻璃流光」视觉 + 全链路弹簧动效 + 共享组件原语层，并修 3 个 UI backlog。

**Architecture:** 先扩 Web token 层（渐变/玻璃/光晕/间距/字阶/motion tokens）与全局基础类，再建动效基建（motion-v + 路由转场）与组件原语（reka-ui + 玻璃皮肤），随后按功能页→核心页铺开；小程序端同构复制（WXSS + `this.animate` + WXS，不迁 Skyline）；最后修 backlog、做一致性收尾与终验。

**Tech Stack:** Web：Vue 3 + Vite + **motion-v** + **reka-ui** + 原生 View Transitions（渐进增强）；小程序：原生 WXML/WXSS/JS + `this.animate`（≥2.9）+ WXS；后端零改动。

**Spec:** `docs/superpowers/specs/2026-09-22-warm-glass-ui-design.md`

## Global Constraints

- 防廉价感 6 条（spec §2.3）：流光只在 hover/press 触发一次（禁无限循环）；光晕每页 ≤2 处且透明度 ≤20%；玻璃参数全局唯一 `blur(16px)+rgba(255,255,255,.65)+inset 0 1px 0 rgba(255,255,255,.95)`；渐变只用于强调件（页面底 wash 是唯一例外）；动效走 motion token 且尊重 `prefers-reduced-motion`；图标一律 Lucide 风 inline SVG，**禁止 emoji 当图标**。
- 动效只动 `transform`/`opacity`，60fps 预算；降级为淡入淡出/瞬时。
- 文案/字段/事件/跳转逻辑一律不动（**例外**：Task 6 的 3 个 bug 修复）。
- 对比度 ≥4.5:1；focus-visible 焦点环保留。
- 无前端测试框架（不新引）：验证 = `cd web && npm run build` 通过 + 浏览器走查 + 微信开发者工具无报错；后端回归 = `cd server && mvn clean test` 全绿（终验跑）。
- Web 构建产物固定输出 `web-dist/`（`vite.config.js` 的 `outDir` 不动）。
- 提交信息中文 + conventional 前缀（`feat(ui)/style(ui)/fix(ui)`）。
- 引依赖仅限：`motion-v`、`reka-ui`（web）；小程序不引 npm 包。
- 双端 CSS 变量名一致（小程序 rpx 值 ×2）。

## 共享页面铺开检查清单（Task 4/5/9/10 每页默认执行）

1. 页面底用 `--bg-page` wash；信息块全部 `.card`（玻璃）或 `.glass-card` 语义类。
2. 状态徽标用 `.badge-pill` + `is-open/is-claimed/is-cooking/is-done`（渐变档）。
3. 按钮用 `.btn-primary/.btn-secondary/.btn-danger/.btn-accent`；文字链操作换 `<IconButton>`。
4. 空状态换 `<EmptyState>`（插画+标题+副文案+主按钮）；加载态换 `<Skeleton>`。
5. 列表包 `.stagger-list`（入场编排）；可点卡片加 `.card.clickable`（hover 抬升 + press 弹簧）。
6. 表单行换 `<Field>`；间距只用 `--space-1..7`；字号只用 `--text-xs..3xl`。
7. 移除内联硬编码颜色/阶外字号；光晕每页 ≤2。
8. **文案/字段/事件/跳转逻辑不动**。

---

### Task 1: Web token 层与全局基础类

**Files:**
- Modify: `web/src/styles/tokens.css`（全量替换）
- Modify: `web/src/style.css`（全局类改造，保留 19-26 行无障碍/降级块）
- Create: `web/src/styles/motion.js`（弹簧配置常量）

**Interfaces:**
- Produces: 全部 CSS 变量（见下方 tokens.css 全文）；`.glass-card/.btn/.badge-pill/.input/.toast/.empty/.skeleton/.stagger-list` 等全局类；`SPRING_TAP/SPRING_PAGE/SPRING_BOUNCE/EXIT_MS/STAGGER_MS`（motion.js 导出，后续 Task 2/3/5 消费）。

- [ ] **Step 1: 替换 tokens.css 为下文全文**

```css
/* web/src/styles/tokens.css */
:root {
  /* 色 · 陶土橙唯一强调 */
  --primary: #c2410c;
  --primary-hot: #ea580c;
  --primary-deep: #9a3412;
  --primary-weak: #fff1e6;
  --grad-flame: linear-gradient(135deg, #c2410c, #ea580c);
  --accent: #059669;
  --accent-deep: #047857;
  --accent-weak: #ecfdf5;
  --grad-done: linear-gradient(135deg, #047857, #059669);
  --grad-cooking: linear-gradient(135deg, #1d4ed8, #3b82f6);
  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-tertiary: #94a3b8;
  --bg-page: linear-gradient(165deg, #faf6f1 0%, #fff7ed 45%, #f3ece5 100%);
  --bg-card: #ffffff;
  --border: #f2e6e2;
  --danger: #dc2626;
  --danger-hot: #ef4444;
  --grad-danger: linear-gradient(135deg, #dc2626, #ef4444);
  --warning: #f59e0b;
  --ring: #9a3412;
  /* 玻璃（全局唯一参数，页面禁改） */
  --glass-bg: rgba(255, 255, 255, 0.65);
  --glass-bg-strong: rgba(255, 255, 255, 0.88);
  --glass-border: rgba(255, 255, 255, 0.85);
  --glass-blur: blur(16px);
  --glass-highlight: inset 0 1px 0 rgba(255, 255, 255, 0.95);
  /* 光晕/阴影 */
  --shadow-card: 0 8px 28px rgba(154, 52, 18, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.95);
  --shadow-press: 0 1px 2px rgba(154, 52, 18, 0.1);
  --shadow-glow: 0 2px 8px rgba(194, 65, 12, 0.35);
  --shadow-lift: 0 14px 34px rgba(154, 52, 18, 0.14);
  --glow-terracotta: radial-gradient(circle, rgba(194, 65, 12, 0.18), transparent 70%);
  /* 几何 */
  --radius-lg: 24px;
  --radius: 16px;
  --radius-sm: 10px;
  --radius-pill: 999px;
  /* 间距阶梯 */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 24px;
  --space-6: 32px;
  --space-7: 48px;
  /* 字阶 */
  --text-xs: 12px;
  --text-sm: 14px;
  --text-md: 16px;
  --text-lg: 20px;
  --text-xl: 24px;
  --text-2xl: 28px;
  --text-3xl: 32px;
  /* 动效（与 motion.js 数值一致） */
  --duration-exit: 200ms;
  --duration-page: 450ms;
  --stagger-item: 60ms;
  --ease-out-soft: cubic-bezier(0.22, 1, 0.36, 1);
  --ease-spring-tap: cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

- [ ] **Step 2: 创建 motion.js**

```js
// web/src/styles/motion.js
export const SPRING_TAP = { stiffness: 500, damping: 30 }
export const SPRING_PAGE = { stiffness: 260, damping: 32 }
export const SPRING_BOUNCE = { stiffness: 600, damping: 18 }
export const EXIT_MS = 200
export const STAGGER_MS = 60
export const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
```

- [ ] **Step 3: 改造 style.css 全局类**。保留文件 19-26 行（focus-visible 与 reduced-motion 块）原样；将 `.card/.btn/.badge-pill/.input/.toast/.empty` 替换为下述形态，并新增 `.glass-card/.glow-orb/.skeleton/.stagger-list/.card.clickable`：

```css
/* 保留原有 body/html 字体栈（1-17 行）不动 */
/* … 19-26 行 focus-visible 与 @media (prefers-reduced-motion) 原样保留 … */

.card, .glass-card {
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: var(--space-4);
  transition: transform 0.3s var(--ease-out-soft), box-shadow 0.3s ease;
}
.card.clickable { cursor: pointer; }
.card.clickable:hover { transform: translateY(-3px); box-shadow: var(--shadow-lift); }
.card.clickable:active { transform: scale(0.985); box-shadow: var(--shadow-press); }

.glow-orb {
  position: absolute;
  border-radius: 50%;
  background: var(--glow-terracotta);
  filter: blur(8px);
  pointer-events: none;
  z-index: 0;
}

.btn {
  position: relative;
  overflow: hidden;
  border: none;
  border-radius: var(--radius-pill);
  font-weight: 700;
  font-size: var(--text-sm);
  padding: var(--space-2) var(--space-4);
  cursor: pointer;
  transition: transform 0.18s var(--ease-spring-tap), box-shadow 0.2s ease;
}
.btn:hover { transform: scale(1.04); }
.btn:active { transform: scale(0.96); }
.btn-primary { background: var(--grad-flame); color: #fff; box-shadow: var(--shadow-glow); }
.btn-primary::after {
  content: "";
  position: absolute;
  top: 0; left: -80%;
  width: 45%; height: 100%;
  background: linear-gradient(100deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  pointer-events: none;
}
.btn-primary:hover::after { animation: btn-shine 0.7s ease 1; }
@keyframes btn-shine { to { left: 130%; } }
.btn-secondary { background: rgba(255, 255, 255, 0.75); color: var(--primary); border: 1px solid rgba(194, 65, 12, 0.25); box-shadow: var(--glass-highlight); }
.btn-danger { background: var(--grad-danger); color: #fff; box-shadow: 0 4px 14px rgba(220, 38, 38, 0.3); }
.btn-accent { background: var(--grad-done); color: #fff; box-shadow: var(--shadow-glow); }
.btn-ghost { background: rgba(255, 255, 255, 0.7); color: var(--primary); border: 1px solid rgba(194, 65, 12, 0.3); }
.btn-small { padding: var(--space-1) var(--space-3); font-size: var(--text-xs); }
.btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.btn:disabled:hover::after { animation: none; }

.badge-pill {
  display: inline-block;
  font-size: var(--text-xs);
  font-weight: 700;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
}
.badge-pill.is-open { background: rgba(255, 255, 255, 0.75); color: var(--text-secondary); border: 1px solid var(--border); }
.badge-pill.is-claimed { background: var(--grad-flame); color: #fff; box-shadow: var(--shadow-glow); }
.badge-pill.is-cooking { background: var(--grad-cooking); color: #fff; box-shadow: 0 2px 8px rgba(29, 78, 216, 0.3); }
.badge-pill.is-done { background: var(--grad-done); color: #fff; box-shadow: 0 2px 8px rgba(5, 150, 105, 0.3); }

.input {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-sm);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(194, 65, 12, 0.15); outline: none; }

.toast {
  position: fixed;
  bottom: var(--space-6);
  left: 50%;
  transform: translateX(-50%);
  z-index: 300;
  background: var(--glass-bg-strong);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  box-shadow: var(--shadow-lift);
  color: var(--text-primary);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-pill);
  font-size: var(--text-sm);
  font-weight: 600;
}
.toast.toast-ok { color: var(--accent-deep); }
.toast.toast-err { color: var(--danger); }

.empty { text-align: center; padding: var(--space-7) var(--space-4); }
.empty .empty-art { width: 96px; height: 96px; margin: 0 auto var(--space-3); display: block; }

.skeleton {
  height: 14px;
  border-radius: 7px;
  margin-bottom: var(--space-3);
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.6) 25%, rgba(255, 255, 255, 0.95) 50%, rgba(255, 255, 255, 0.6) 75%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s infinite;
}
@keyframes sk-shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }

.stagger-item { opacity: 0; }
.stagger-list.run .stagger-item { animation: st-in 0.5s var(--ease-out-soft) both; animation-delay: calc(var(--i, 0) * var(--stagger-item)); }
@keyframes st-in { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: none; } }

.page-title { font-size: var(--text-lg); font-weight: 800; letter-spacing: -0.01em; }
.muted { font-size: var(--text-sm); color: var(--text-secondary); }
```

同时把全站 `11px/13px/18px` 等阶外值（如 `App.vue:145` 的 11px、`style.css:134` 的 13px）改为 `--text-xs/--text-sm`（后续 Task 4/5 页面里逐处替换）。

- [ ] **Step 4: 构建验证**

Run: `cd web && npm install && npm run build`
Expected: 构建成功输出 `web-dist/`，无报错。

- [ ] **Step 5: 走查**

浏览器 `npm run dev`：/circles 底为暖渐变 wash、卡片玻璃质感带顶高光、主按钮 hover 有一次性流光、`prefers-reduced-motion` 开启后流光/骨架动画停止。

- [ ] **Step 6: Commit**

```bash
git add web/src/styles/tokens.css web/src/styles/motion.js web/src/style.css
git commit -m "feat(ui): Web 暖玻璃 token 层与全局基础类"
```

---

### Task 2: Web 动效基建（motion-v + 路由转场 + 导航滑块）

**Files:**
- Modify: `web/package.json`（新增 `motion-v` 依赖）
- Modify: `web/src/router.js`（meta.depth + to.meta.transition）
- Create: `web/src/components/PageTransition.vue`
- Modify: `web/src/App.vue`（router-view 包转场、去掉 fullPath key、TabBar 滑块、导航选中态）
- Create: `web/src/components/Icon.vue`（Lucide 风 SVG sprite，后续页面共用）

**Interfaces:**
- Consumes: `SPRING_PAGE/EXIT_MS/prefersReducedMotion`（Task 1 motion.js）
- Produces: `<PageTransition>`（默认 slot，读 `route.meta.depth` 自动选方向）；`routes[].meta.depth`（0=login/share，1=主 tab，2=详情，3=沉浸如 cook）；`<Icon name="camera|trash|timer|sparkle|rotate|plus|home|bell|user">`。

- [ ] **Step 1: 安装 motion-v**

Run: `cd web && npm install motion-v`
Expected: dependencies 出现 `"motion-v"`。

- [ ] **Step 2: router.js 给路由打 depth，并在跳转时写入方向**

在 `routes` 每条 meta 增加 depth：`/login`、`/s/:token` → `depth: 0`；`/circles`、`/notifications`、`/me`、`/pantry` → `depth: 1`；`/sheets/:id`、`/recipes/generate`、`/recipes/:id` → `depth: 2`；`/recipes/:id/cook` → `depth: 3`。在 `router.beforeEach` 末尾补充方向计算：

```js
router.afterEach((to, from) => {
  const toDepth = to.meta.depth ?? 1
  const fromDepth = from.meta.depth ?? 1
  to.meta.transition = toDepth === fromDepth ? 'fade' : toDepth > fromDepth ? 'forward' : 'back'
})
```

- [ ] **Step 3: 创建 PageTransition.vue**（按方向动 transform+opacity；reduced-motion 降级纯淡入）

```vue
<!-- web/src/components/PageTransition.vue -->
<template>
  <Transition :name="name" mode="out-in" :css="!reduced" @enter="onEnter" @leave="onLeave">
    <slot />
  </Transition>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { animate } from 'motion-v'
import { SPRING_PAGE, EXIT_MS, prefersReducedMotion } from '../styles/motion.js'
const route = useRoute()
const reduced = ref(false)
onMounted(() => { reduced.value = prefersReducedMotion() })
const name = computed(() => (reduced.value ? 'pg-fade' : `pg-${route.meta.transition || 'fade'}`))
function onEnter(el, done) {
  if (reduced.value) return done()
  const from = route.meta.transition === 'back' ? [-28, 28] : route.meta.transition === 'forward' ? [28, -28] : [0, 0]
  animate(el, { opacity: [0, 1], transform: [`translateX(${from[0]}px) scale(.98)`, 'translateX(0) scale(1)'] },
    { ...SPRING_PAGE, onComplete: done })
}
function onLeave(el, done) {
  if (reduced.value) return done()
  animate(el, { opacity: 0, transform: 'translateX(-16px) scale(.98)' }, { duration: EXIT_MS / 1000, onComplete: done })
}
</script>
<style>
.pg-fade-enter-active, .pg-fade-leave-active { transition: opacity 0.3s ease; }
.pg-fade-enter-from, .pg-fade-leave-to { opacity: 0; }
</style>
```

- [ ] **Step 4: 创建 Icon.vue**（统一图标出口，禁止再散写 SVG）

```vue
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
```

- [ ] **Step 5: App.vue 三处改造**

1. `<router-view :key="route.fullPath" />` 改为：

```vue
<main class="main">
  <PageTransition>
    <router-view :key="route.name || route.path" />
  </PageTransition>
</main>
```

（`import PageTransition from './components/PageTransition.vue'`；`route.fullPath` 的 key 是白闪根源，改为 name/path 后同页参数变化也走转场。）

2. 底栏 `.tabbar-item.router-link-active` 增加滑块指示（在 `.tabbar` 里加一个绝对定位 `span.tabbar-ind`，用 `transform: translateX(calc(var(--tab-i) * 100%))`，三个 item 的 active 状态通过 `watch(route)` 设置 `--tab-i` 0/1/2；未选中色改 `--text-tertiary`）。
3. 顶栏 `.nav-link` 选中态同样加 1px 底部渐变条 + `transition`。

- [ ] **Step 6: 构建 + 走查**

Run: `cd web && npm run build` → 预期成功。
走查：/circles → /sheets/1 前进右入、返回左入、tab 切换淡入；无白闪；reduced-motion 下纯淡入；TabBar 指示条滑动。

- [ ] **Step 7: Commit**

```bash
git add web/package.json web/package-lock.json web/src/router.js web/src/components/PageTransition.vue web/src/components/Icon.vue web/src/App.vue
git commit -m "feat(ui): Web 动效基建(motion-v/路由转场/导航滑块/图标组件)"
```

---

### Task 3: Web 组件原语 I（Toast 三态 / ConfirmDialog / BottomSheet / Field / CodeInput / IconButton）

**Files:**
- Modify: `web/src/utils/toast.js`（type 支持）
- Modify: `web/src/App.vue`（Toast 渲染换 `<AppToast>`）
- Create: `web/src/components/AppToast.vue`
- Create: `web/src/components/ConfirmDialog.vue`
- Create: `web/src/components/BottomSheet.vue`
- Create: `web/src/components/Field.vue`
- Create: `web/src/components/CodeInput.vue`
- Create: `web/src/components/IconButton.vue`

**Interfaces:**
- Consumes: `SPRING_TAP/SPRING_BOUNCE/EXIT_MS`（motion.js）、`<Icon>`（Task 2）
- Produces:
  - `showToast(text, type = 'info')`（type ∈ info|ok|err）
  - `confirm({ title, message, confirmText, danger }) => Promise<boolean>`（ConfirmDialog.vue 内导出，或经 `utils/confirm.js` 包装）
  - `<BottomSheet :open @update:open>`（default slot 内容）
  - `<Field label v-model placeholder>`；`<CodeInput :length="6" v-model>`（v-model 为完整字符串）
  - `<IconButton name="camera" title="拍照" @click>`（title 必填，tooltip/aria）

- [ ] **Step 1: toast.js 增加 type，写 AppToast.vue，替换 App.vue 内联 toast**

```js
// web/src/utils/toast.js
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
```

```vue
<!-- web/src/components/AppToast.vue -->
<template>
  <Transition name="toast">
    <div v-if="toast.visible" class="toast" :class="`toast-${toast.type}`">
      <Icon v-if="toast.type === 'ok'" name="check" />
      <Icon v-if="toast.type === 'err'" name="x" />
      <span>{{ toast.text }}</span>
    </div>
  </Transition>
</template>
<script setup>
import { toast } from '../utils/toast.js'
import Icon from './Icon.vue'
</script>
<style scoped>
.toast { display: inline-flex; align-items: center; gap: var(--space-2); }
.toast-enter-active { animation: t-pop 0.5s var(--ease-spring-tap); }
.toast-leave-active { transition: opacity var(--duration-exit), transform var(--duration-exit); }
.toast-leave-to { opacity: 0; transform: translate(-50%, -6px) scale(0.96); }
@keyframes t-pop { from { opacity: 0; transform: translate(-50%, 16px) scale(0.9); } to { opacity: 1; transform: translateX(-50%); } }
</style>
```

- [ ] **Step 2: ConfirmDialog.vue（reka-ui Dialog + 玻璃皮肤 + confirm() 命令式 API）**

```bash
cd web && npm install reka-ui
```

```vue
<!-- web/src/components/ConfirmDialog.vue -->
<template>
  <DialogRoot :open="state.open" @update:open="onOpen">
    <DialogPortal>
      <DialogOverlay class="ovl" />
      <DialogContent class="dlg" aria-describedby="confirm-desc">
        <DialogTitle class="dlg-title">{{ state.title }}</DialogTitle>
        <DialogDescription id="confirm-desc" class="dlg-desc">{{ state.message }}</DialogTitle>
        <div class="dlg-acts">
          <button class="btn btn-secondary" @click="resolve(false)">取消</button>
          <button class="btn" :class="state.danger ? 'btn-danger' : 'btn-primary'" @click="resolve(true)">
            {{ state.confirmText || '确认' }}
          </button>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<script setup>
import { reactive } from 'vue'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription } from 'reka-ui'
const state = reactive({ open: false, title: '', message: '', danger: false, confirmText: '' })
let resolver = null
function resolve(v) { state.open = false; resolver?.(v); resolver = null }
function onOpen(v) { if (!v) resolve(false) }
export function confirm(opts) {
  Object.assign(state, { open: true, title: opts.title, message: opts.message, danger: !!opts.danger, confirmText: opts.confirmText })
  return new Promise((r) => { resolver = r })
}
</script>
<style scoped>
.ovl { position: fixed; inset: 0; background: rgba(28, 25, 23, 0.35); backdrop-filter: blur(4px); z-index: 200; }
.dlg {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
  width: min(320px, 92vw); z-index: 201;
  background: var(--glass-bg-strong); backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border); border-radius: 20px;
  box-shadow: var(--shadow-lift), var(--glass-highlight); padding: var(--space-5);
  animation: dlg-in 0.4s var(--ease-spring-tap);
}
@keyframes dlg-in { from { opacity: 0; transform: translate(-50%, calc(-50% + 12px)) scale(0.9); } to { opacity: 1; transform: translate(-50%, -50%) scale(1); } }
.dlg-title { margin: 0 0 var(--space-2); font-size: var(--text-md); font-weight: 800; }
.dlg-desc { margin: 0 0 var(--space-5); font-size: var(--text-sm); color: var(--text-secondary); }
.dlg-acts { display: flex; gap: var(--space-3); justify-content: flex-end; }
</style>
```

创建 `web/src/utils/confirm.js` 转发（页面只 `import { confirm } from '../utils/confirm.js'`）：

```js
// web/src/utils/confirm.js —— 由 App.vue 中 ConfirmDialog 实例注册 setter
let impl = null
export function registerConfirm(fn) { impl = fn }
export function confirm(opts) { return impl(opts) }
```

App.vue 挂载 `<ConfirmDialog ref="cdRef" />` 并在 `onMounted` 里 `registerConfirm(cdRef.value.confirm)`（ConfirmDialog 暴露 `defineExpose({ confirm })`，内部状态机同上；实现时把 `confirm` 改为组件方法并在 setup 顶部 `const cdRef = ref()` 挂到 App 根部）。`window.confirm` 全部改走该 API。

- [ ] **Step 3: BottomSheet.vue**（同 reka-ui Dialog，皮肤为底部上滑）

```vue
<!-- web/src/components/BottomSheet.vue -->
<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ovl" />
      <DialogContent class="bs" aria-describedby="bs-desc">
        <div class="handle" />
        <DialogDescription id="bs-desc" class="sr-only">操作面板</DialogDescription>
        <slot />
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<script setup>
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogDescription } from 'reka-ui'
defineProps({ open: Boolean })
defineEmits(['update:open'])
</script>
<style scoped>
.ovl { position: fixed; inset: 0; background: rgba(28, 25, 23, 0.35); backdrop-filter: blur(4px); z-index: 200; }
.bs {
  position: fixed; left: 0; right: 0; bottom: 0; margin: 0 auto; max-width: 420px; z-index: 201;
  background: var(--glass-bg-strong); backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border); border-radius: 22px 22px 0 0;
  padding: var(--space-3) var(--space-5) var(--space-5);
  animation: bs-in 0.45s var(--ease-out-soft);
}
@keyframes bs-in { from { transform: translateY(110%); } to { transform: none; } }
.handle { width: 40px; height: 4px; border-radius: 2px; background: var(--border); margin: 0 auto var(--space-4); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
```

- [ ] **Step 4: Field.vue / CodeInput.vue / IconButton.vue**

```vue
<!-- web/src/components/Field.vue -->
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
```

```vue
<!-- web/src/components/CodeInput.vue -->
<template>
  <div class="code-input" @paste="onPaste">
    <input v-for="i in length" :key="i" class="code-cell input" maxlength="1"
      :value="chars[i - 1] || ''" :aria-label="`第 ${i} 位`"
      @input="onInput(i - 1, $event)" @keydown.backspace="onBack(i - 1, $event)" />
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
const props = defineProps({ length: { type: Number, default: 6 }, modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
const cells = ref(props.modelValue.split(''))
const chars = computed(() => cells.value)
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
```

```vue
<!-- web/src/components/IconButton.vue -->
<template>
  <button class="icon-btn" :title="title" :aria-label="title" @click="$emit('click')">
    <Icon :name="name" />
  </button>
</template>
<script setup>
import Icon from './Icon.vue'
defineProps({ name: { type: String, required: true }, title: { type: String, required: true } })
defineEmits(['click'])
</script>
<style scoped>
.icon-btn {
  width: 40px; height: 40px; border-radius: var(--radius-sm);
  border: 1px solid rgba(194, 65, 12, 0.2); background: rgba(255, 255, 255, 0.75);
  color: var(--primary-deep); display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; transition: transform 0.18s var(--ease-spring-tap), box-shadow 0.2s;
}
.icon-btn:hover { transform: translateY(-2px); box-shadow: var(--shadow-glow); color: var(--primary); }
.icon-btn:active { transform: scale(0.92); }
</style>
```

- [ ] **Step 5: 构建验证 + 弹层走查**

Run: `cd web && npm run build` → 成功。
走查：临时在任一页面触发 `confirm({ title: '删除这道菜？', message: '无法恢复', danger: true })` 看弹簧弹层与 Promise 返回；`showToast('已认领', 'ok')` 三态图标正确；CodeInput 六格粘贴/退格连跳。

- [ ] **Step 6: Commit**

```bash
git add web/package.json web/package-lock.json web/src/utils/ web/src/components/AppToast.vue web/src/components/ConfirmDialog.vue web/src/components/BottomSheet.vue web/src/components/Field.vue web/src/components/CodeInput.vue web/src/components/IconButton.vue web/src/App.vue
git commit -m "feat(ui): Web 组件原语(Toast/ConfirmDialog/BottomSheet/Field/CodeInput/IconButton)"
```

---

### Task 4: Web 组件原语 II + 功能页铺开 A（Login/Circles/Notifications/Profile/Pantry/ShareView）

**Files:**
- Create: `web/src/components/EmptyState.vue`、`web/src/components/Skeleton.vue`、`web/src/components/Tabs.vue`、`web/src/components/PhotoThumb.vue`、`web/src/components/Lightbox.vue`
- Modify: `web/src/views/Login.vue`、`Circles.vue`、`Notifications.vue`、`Profile.vue`、`Pantry.vue`、`ShareView.vue`

**Interfaces:**
- Consumes: Task 1~3 全部全局类与原语
- Produces: `<EmptyState title desc action>`（action slot 放主按钮；内置线性插画：空篮子 SVG，见 spec §3 标本）；`<Skeleton :rows="3">`；`<Tabs :items="[{key,label}]" v-model>`（滑块指示）；`<PhotoThumb src @open>` + `<Lightbox :src :open @update:open>`。

- [ ] **Step 1: 写 5 个原语组件**（EmptyState 空篮子插画 SVG 用 spec §3 标本间那段 `viewBox="0 0 96 96"` 的路径；Skeleton/Tabs/PhotoThumb/Lightbox 按 spec §3 样式与 Task 3 同款弹簧写法）。

EmptyState 骨架（插画路径内联）：

```vue
<!-- web/src/components/EmptyState.vue -->
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
```

Skeleton / Tabs / PhotoThumb / Lightbox 按以下接口实现（样式消费 Task 1 token，动画用 `--ease-spring-tap`）：

- `<Skeleton :rows="3">`：渲染 rows 条 `.skeleton`，宽度 80%/55%/65% 轮换。
- `<Tabs :items v-model>`：`.tab-demo` 结构（指示条 `transform: translateX(calc(idx * 100%))`，`transition: transform .4s var(--ease-out-soft)`）。
- `<PhotoThumb src width="72" @open>`：hover `scale(1.08)` 弹簧。
- `<Lightbox :src :open>`：`reka-ui DialogRoot` 全屏遮罩，图片 `dlg-in` 同款居中缩放入场。

- [ ] **Step 2: Login.vue**——绑定码单输入换 `<CodeInput :length="6" v-model="code">`（提交逻辑/接口不动）；登录卡加 `position:relative` + 一枚 `.glow-orb`（`width:180px;height:180px;top:-40px;right:-40px`）；标题换 `--text-2xl`。走查「防廉价感」：光晕仅 1 处。

- [ ] **Step 3: Circles.vue**——三块列表（圈子卡/清单卡/成员）每条包 `.stagger-item`，容器 `.stagger-list` 在数据加载完成后加 `run` class（`onMounted`/fetch 回调里 `listRun.value = true`，style 绑定 `--i: index`）；圈子/清单卡加 `.card.clickable`（点击跳转处）；建圈/建单表单裸 input 换 `<Field>`；「＋ 发起点单」按钮确认为 `.btn-primary` + `<Icon name="plus">`（去掉 emoji ＋）。

- [ ] **Step 4: Notifications.vue**——通知卡 `.stagger-item` + `.card.clickable`；未读竖条保留但色改 `--grad-flame` 微条；「加载更多」按钮 `.btn-secondary`。

- [ ] **Step 5: Profile.vue**——口味画像卡玻璃化 + 单枚光晕；taste-tag 胶囊复用 `.badge-pill.is-open` 形态；登出按钮 `.btn-danger`（点击逻辑暂不动，Task 6 改幂等）；nav-link 行 hover 背景 `var(--primary-weak)` 过渡。

- [ ] **Step 6: Pantry.vue**——列表 `.stagger-item`；临期/过期徽标升 token（`.badge-pill` 新增 `.is-warn{background:#fef3c7;color:#b45309}`、`.is-expired{background:#fee2e2;color:var(--danger)}`，补进 style.css）；select 套 `.input` 保留。

- [ ] **Step 7: ShareView.vue**——徽标同上 token 化（SHARED 用 `.is-cooking` 同渐变蓝）；底部提示条换玻璃 `.glass-card` 固定条；整页光晕 ≤1。

- [ ] **Step 8: 构建 + 走查**

Run: `cd web && npm run build` → 成功。
走查：六页全部玻璃卡 + stagger 入场；无 emoji（grep `web/src/views` 无 `＋|★`）；间距只用 `--space-*`；375px 宽不破版。

- [ ] **Step 9: Commit**

```bash
git add web/src/components/EmptyState.vue web/src/components/Skeleton.vue web/src/components/Tabs.vue web/src/components/PhotoThumb.vue web/src/components/Lightbox.vue web/src/style.css web/src/views/
git commit -m "style(ui): Web 组件原语 II + 功能页暖玻璃铺开"
```

---

### Task 5: Web 核心页铺开 B（SheetDetail/RecipeDetail/CookMode/RecipeGenerate）+ 共享元素

**Files:**
- Modify: `web/src/views/SheetDetail.vue`、`RecipeDetail.vue`、`CookMode.vue`、`RecipeGenerate.vue`
- Modify: `web/src/style.css`（如需补 `.hero` 头卡类）

**Interfaces:**
- Consumes: Task 1~4 全部；`SPRING_PAGE/SPRING_BOUNCE`（motion.js）
- Produces: SheetDetail 头卡与列表卡带 `style="view-transition-name: sheet-hero"`（RecipeDetail 为 `recipe-hero`）；CookMode 切步方向函数 `goStep(dir)`（Task 6 复用做边界 toast）。

- [ ] **Step 1: SheetDetail.vue**——头卡 `.card` + `--radius-lg` + 单枚光晕 + `view-transition-name: sheet-hero`；菜品卡 `.stagger-item` + `.card.clickable`；认领/烹饪/完成按钮换 `.btn-accent/.btn-primary/.btn-secondary` + `<Icon>`（「删除」→ `<IconButton name="trash" title="删除">`）；`window.confirm` 换 `confirm({...danger:true})`（Task 3 API）；徽标 `.badge-pill` 四态（去掉页内硬编码色块）。

- [ ] **Step 2: RecipeDetail.vue**——头卡 `view-transition-name: recipe-hero` + 单枚光晕；步骤列表编号排版（`01/02/03` 用 `--text-xs` + `--primary`，步骤文案 `--text-md`，吸收 Editorial 细节）；星评按钮点按触发 `animate(el, { scale: [1, 1.45, 1], rotate: [0, -8, 0] }, SPRING_BOUNCE)`（包 `v-for` 的 `<motion.button>` 或裸元素 + `animate()` 均可，reduced-motion 时跳过）；「回滚到此版」「删除」→ `<IconButton name="rotate|trash">`；编辑行换 `<Field>`；照片用 `<PhotoThumb>` + `<Lightbox>`；吸底 CTA `.btn-primary`。

- [ ] **Step 3: CookMode.vue**——大字步骤包 Transition，切步方向动效：

```js
import { animate } from 'motion-v'
import { SPRING_PAGE, prefersReducedMotion } from '../styles/motion.js'
const stepDir = ref(1)
function goStep(dir) {
  const el = stepEl.value
  const target = current.value + dir
  if (target < 0) { showToast('已是第一步'); return }
  if (target >= steps.value.length) { showToast('已是最后一步'); return }
  stepDir.value = dir
  current.value = target
  if (prefersReducedMotion()) return
  animate(el,
    { opacity: [0, 1], transform: [`translateX(${dir * 100}%)`, 'translateX(0)'] },
    { ...SPRING_PAGE })
}
```

（`enterStep`/手势回调改调 `goStep(dir)`；进度条 `transition: width .5s var(--ease-out-soft)`；倒计时归零加一次 `animate(scale 1→1.2→1)` 脉冲；`+ 拍照`→`<IconButton name="camera" title="拍照">`；AI 建议卡统一 `.glass-card`；「问 AI」配 `<Icon name="sparkle">`；首/末步禁用态保留但边界点击已有 toast——首末按钮 `disabled` 改为不 disable 而是点击给 toast，与 `goStep` 边界分支一致。）

- [ ] **Step 4: RecipeGenerate.vue**——流式盒补光标（`@keyframes blink` 1s，与小程序同款）；文本 delta 逐段包 `.seg`（`animation: st-in .3s var(--ease-out-soft) both`）；等待态保留呼吸；「生成」CTA `.btn-primary` + `<Icon name="sparkle">`。（参数修复在 Task 6。）

- [ ] **Step 5: 构建 + 走查**

Run: `cd web && npm run build` → 成功。
走查：清单卡→详情头卡在支持 View Transitions 的 Chrome 中形变共享元素，Safari/不支持时退化 Task 2 转场；烹饪左右切步方向正确、末步 toast；星评弹跳；AI 流式有光标。

- [ ] **Step 6: Commit**

```bash
git add web/src/views/SheetDetail.vue web/src/views/RecipeDetail.vue web/src/views/CookMode.vue web/src/views/RecipeGenerate.vue web/src/style.css
git commit -m "style(ui): Web 核心页暖玻璃铺开(共享元素/烹饪切步/星评/流式)"
```

---

### Task 6: UI backlog 修复 ×3（PROJECT-STATUS §5）

**Files:**
- Modify: `web/src/views/RecipeGenerate.vue`（取参契约）
- Modify: `web/src/views/RecipeDetail.vue`（迭代入口传参对齐）
- Modify: `web/src/views/CookMode.vue`（若边界 toast 未在 Task 5 完成则在此补齐）
- Modify: `web/src/App.vue`（logout 幂等）

**Interfaces:**
- Produces: `RecipeGenerate` 支持两套入参：A) `query: { circleId, dishName }`（新建生成）；B) `query: { recipeId, comment }`（迭代）；二者都缺时才判失败。`logout()` 幂等（并发只跑一次）。

- [ ] **Step 1: RecipeGenerate 取参契约**。在解析处并集读取：

```js
const route = useRoute()
const circleId = computed(() => route.query.circleId || null)
const dishName = computed(() => route.query.dishName || null)
const recipeId = computed(() => route.query.recipeId || null)
const comment = computed(() => route.query.comment || '')
const isIterate = computed(() => !!recipeId.value)
// 校验：!isIterate && !(circleId && dishName) 才算缺参
```

缺参分支改为**静默** `router.replace('/circles')` + `showToast('缺少菜谱参数', 'err')` **只 toast 一次**（现状是先闪错误再跳转的重复提示，跳转前不再有中间态 toast）。`startGenerate()` 按 `isIterate` 分支调「迭代」或「生成」接口（接口封装不动，只统一入口参数）。

- [ ] **Step 2: RecipeDetail 迭代入口传参对齐**。跳转换：

```js
router.push({ path: '/recipes/generate', query: { recipeId: recipe.id, comment: draftComment.value } })
```

- [ ] **Step 3: logout 幂等**。App.vue：

```js
let loggingOut = false
async function logout() {
  if (loggingOut) return
  loggingOut = true
  try {
    /* 原有清理逻辑：localStorage.removeItem('accessToken'/'refreshToken'/'user') 与 reLaunch 等价跳转 */
    await router.replace('/login')
  } finally {
    loggingOut = false
  }
}
```

（双 `reLaunch`/双跳转的旧调用合并为单次 `router.replace`；若原实现含 `location.reload` 之类的第二次跳转，删除。）

- [ ] **Step 4: 末步 skip 反馈**（确认 Task 5 的 `goStep` 边界 toast 在按钮与手势两条路径都生效；手势路径回调里同样走 `goStep`，不允许绕过）。

- [ ] **Step 5: 构建 + 按复现步骤验证**

Run: `cd web && npm run build`
验证（对应 PROJECT-STATUS §5 原描述）：
1. RecipeDetail 点「问 AI 迭代」→ 生成页直接开始迭代，无「缺少菜谱参数」闪烁。
2. 烹饪最后一步点「继续」/右滑 → toast「已是最后一步」。
3. 快速连点两次「退出」→ 只发生一次跳转/清理。

- [ ] **Step 6: Commit**

```bash
git add web/src/views/RecipeGenerate.vue web/src/views/RecipeDetail.vue web/src/views/CookMode.vue web/src/App.vue
git commit -m "fix(ui): 修 RecipeGenerate 取参闪烁/末步无反馈/logout 双跳转"
```

---

### Task 7: 小程序 token 层与全局类（app.wxss）

**Files:**
- Modify: `miniapp/app.wxss`（全量升级，rpx = web px ×2）

**Interfaces:**
- Produces: 与 Web 同名 token（`--primary-hot/--grad-flame/--glass-*/--shadow-glow/--glow-terracotta/--space-*/--text-*/--duration-exit/--stagger-item/--ease-out-soft/--ease-spring-tap`）+ 全局类 `.card/.btn` 族 `.badge-pill` 族 `.input/.empty/.skeleton/.stagger-list/.glow-orb/.glass-card`。

- [ ] **Step 1: 替换 app.wxss 的 `page` token 块**，值按 Task 1 映射（如 `--radius-sm: 20rpx`、`--space-4: 32rpx`、`--text-md: 32rpx`、`--glass-blur: blur(32rpx)`——注意小程序 `backdrop-filter` 部分基础库不支持，玻璃面同时给 `background: rgba(255,255,255,.82)` 兜底不透明度，样式注释写明）。页面底 `background` 用 `linear-gradient(165deg, #faf6f1, #fff7ed, #f3ece5)`（page 背景支持渐变）。

- [ ] **Step 2: 全局类升级**（对照 Task 1 style.css 逐类等价重写为 rpx）：`.card` 换玻璃三件套；`.btn` 加 `overflow:hidden` + 一次性流光（WXSS 动画 `@keyframes btn-shine`，**绑定在 `hover-class` 触发的类上**而非常驻）；`.btn` 补齐 `.btn-secondary/.btn-danger/.btn-accent/.btn-ghost.button-hover` 按压态（现状只有 primary 有）；`.badge-pill` 四态渐变 + 新增 `.is-warn/.is-expired`；`.skeleton` shimmer；`.stagger-list.run .stagger-item` 入场（`animation-delay: calc(var(--i) * 60ms)`）。

- [ ] **Step 3: 把各页散落的阶外字号（22/26/30/36rpx 等）与状态色硬编码（`sheet-list.wxss:49-61`、`sheet-detail.wxss:108-117`、`pantry.wxss:10-14` 等 9 处）登记到清单，Task 9/10 逐页替换**。本任务先保证全局类与 token 就位。

- [ ] **Step 4: 开发者工具验证**：打开 `miniapp/` 无报错；抽看 login/circle 页玻璃卡渲染正常（兜底白 82% 下依然可读）。

- [ ] **Step 5: Commit**

```bash
git add miniapp/app.wxss
git commit -m "feat(ui): 小程序暖玻璃 token 层与全局类(按压态补全)"
```

---

### Task 8: 小程序组件原语（miniapp/components/）

**Files:**
- Create: `miniapp/components/`：`field/`（field.wxml/wxss/js/json）、`code-input/`、`icon-btn/`、`empty-state/`、`skeleton/`、`tabs/`、`confirm-dialog/`、`bottom-sheet/`、`photo-thumb/`
- Create: `miniapp/utils/toast.js`（玻璃 toast 封装）
- Modify: `miniapp/app.json`（`usingComponents` 不放全局，逐页按需注册；本任务不改 app.json，仅建组件）

**Interfaces:**
- Consumes: Task 7 token/全局类
- Produces: 与 Web 同名同 props：`field`（properties: `label/value/placeholder`，triggerEvent `input`）、`code-input`（`length=6`，事件 `change` payload 整串）、`icon-btn`（`name/title`，事件 `tap`；name→本地 SVG sprite 映射与 web/Icon.vue 同 8 个图标，SVG 用 `<image>` 不行——用 WXML 内联 `<svg>` 不支持，故小程序图标走 **iconfont/base64 SVG background-image** class 方案：`.icon-camera` 等 8 个 class，background 为 data-URI SVG）、`empty-state`（`title/desc` + slot `action`，插画同 web 路径）、`skeleton`（`rows`）、`tabs`（`items` 数组 + `current`，事件 `change`）、`confirm-dialog`（method `show({title,message,danger,confirmText})` 返回 Promise，视觉同 web）、`bottom-sheet`（`show/hide`）、`photo-thumb`（`src`，事件 `open`）；`utils/toast.js` 导出 `showGlassToast(text, type='info')`（自定义 glass-toast 组件或 `wx.showToast` 图标差异兜底——统一用页面内嵌 `<view class="toast">` 由 app 级单例组件更稳：实现为 `components/glass-toast/` + `getApp().toastShow`，页面在 wxml 根部挂 `<glass-toast id="gtoast" />`）。

- [ ] **Step 1: 建 `icon-btn` 组件**（8 个图标 data-URI SVG class + hover-class 按压 scale）。data-URI 示例（trash）：

```css
.icon-trash { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%239a3412' stroke-width='2' stroke-linecap='round'%3E%3Cpath d='M3 6h18'/%3E%3Cpath d='M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6'/%3E%3Cpath d='M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'/%3E%3C/svg%3E"); }
```

（其余 7 个同法转义。）

- [ ] **Step 2: 建 `field`/`code-input`/`empty-state`/`skeleton`/`tabs`/`photo-thumb`**（视觉与 Task 4 web 对应组件一致，rpx；tabs 指示条 `transition: transform .4s var(--ease-out-soft)`）。

- [ ] **Step 3: 建 `confirm-dialog`/`bottom-sheet`/`glass-toast`**（遮罩 + `animation: dlg-in .4s var(--ease-spring-tap)` / `bs-in .45s var(--ease-out-soft)`；glass-toast 弹跳进 `t-pop` 上浮出）。`utils/toast.js` 包装：

```js
// miniapp/utils/toast.js
let host = null
export function bindToast(page, id) { host = page.selectComponent(id) }
export function showGlassToast(text, type = 'info') {
  if (host) host.show(text, type)
  else wx.showToast({ title: text, icon: 'none' })
}
```

- [ ] **Step 4: 开发者工具验证**：造一个临时 demo 页引用全部组件（或用任一现有页预挂），确认渲染/事件/弹层 Promise 正常，然后移除临时挂载。组件 `virtualHost` 按需开启（field/icon-btn 开）。

- [ ] **Step 5: Commit**

```bash
git add miniapp/components/ miniapp/utils/toast.js
git commit -m "feat(ui): 小程序组件原语(与 web 同构)"
```

---

### Task 9: 小程序功能页铺开（login/circle/order-create/sheet-list/notifications/profile/pantry）

**Files:**
- Modify: `miniapp/pages/login/*`、`circle/*`、`order-create/*`、`sheet-list/*`、`notifications/*`、`profile/*`、`pantry/*`（wxml/wxss/js 的 json 注册组件）

**Interfaces:**
- Consumes: Task 7 全局类、Task 8 组件
- Produces: 七页完成「共享页面铺开检查清单」；页面 onShow 首帧对 `.stagger-list` 数据回填后加 `run`（`this.setData({ listRun: true })`）。

- [ ] **Step 1: login**——`<code-input length="6">` 替换单输入；登录卡 + 一枚 `.glow-orb`；logo 字阶 `--text-3xl`；按钮 `hover-class` 有按压反馈。
- [ ] **Step 2: circle**——页内 tab 换 `<tabs>` 组件（滑块）；圈子卡 `.card` + `hover-class` 按压；chips 的 `chip-active` 改 `--grad-flame`；头像占位光晕 ≤1；列表 stagger。
- [ ] **Step 3: order-create**——表单换 `<field>`；删除行钮换 `<icon-btn name="trash">`；提交 `.btn-primary`；stagger 行。
- [ ] **Step 4: sheet-list**——清单卡 `.card.clickable` + `hover-class`（现状无按压）；FAB 胶囊 `.btn-primary` + `<icon-btn name="plus">` 或带文字主按钮（去 emoji ＋）；空状态换 `<empty-state title="还没有点单清单" desc="发起一单，让家人来认领拿手菜">`。
- [ ] **Step 5: notifications**——未读条渐变；卡 stagger + 按压；「加载更多」`.btn-secondary`。
- [ ] **Step 6: profile**——画像卡玻璃 + 光晕；taste-tag `.badge-pill.is-open`；nav-link hover/按压底色过渡；退出按钮 `.btn-danger`。
- [ ] **Step 7: pantry**——列表 stagger；临期/过期 `.badge-pill.is-warn/.is-expired`（删硬编码）。
- [ ] **Step 8: 开发者工具走查七页**：无报错、无阶外字号（对照 Task 7 Step 3 清单清零）、无 emoji、玻璃兜底可读、按压态全变体可用。
- [ ] **Step 9: Commit**

```bash
git add miniapp/pages/login miniapp/pages/circle miniapp/pages/order-create miniapp/pages/sheet-list miniapp/pages/notifications miniapp/pages/profile miniapp/pages/pantry
git commit -m "style(ui): 小程序功能页暖玻璃铺开"
```

---

### Task 10: 小程序核心页 + 烹饪 WXS 手势 + 双端一致性收尾

**Files:**
- Modify: `miniapp/pages/sheet-detail/*`、`recipe-generate/*`、`recipe-detail/*`、`cook-mode/*`
- Modify: `web/src/views/CookMode.vue`（若 AI 建议卡/进度条仍有差异）与 `RecipeGenerate.vue`（光标双端一致）
- Create: `miniapp/pages/cook-mode/gesture.wxs`（WXS 跟手）

**Interfaces:**
- Consumes: Task 7/8；web 侧 Task 3 `showGlassToast`
- Produces: `gesture.wxs` 导出 `touchstart/touchmove/touchend` 处理器（move 期间 `translateX` 跟手，end 时按位移阈值 `>60rpx` 触发 `fireEvent('swipe', {dir:1|-1})`）；cook 页 `bindswipe="onSwipe"` 走与按钮相同的切步逻辑（含边界 toast）。

- [ ] **Step 1: gesture.wxs**（WXS 不能直接 setData，用 `ownerInstance.selectComponent('.step-stage').setStyle`；实现如下）：

```js
// miniapp/pages/cook-mode/gesture.wxs
var startX = 0
var dx = 0
function touchstart(event, ownerInstance) {
  startX = event.touches[0].clientX
  dx = 0
}
function touchmove(event, ownerInstance) {
  dx = event.touches[0].clientX - startX
  var el = ownerInstance.selectComponent('.step-stage')
  if (el) el.setStyle({ transform: 'translateX(' + dx + 'px)', transition: 'none' })
}
function touchend(event, ownerInstance) {
  var el = ownerInstance.selectComponent('.step-stage')
  if (el) el.setStyle({ transform: 'translateX(0)', transition: 'transform .45s cubic-bezier(.22,1,.36,1)' })
  if (dx > 60) event.instance.fireEvent('swipe', { dir: -1 })
  else if (dx < -60) event.instance.fireEvent('swipe', { dir: 1 })
  dx = 0
}
module.exports = { touchstart: touchstart, touchmove: touchmove, touchend: touchend }
```

wxml：`<view class="step-stage" bindtouchstart="..." data-event-opts>`——WXS 事件绑定写法为 `<view class="step-stage" bindtouchstart="{{g.touchstart}}" bindtouchmove="{{g.touchmove}}" bindtouchend="{{g.touchend}}" bindswipe="onSwipe">`，`<wxs src="./gesture.wxs" module="g" />`。

- [ ] **Step 2: cook-mode 页**——JS 的 `nextStep/prevStep` 合并为 `goStep(dir)`（边界 `showGlassToast('已是最后一步'/'已是第一步')`），按钮与 `onSwipe(e)` 同走；切步动画 `this.animate('.step-body', [...], 400)` 方向对应 dir；进度条轨道改 `--primary-weak`（与 web 对齐）；AI 建议卡换 `<glass>` 白玻璃（对齐 web `.glass-card`，删橙底面板）；倒计时归零脉冲 `this.animate('.timer', [{scale:1},{scale:1.2},{scale:1}], 400)`。
- [ ] **Step 3: recipe-generate 页**——逐段淡入 + 光标保留；与 web 视觉同参数。
- [ ] **Step 4: recipe-detail 页**——步骤编号排版（同 web Task 5 Step 2）；星评 `this.animate` 弹跳；删除/回滚换 `<icon-btn>`；照片 `<photo-thumb>` + 全屏预览 `wx.previewImage` 或 lightbox 等价。
- [ ] **Step 5: sheet-detail 页**——菜单换 `<bottom-sheet>`/`<confirm-dialog>`（替换 `wx.showModal`，API 改 Promise 调用）；徽标/按钮 token 化收尾。
- [ ] **Step 6: 双端一致性终检**（spec §6.1 全单）：AI 建议卡同为玻璃卡、进度条轨道同色、web 有流式光标、语义色 0 硬编码（`grep -rn "#1d4ed8\|#fef3c7\|#fee2e2\|#f1f5f9" web/src miniapp --include=*.vue --include=*.wxss --include=*.css --include=*.js | grep -v tokens` 仅允许出现在 tokens 文件）、字阶归位、`.empty-main/.empty-sub` 收编 EmptyState、底栏未选中 `--text-tertiary`、`--accent` 用途注释写入 tokens。
- [ ] **Step 7: 构建/走查**

Run: `cd web && npm run build`；开发者工具开 cook-mode：滑动跟手、松手按阈值切步或回弹、末步 toast；生成页流式光标与 web 一致。

- [ ] **Step 8: Commit**

```bash
git add miniapp/pages/sheet-detail miniapp/pages/recipe-generate miniapp/pages/recipe-detail miniapp/pages/cook-mode web/src/
git commit -m "feat(ui): 小程序核心页动效与双端一致性收尾(WXS 跟手)"
```

---

### Task 11: 终验、回归与文档更新

**Files:**
- Modify: `docs/PROJECT-STATUS.md`（路线图登记本轮 UI 升级；§5 划掉已修 3 项）
- Modify: `docs/TODO.md`（登记 Skyline 延后项 + 双端真机走查欠账）
- Modify: `docs/superpowers/specs/2026-09-22-warm-glass-ui-design.md`（§7 验收清单逐项打勾）

**Interfaces:**
- Consumes: Task 1~10 全部产出
- Produces: 无代码；产出验收记录与文档状态。

- [ ] **Step 1: 全量构建与后端回归**

```bash
cd web && npm install && npm run build
cd ../server && mvn clean test
```

Expected: web 构建成功（产物 `web-dist/`）；`mvn clean test` 全绿（后端零改动，作回归闸）。

- [ ] **Step 2: 静态抽查（防廉价感 + 清旧账）**

```bash
grep -rn "window.confirm" web/src && echo FAIL || echo OK
grep -rnE "＋|★" web/src/views miniapp/pages && echo FAIL || echo OK
grep -rnE "font-size: (11|13|18|22|26|30|36)px" web/src && echo FAIL || echo OK
grep -rnE "(22|26|30|36)rpx" miniapp/pages && echo FAIL || echo OK
grep -rn "background: #1d4ed8\|background: #fef3c7\|background: #fee2e2\|background: #f1f5f9" web/src miniapp | grep -v -i token && echo FAIL || echo OK
```

Expected: 全部 OK。

- [ ] **Step 3: 响应式走查** 375 / 768 / 1024 / 1440 四档：玻璃/光晕不破版、导航双形态正常、CookMode 大字不溢出。

- [ ] **Step 4: 双端全流程手工回归**（每项记结果）：登录（绑定码六格）→ 建圈/建单 → 认领/释放 → 通知 → 生成菜谱（流式光标）→ 详情（星评/照片 lightbox/版本回滚确认弹层）→ 烹饪（切步方向/边界 toast/倒计时脉冲/拍照）→ 食材库 → 画像/登出（幂等）→ Web 分享页 `/s/:token`。小程序同流程 + 滑动跟手。

- [ ] **Step 5: 可访问**：系统开启「减弱动态效果」后全部动效降级；键盘 Tab 焦点环可见；玻璃面上关键文字对比度 ≥4.5:1。

- [ ] **Step 6: 文档更新**（PROJECT-STATUS 路线图加一行「UI Warm Glass 升级 ✅」并清 §5 三项；TODO 加「🧑 Skyline 迁移评估（worklet/共享元素/自定义路由）」「🧑 双端真机 UI 走查」；spec §7 打勾）。

- [ ] **Step 7: Commit**

```bash
git add docs/
git commit -m "docs(ui): Warm Glass 升级终验记录与进度更新"
```

---

## Self-Review 记录

1. **Spec 覆盖**：§2 视觉 token/材质/防廉价感 → Task 1/7；§3 动效 14 项 → Task 2（转场/导航）、4（stagger/骨架/星评就位）、5（切步/共享元素/流式/星评）、8/10（小程序对齐）；§4 组件 13 类 → Task 3/4（web）8（小程序）；§5 WebView 路线 → Task 7/8/10；§6 一致性 8 条 → Task 10 Step 6；§6.2 backlog 3 项 → Task 6；§7 验收 → Task 11。无缺口。
2. **占位符**：组件实现以接口+关键代码块给出，页面铺开以「共享检查清单 + 逐页差异点」定界（沿用本仓 `2026-09-20-ui-modernization.md` 成例）；无 TBD/TODO 残留。
3. **类型/命名一致**：`showToast(text, type)`/`confirm(opts)→Promise<boolean>`/`goStep(dir)`/`SPRING_TAP|SPRING_PAGE|SPRING_BOUNCE`/`showGlassToast`/组件名（web PascalCase 与小程序 kebab-case 映射）各任务一致。
