# Link-Life UI 现代化重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按设计文档建立统一设计 token 体系，双端（Web 10 页 + 小程序 11 页）全量重构为温暖柔和卡片风。

**Architecture:** Token 层先行（Web `tokens.css` + `style.css` 全局组件类；小程序 `app.wxss`），随后按批重构页面。Web 端 `App.vue` 重写为响应式双形态导航（<768px 底部 TabBar，≥768px 顶部导航）。纯样式与模板层改动，零业务逻辑改动。

**Tech Stack:** Vue 3 + Vite（Web）、原生 WXML/WXSS（小程序）、ui-ux-pro-max 设计系统产出。

**Spec:** `docs/superpowers/specs/2026-09-20-ui-modernization-design.md`

## Global Constraints

- 不引入组件库（element-plus / vant-weapp 等）
- 不做暗色模式
- 不改任何 `<script>` 业务逻辑、API 调用、路由路径、数据结构（`App.vue` 导航模板重构除外，其中 script 仅允许新增导航所需的展示型 computed）
- 色彩 token 必须与 spec 3.1 完全一致：`#C2410C` `#9A3412` `#FFF1E6` `#059669` `#ECFDF5` `#FFFBEB` `#FFFFFF` `#0F172A` `#475569` `#94A3B8` `#F2E6E2` `#DC2626` `#F59E0B`
- 双端旧色值 `#07c160` `#e8f8ef` `#e64340` `#f7ba2a` `#e66` 重构后全仓不得残留（用 grep 验证）
- 小程序样式单位一律 rpx；Web 一律 px
- 每个任务结束必须 `npm run build`（Web 任务）通过后提交；小程序任务以 grep 验证 + 无残留旧类引用为验收
- 图标：SVG/Lucide 风格，禁止 emoji 当图标（页面文案中的装饰性 emoji 一并移除）
- 提交信息格式：`feat(ui): <内容>` / `style(ui): <内容>`

## 共享：页面重构检查清单（下文每页任务默认执行，不再重复）

1. 页面根容器：`bg-page` 底色、统一内边距 16px（小程序 32rpx）
2. 卡片化：所有信息块改用 `.card`（Web）/`.card`（小程序，见 Task 6 全局类）：白底 + `--radius` + `--shadow-card` + 16px/32rpx 内边距
3. 状态徽标：状态文字改用 `.badge-pill` + 状态色类（OPEN→`is-open`、CLAIMED→`is-claimed`、COOKING→`is-cooking`、DONE→`is-done`）
4. 按钮：主操作 `.btn-primary`，次操作 `.btn-secondary`，危险 `.btn-danger`（小程序用 Task 6 同名类）
5. 空状态：统一 `.empty` 结构（图形 + 文案 + 可选主按钮）
6. 列表项间距 `--gap`；页标题字阶 20px/40rpx bold
7. 移除内联硬编码颜色，全部换 token 变量
8. 文案、字段、事件处理、跳转逻辑一律不动

---

### Task 1: Web 设计 Token 层与全局组件类

**Files:**
- Modify: `web/src/styles/tokens.css`（全量重写）
- Modify: `web/src/style.css`（全量重写，引入全局组件类）
- Modify: `web/index.html`（引入 Nunito Sans 字体 link）

**Interfaces:**
- Produces: 全局类 `.card` `.btn` `.btn-small` `.btn-primary` `.btn-secondary` `.btn-danger` `.badge-pill` `.is-open/.is-claimed/.is-cooking/.is-done` `.empty` `.input` `.page-title` `.toast` 及全部 CSS 变量，后续所有 Web 页面任务依赖这些类与变量

- [ ] **Step 1: 重写 tokens.css**

```css
:root {
  --primary: #c2410c;
  --primary-deep: #9a3412;
  --primary-weak: #fff1e6;
  --accent: #059669;
  --accent-weak: #ecfdf5;
  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-tertiary: #94a3b8;
  --bg-page: #fffbeb;
  --bg-card: #ffffff;
  --border: #f2e6e2;
  --danger: #dc2626;
  --warning: #f59e0b;
  --ring: #9a3412;
  --radius-lg: 24px;
  --radius: 16px;
  --radius-sm: 10px;
  --shadow-card: 0 2px 8px rgba(154, 52, 18, 0.06), 0 8px 24px rgba(154, 52, 18, 0.08);
  --shadow-press: 0 1px 2px rgba(154, 52, 18, 0.1);
  --gap: 12px;
}
```

- [ ] **Step 2: 重写 style.css**

```css
html,
body {
  height: 100%;
}

body {
  margin: 0;
  background: var(--bg-page);
  color: var(--text-primary);
  font-family: 'Nunito Sans', 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;
}

* { box-sizing: border-box; }

a { color: var(--primary); text-decoration: none; }

button { cursor: pointer; font-family: inherit; }
button:focus-visible, a:focus-visible, input:focus-visible {
  outline: 2px solid var(--ring);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition: none !important; animation: none !important; }
}

.card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: var(--radius-sm);
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  transition: background 200ms ease-out, box-shadow 200ms ease-out, transform 200ms ease-out;
}

.btn:active { box-shadow: var(--shadow-press); transform: translateY(1px); }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-primary { background: var(--primary); color: #fff; }
.btn-primary:hover { background: var(--primary-deep); }
.btn-secondary {
  background: var(--bg-card);
  color: var(--primary);
  border: 1px solid var(--primary);
}
.btn-secondary:hover { background: var(--primary-weak); }
.btn-danger { background: var(--danger); color: #fff; }
.btn-danger:hover { background: #b91c1c; }
.btn-small { padding: 6px 12px; font-size: 13px; }

.badge-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.badge-pill.is-open { background: #f1f5f9; color: var(--text-secondary); }
.badge-pill.is-claimed { background: var(--primary-weak); color: var(--primary-deep); }
.badge-pill.is-cooking { background: #eff6ff; color: #1d4ed8; }
.badge-pill.is-done { background: var(--accent-weak); color: var(--accent); }

.input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-card);
  transition: border-color 200ms ease-out, box-shadow 200ms ease-out;
}
.input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-weak);
}
.input::placeholder { color: var(--text-tertiary); }

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 80px;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.9);
  color: #fff;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  z-index: 1000;
}
```

- [ ] **Step 3: index.html 引入字体**

在 `<head>` 中加入：

```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@400;600;700&display=swap" rel="stylesheet" />
```

- [ ] **Step 4: 构建验证**

Run: `cd web && npm run build`
Expected: 构建成功，无 CSS 语法错误

- [ ] **Step 5: Commit**

```bash
git add web/src/styles/tokens.css web/src/style.css web/index.html
git commit -m "feat(ui): web 设计 token 层与全局组件类"
```

---

### Task 2: Web App.vue 响应式双形态导航

**Files:**
- Modify: `web/src/App.vue`（template 与 style 重写；script 仅新增展示型 computed）

**Interfaces:**
- Consumes: Task 1 的全局类与 CSS 变量
- Produces: `.tabbar`（移动端底栏）与 `.topbar`（桌面顶栏）导航；`unread` 角标两处共用

- [ ] **Step 1: 重写 template**

```html
<template>
  <div class="app">
    <header v-if="showNav" class="topbar">
      <span class="brand">Link-Life</span>
      <nav class="topbar-nav">
        <router-link to="/" class="nav-link">首页</router-link>
        <router-link v-if="user" to="/notifications" class="nav-link">
          通知<span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
        <router-link v-if="user" to="/me" class="nav-link">我的</router-link>
      </nav>
      <span class="topbar-right">
        <span v-if="user" class="nickname">{{ user.nickname }}</span>
        <button v-if="user" class="btn btn-secondary btn-small" @click="logout">退出</button>
      </span>
    </header>
    <main class="main">
      <router-view :key="route.fullPath" />
    </main>
    <nav v-if="showNav" class="tabbar">
      <router-link to="/" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        <span>首页</span>
      </router-link>
      <router-link v-if="user" to="/notifications" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
        <span>通知</span>
        <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      </router-link>
      <router-link v-if="user" to="/me" class="tabbar-item">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        <span>我的</span>
      </router-link>
    </nav>
    <div v-if="toast.visible" class="toast">{{ toast.text }}</div>
  </div>
</template>
```

注意：script 部分**只新增** `navLinks` 相关展示 computed（如需），现有 `loadUser`/轮询/`logout`/watch 逻辑一行不动。若现有路由没有 `/`（首页）路径，保留现有页面实际路径；tabbar 首页项指向现首页路由。

- [ ] **Step 2: 重写 style**

```css
.app { min-height: 100%; display: flex; flex-direction: column; }
.main { flex: 1; width: 100%; max-width: 1024px; margin: 0 auto; padding: 24px 16px calc(24px + env(safe-area-inset-bottom)); }

.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  background: rgba(255, 251, 235, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}
.brand { font-weight: 700; font-size: 18px; color: var(--primary-deep); }
.topbar-nav { display: flex; gap: 8px; }
.nav-link {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 999px;
  color: var(--text-secondary); font-size: 14px; font-weight: 600;
  transition: background 200ms ease-out, color 200ms ease-out;
}
.nav-link:hover { background: var(--primary-weak); color: var(--primary-deep); }
.nav-link.router-link-active { background: var(--primary); color: #fff; }
.topbar-right { display: flex; align-items: center; gap: 12px; }
.nickname { color: var(--text-secondary); font-size: 14px; }
.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 999px; background: var(--danger); color: #fff;
  font-size: 11px; font-weight: 700; line-height: 1;
}

.tabbar { display: none; }

@media (max-width: 767px) {
  .topbar { display: none; }
  .main { padding: 16px 16px calc(88px + env(safe-area-inset-bottom)); }
  .tabbar {
    position: fixed;
    left: 0; right: 0; bottom: 0;
    z-index: 100;
    display: flex;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(8px);
    border-top: 1px solid var(--border);
    padding-bottom: env(safe-area-inset-bottom);
  }
  .tabbar-item {
    flex: 1;
    display: flex; flex-direction: column; align-items: center; gap: 2px;
    padding: 8px 0 6px;
    color: var(--text-tertiary); font-size: 11px; position: relative;
  }
  .tabbar-item.router-link-active { color: var(--primary); }
  .tabbar-item .badge { position: absolute; top: 4px; right: calc(50% - 20px); }
}
```

- [ ] **Step 3: 清理旧样式**

删除 App.vue 原有 `<style>` 中与旧 topbar 相关的类名残留，确认无 `#07c160` 等旧色值。

- [ ] **Step 4: 构建验证**

Run: `cd web && npm run build`
Expected: 成功

- [ ] **Step 5: 视觉验证**

Run: `cd web && npm run dev`，浏览器分别以 375px 与 1024px 宽度打开，确认 <768px 显示底部 TabBar、≥768px 显示顶部导航、通知角标正常。
（验证后关闭 dev server）

- [ ] **Step 6: Commit**

```bash
git add web/src/App.vue
git commit -m "feat(ui): web 响应式双形态导航(底栏TabBar+顶栏)"
```

---

### Task 3: Web 免登录页重构（Login、ShareView）

**Files:**
- Modify: `web/src/views/Login.vue`
- Modify: `web/src/views/ShareView.vue`

**Interfaces:**
- Consumes: Task 1 全局类（`.card` `.btn-primary` `.input` `.page-title`）

- [ ] **Step 1: Login.vue 重构**

模板与样式按共享检查清单执行：
- 页面垂直水平居中，登录卡片用 `.card` + `--radius-lg`（大卡片），最大宽度 400px
- 标题用 `.page-title` 居中，品牌名用 `--primary-deep`
- 输入框换 `.input`，按钮换 `.btn .btn-primary`（宽度 100%）
- 绑定码说明文字用 `--text-secondary` 14px
- 登录按钮 loading 态保留现有 disabled 逻辑

- [ ] **Step 2: ShareView.vue 重构**

- 清单信息区 `.card` 化；菜品列表项用 `--gap` 间距 + 行内分隔线 `--border`
- 状态徽标换 `.badge-pill`（OPEN→`is-open`，DONE→`is-done` 等）
- 底部提示（如"免登录只读"）用 `--text-tertiary` 12px
- 错误态（3001 清单不存在）用 `.empty` 结构

- [ ] **Step 3: 构建验证**

Run: `cd web && npm run build && npm run dev`
Expected: 打开 `/login` 与一个分享页路径确认视觉正常，无旧色值

- [ ] **Step 4: Commit**

```bash
git add web/src/views/Login.vue web/src/views/ShareView.vue
git commit -m "style(ui): web 登录页与分享页卡片化重构"
```

---

### Task 4: Web 功能页重构第一批（Circles、Notifications、Profile、Pantry、RecipeGenerate）

**Files:**
- Modify: `web/src/views/Circles.vue`
- Modify: `web/src/views/Notifications.vue`
- Modify: `web/src/views/Profile.vue`
- Modify: `web/src/views/Pantry.vue`
- Modify: `web/src/views/RecipeGenerate.vue`

**Interfaces:**
- Consumes: Task 1 全局类；Task 2 响应式容器（`.main` 已含 max-width 与底部留白）

- [ ] **Step 1: Circles.vue**

- 圈子列表 `.card` 化，每项左侧头像/首字符圆形徽标用 `--primary-weak` 底 + `--primary-deep` 字
- 主操作按钮（新建圈子）`.btn-primary`；标题行右侧操作用 `.btn-secondary btn-small`
- 空状态 `.empty`

- [ ] **Step 2: Notifications.vue**

- 通知项 `.card` 化；未读项左侧 6px 竖条用 `--primary`
- 时间文字 `--text-tertiary` 12px；空状态 `.empty`

- [ ] **Step 3: Profile.vue**

- 用户信息卡 `.card` + `--radius-lg`；菜单项行高 48px、分隔线 `--border`
- 退出登录按钮 `.btn-danger`（或 `.btn-secondary`，与现状语义一致）

- [ ] **Step 4: Pantry.vue**

- 食材项 `.card` 化网格布局（≥768px 两列，<768px 单列：`grid-template-columns: repeat(auto-fill, minmax(280px, 1fr))`）
- 过期/临期状态用 `.badge-pill`（临期 `--warning` 底色 `#FEF3C7`、已过期 danger 弱底 `#FEE2E2`）

- [ ] **Step 5: RecipeGenerate.vue**

- 表单区 `.card`；输入/选择器 `.input`；生成按钮 `.btn-primary` 宽 100%
- 生成中状态文案区加柔和呼吸动画（`@keyframes` opacity 0.6→1，2000ms infinite，尊重全局 reduced-motion）

- [ ] **Step 6: 构建验证**

Run: `cd web && npm run build`
Expected: 成功

- [ ] **Step 7: Commit**

```bash
git add web/src/views/Circles.vue web/src/views/Notifications.vue web/src/views/Profile.vue web/src/views/Pantry.vue web/src/views/RecipeGenerate.vue
git commit -m "style(ui): web 功能页重构第一批(圈子/通知/我的/食材库/生成)"
```

---

### Task 5: Web 核心页重构（SheetDetail、RecipeDetail、CookMode）

**Files:**
- Modify: `web/src/views/SheetDetail.vue`
- Modify: `web/src/views/RecipeDetail.vue`
- Modify: `web/src/views/CookMode.vue`

**Interfaces:**
- Consumes: Task 1 全局类（含全部状态徽标类）

- [ ] **Step 1: SheetDetail.vue**

- 清单头部卡 `.card` + `--radius-lg`：标题 24px、状态徽标 `.badge-pill`、分享按钮 `.btn-secondary`
- 菜品列表：每项 `.card`，认领状态徽标四态（OPEN/CLAIMED/COOKING/DONE 对应 `is-open/is-claimed/is-cooking/is-done`）
- 认领/释放/完成按钮 `.btn-primary/.btn-secondary/.btn-primary`（完成用 `.btn-primary` + accent 色：新增局部类 `.btn-accent { background: var(--accent); color: #fff; }` 放本页 scoped）
- 自由输入添加行：`.input` + `.btn-primary` 组合

- [ ] **Step 2: RecipeDetail.vue**

- 菜谱头部卡（图 + 名称 + 元信息）`.card` + `--radius-lg`
- 步骤列表：序号圆形徽标 `--primary-weak` 底 `--primary-deep` 字，步骤卡 `.card`
- "生成点单"主 CTA `.btn-primary` 吸底（`position: sticky; bottom: 16px;`）

- [ ] **Step 3: CookMode.vue**

- 烹饪模式：全屏 `--bg-page` 底，当前步骤大字 28px 居中，进度条 8px 圆角、填充 `--primary`
- 上一步/下一步按钮 `.btn-secondary/.btn-primary` 大尺寸（padding 14px 32px）
- 计时器数字用 Nunito Sans 700 32px

- [ ] **Step 4: 构建验证**

Run: `cd web && npm run build`
Expected: 成功

- [ ] **Step 5: Commit**

```bash
git add web/src/views/SheetDetail.vue web/src/views/RecipeDetail.vue web/src/views/CookMode.vue
git commit -m "style(ui): web 核心页重构(清单/菜谱/烹饪模式)"
```

---

### Task 6: 小程序 Token 层与全局组件类

**Files:**
- Modify: `miniapp/app.wxss`（全量重写）
- Modify: `miniapp/app.json`（window 配色）

**Interfaces:**
- Produces: 小程序全局类 `.card` `.btn` `.btn-primary` `.btn-secondary` `.btn-danger` `.btn-accent` `.badge-pill` `.is-open/.is-claimed/.is-cooking/.is-done` `.empty` `.input` `.page-title`，后续小程序页面任务依赖

- [ ] **Step 1: 重写 app.wxss**

```css
page {
  background-color: #fffbeb;
  font-size: 28rpx;
  color: #0f172a;
  --primary: #c2410c;
  --primary-deep: #9a3412;
  --primary-weak: #fff1e6;
  --accent: #059669;
  --accent-weak: #ecfdf5;
  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-tertiary: #94a3b8;
  --bg-card: #ffffff;
  --border: #f2e6e2;
  --danger: #dc2626;
  --warning: #f59e0b;
  --radius-lg: 48rpx;
  --radius: 32rpx;
  --radius-sm: 20rpx;
  --shadow-card: 0 4rpx 16rpx rgba(154, 52, 18, 0.06), 0 16rpx 48rpx rgba(154, 52, 18, 0.08);
  --shadow-press: 0 2rpx 4rpx rgba(154, 52, 18, 0.1);
}

.card {
  background: var(--bg-card);
  border: 1rpx solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 32rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 32rpx;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  padding: 20rpx 40rpx;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.4;
}
.btn::after { border: none; }
.btn-primary { background: var(--primary); color: #ffffff; }
.btn-primary.hover, .btn-primary.button-hover { background: var(--primary-deep); }
.btn-secondary { background: var(--bg-card); color: var(--primary); border: 2rpx solid var(--primary); }
.btn-danger { background: var(--danger); color: #ffffff; }
.btn-accent { background: var(--accent); color: #ffffff; }

.badge-pill {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
}
.badge-pill.is-open { background: #f1f5f9; color: var(--text-secondary); }
.badge-pill.is-claimed { background: var(--primary-weak); color: var(--primary-deep); }
.badge-pill.is-cooking { background: #eff6ff; color: #1d4ed8; }
.badge-pill.is-done { background: var(--accent-weak); color: var(--accent); }

.input {
  width: 100%;
  border: 2rpx solid var(--border);
  border-radius: var(--radius-sm);
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  color: var(--text-primary);
  background: var(--bg-card);
}
.input:focus { border-color: var(--primary); }

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  padding: 96rpx 32rpx;
  color: var(--text-secondary);
  font-size: 28rpx;
}
```

- [ ] **Step 2: 更新 app.json window 配色**

`window` 节改为：

```json
"window": {
  "navigationBarTitleText": "Link-Life",
  "navigationBarBackgroundColor": "#fffbeb",
  "navigationBarTextStyle": "black",
  "backgroundColor": "#fffbeb"
}
```

- [ ] **Step 3: 验证**

Run: `grep -rn "07c160\|e8f8ef\|e64340\|f7ba2a" miniapp/app.wxss miniapp/app.json`
Expected: 无匹配

- [ ] **Step 4: Commit**

```bash
git add miniapp/app.wxss miniapp/app.json
git commit -m "feat(ui): 小程序设计 token 层与全局组件类"
```

---

### Task 7: 小程序核心页重构（sheet-list、sheet-detail、circle）

**Files:**
- Modify: `miniapp/pages/sheet-list/sheet-list.wxss`（及 `.wxml` 中类名对齐）
- Modify: `miniapp/pages/sheet-detail/sheet-detail.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/circle/circle.wxss`（及 `.wxml`）

**Interfaces:**
- Consumes: Task 6 全局类；小程序 `.hover-class` 按压态（按钮上加 `hover-class="button-hover"`）

- [ ] **Step 1: sheet-list**

- 清单卡片 `.card` 化；每项含标题（32rpx 600）、状态 `.badge-pill`、更新时间（24rpx `--text-tertiary`）
- "发起点单"悬浮主按钮：固定右下 56×112rpx 圆角胶囊 `.btn-primary`
- 空状态 `.empty`
- wxml 中类名与 wxss 对齐，删除页内旧硬编码颜色

- [ ] **Step 2: sheet-detail**

- 头部卡 `.card`：标题 40rpx、状态 `.badge-pill`、分享按钮 `.btn-secondary`
- 菜品项 `.card` + 状态四态徽标；认领按钮 `.btn-primary`、释放 `.btn-secondary`、完成 `.btn-accent`
- 自由添加输入行：`.input` + `.btn-primary`

- [ ] **Step 3: circle**

- 圈子列表 `.card` 化，头像圆形 `--primary-weak` 底
- 新建圈子入口 `.btn-primary`
- 空状态 `.empty`

- [ ] **Step 4: 验证**

Run: `grep -rn "07c160\|e64340\|f7ba2a\|e8f8ef" miniapp/pages/sheet-list miniapp/pages/sheet-detail miniapp/pages/circle`
Expected: 无匹配

- [ ] **Step 5: Commit**

```bash
git add miniapp/pages/sheet-list miniapp/pages/sheet-detail miniapp/pages/circle
git commit -m "style(ui): 小程序核心页重构(清单列表/详情/圈子)"
```

---

### Task 8: 小程序功能页重构（order-create、notifications、profile、pantry、login）

**Files:**
- Modify: `miniapp/pages/order-create/order-create.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/notifications/notifications.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/profile/profile.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/pantry/pantry.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/login/login.wxss`（及 `.wxml`）

**Interfaces:**
- Consumes: Task 6 全局类

- [ ] **Step 1: order-create**

- 选菜区 `.card`、已选 chips 用 `--primary-weak` 底 `--primary-deep` 字圆角 999rpx
- 自由输入 `.input`；生成清单按钮 `.btn-primary` 宽 100%（112rpx 高）

- [ ] **Step 2: notifications**

- 通知项 `.card`；未读左侧 12rpx 竖条 `--primary`；时间 24rpx `--text-tertiary`

- [ ] **Step 3: profile**

- 用户信息卡 `.card` + `--radius-lg`；菜单行高 96rpx、分隔线 `--border`；退出 `.btn-danger`

- [ ] **Step 4: pantry**

- 食材项 `.card`；临期徽标 `#FEF3C7` 底 + `#B45309` 字、过期 `#FEE2E2` 底 + `--danger` 字（`.badge-pill` 局部扩展类）

- [ ] **Step 5: login**

- 居中品牌卡 `--radius-lg`；登录按钮 `.btn-primary` 宽 100%；说明文字 `--text-secondary`

- [ ] **Step 6: 验证**

Run: `grep -rn "07c160\|e64340\|f7ba2a\|e8f8ef" miniapp/pages/order-create miniapp/pages/notifications miniapp/pages/profile miniapp/pages/pantry miniapp/pages/login`
Expected: 无匹配

- [ ] **Step 7: Commit**

```bash
git add miniapp/pages/order-create miniapp/pages/notifications miniapp/pages/profile miniapp/pages/pantry miniapp/pages/login
git commit -m "style(ui): 小程序功能页重构(点单/通知/我的/食材库/登录)"
```

---

### Task 9: 小程序菜谱页重构（recipe-generate、recipe-detail、cook-mode）

**Files:**
- Modify: `miniapp/pages/recipe-generate/recipe-generate.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/recipe-detail/recipe-detail.wxss`（及 `.wxml`）
- Modify: `miniapp/pages/cook-mode/cook-mode.wxss`（及 `.wxml`）

**Interfaces:**
- Consumes: Task 6 全局类

- [ ] **Step 1: recipe-generate**

- 表单卡 `.card`；输入 `.input`；生成按钮 `.btn-primary` 宽 100%
- 生成中提示区 `--primary-weak` 底圆角卡

- [ ] **Step 2: recipe-detail**

- 头部卡 `.card`；步骤序号圆形 `--primary-weak` 底；步骤卡 `.card`
- 底部"生成点单"按钮 `.btn-primary` 吸底（`position: fixed; bottom: 32rpx; left/right 32rpx;` + 安全区 padding）

- [ ] **Step 3: cook-mode**

- 全屏 `--bg-page` 底；当前步骤 56rpx 大字居中；进度条 16rpx 高圆角填充 `--primary`
- 上一步 `.btn-secondary`、下一步 `.btn-primary`，等宽两列布局

- [ ] **Step 4: 验证**

Run: `grep -rn "07c160\|e64340\|f7ba2a\|e8f8ef" miniapp/pages/recipe-generate miniapp/pages/recipe-detail miniapp/pages/cook-mode`
Expected: 无匹配

- [ ] **Step 5: Commit**

```bash
git add miniapp/pages/recipe-generate miniapp/pages/recipe-detail miniapp/pages/cook-mode
git commit -m "style(ui): 小程序菜谱页重构(生成/详情/烹饪模式)"
```

---

### Task 10: 双端终验

**Files:**
- 无新增/修改（验证任务；如发现残留则修复对应文件）

- [ ] **Step 1: 全仓旧色值扫描**

Run: `grep -rni "07c160\|e8f8ef\|e64340\|f7ba2a\|#e66\b" web/src miniapp --include="*.vue" --include="*.css" --include="*.wxss" --include="*.wxml" --include="*.json"`
Expected: 无匹配（`project.config.json` 等工具配置除外，如有则评估是否属于 UI）

- [ ] **Step 2: Web 构建 + 响应式全检**

Run: `cd web && npm run build && npm run dev`
浏览器 375 / 768 / 1024 / 1440px 四档走查：Login、首页列表、SheetDetail、RecipeDetail、CookMode、Notifications、Profile、ShareView。检查 TabBar 切换、角标、焦点环、hover 过渡。

- [ ] **Step 3: 小程序真机/模拟器走查**

微信开发者工具打开 `miniapp/`，逐页走查 11 页：卡片阴影、徽标四态、按钮按压态、吸底按钮安全区。

- [ ] **Step 4: 功能回归抽查**

- Web：绑定码登录 → 建圈子 → 发起点单 → 认领/完成流转 → 通知角标 → 分享页免登录可见
- 小程序：微信登录 → 点单 → 认领状态机 → 通知页
Expected: 全流程与重构前一致（零逻辑改动验证）

- [ ] **Step 5: Commit（如有修复）**

```bash
git add -A
git commit -m "style(ui): 双端终验修复"
```
