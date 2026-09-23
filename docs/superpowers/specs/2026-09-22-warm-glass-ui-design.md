# Warm Glass UI 升级设计（2026-09-22）

> 基线：2026-09-20 Soft UI Evolution 暖陶土橙版（已落地）。
> 本次目标：在不动业务逻辑的前提下，把双端 UI 升级为「暖玻璃流光」视觉体系 + 全链路弹簧动效体系 + 共享组件原语层，并顺手修 3 个 UI backlog。
> 决策来源：与用户逐项澄清（色板方向/动效力度/范围边界/依赖策略）+ 联网案例调研（Linear 重设计、2026 Motion UX 趋势、Motion for Vue、View Transitions API、微信小程序动画/Skyline 文档、Reka UI）。

---

## 1. 背景与目标

### 1.1 基线现状（问题清单）

2026-09-20 版统一了双端 token（色/圆角/阴影/字族），但：

- **动效基建为零**：全站仅 1 个 @keyframes（呼吸）+ 3 处 transition；无页面转场（`App.vue` 的 `router-view :key="route.fullPath"` 甚至强制整树重挂载）、无列表入场、Toast 瞬切、烹饪步骤瞬变、无骨架屏、卡片无 hover 抬升；小程序 `.button-hover` 只定义了 primary 一档。
- **视觉层次平淡**：单档阴影、无 hero 光效、空状态无插画、图标体系缺位（spec 禁 emoji 未落实）、`window.confirm`/`wx.showModal` 质感割裂。
- **token 不完整**：间距/字阶未 token 化（页面里 4~48px 手感混用、阶外字号遍布）；状态语义色在 9 处硬编码重复。
- **双端漂移**：AI 建议卡（web 白卡 vs 小程序橙底）、进度条轨道、流式光标（小程序有 web 无）。
- **已登记 UI backlog**（`docs/PROJECT-STATUS.md` §5）：末步 skip 无反馈、RecipeGenerate 取参不一致/redirect 前闪烁、logout 双重 reLaunch。

### 1.2 目标（用户确认的四项决策）

| 决策点 | 结论 |
|---|---|
| 色板 | 保留暖陶土橙唯一强调色，底色中性化收敛（页面换暖渐变 wash，中性面纯色） |
| 动效力度 | **全链路动效体系**：页面转场、列表 stagger、hover/press、Toast/弹层、烹饪切步、星评、骨架屏、流式节奏 |
| 范围 | 视觉 + 动效 + 3 个 UI backlog 修复 + 共享组件原语层 |
| 依赖 | 允许引入前沿组件：Web 引 **motion-v** + **reka-ui** + 原生 View Transitions；小程序原生栈（WXSS/`this.animate`/WXS） |

### 1.3 风格定位：Warm Glass 暖玻璃流光

毛玻璃卡片 + 环境光晕 + 强调件渐变/流光 + 弹簧物理动效。**颜色克制、材质与动效出彩**——「炫酷」由玻璃深度、光效峰值、弹簧手感承载，而不是靠多色堆叠。

---

## 2. 视觉语言（§1 已确认）

### 2.1 色彩 token（在现有 tokens 上增量修订）

| Token | 值 | 用途 |
|---|---|---|
| `--primary` | `#c2410c` | 唯一强调色主值 |
| `--primary-hot` | `#ea580c` | 渐变暖端（新增） |
| `--primary-deep` / `--primary-weak` | `#9a3412` / `#fff1e6` | 沿用 |
| `--accent-*` | `#059669` / `#047857` / `#ecfdf5` | 沿用（正向） |
| `--grad-flame` | `linear-gradient(135deg, #c2410c, #ea580c)` | 主按钮/强调徽标（新增） |
| `--grad-done` / `--grad-cooking` | `linear-gradient(135deg,#047857,#059669)` / `linear-gradient(135deg,#1d4ed8,#3b82f6)` | 状态徽标（新增） |
| `--bg-page` | 改为渐变 wash：`linear-gradient(165deg,#faf6f1 0%,#fff7ed 45%,#f3ece5 100%)` | 页面底（替代纯色 `#fffbeb`） |
| `--glass-bg` | `rgba(255,255,255,.65)` | 玻璃面（新增） |
| `--glass-border` | `rgba(255,255,255,.85)` | 玻璃描边（新增） |
| `--glass-blur` | `blur(16px)` | 玻璃模糊（新增） |
| `--shadow-card` | 改暖色深影：`0 8px 28px rgba(154,52,18,.1), inset 0 1px 0 rgba(255,255,255,.95)` | 玻璃卡（inset 顶高光） |
| `--shadow-glow` | `0 2px 8px rgba(194,65,12,.35)` | 强调件微光晕（新增） |
| `--text-*` / `--border` / `--danger` / `--warning` / `--ring` | 沿用 | 纯中性面永远纯色 |
| 语义状态色 | OPEN/CLAIMED/COOKING/DONE/SHARED/IN_PROGRESS/临期/过期 全部升为 token | 收编 9 处硬编码 |

**新增 token 组（本节重点）**：

- 间距阶梯：`--space-1..7` = 4/8/12/16/24/32/48px（小程序 rpx ×2）
- 字阶：`--text-xs..3xl` = 12/14/16/20/24/28/32px（小程序 24~64rpx），消灭阶外值
- Motion tokens（见 §3）
- 光晕：`--glow-terracotta = radial-gradient(circle, rgba(194,65,12,.16~.2), transparent 70%)`

### 2.2 材质规范

- **玻璃卡**：`--glass-bg` + `--glass-blur` + `--glass-border` + `--shadow-card`（含 inset 顶高光），圆角沿用 radius 16/24px。
- **环境光晕**：radial-gradient 圆斑 + blur(4~10px)，置于 hero/主卡背后。
- **强调件**（主按钮、非中性状态徽标）：`--grad-flame` 渐变 + `--shadow-glow`。
- 阴影一律带暖色调 `rgba(154,52,18,*)`，禁止纯黑阴影。

### 2.3 防廉价感硬约束（验收项）

1. 流光扫过**只在 hover/press 触发一次**，禁止无限循环动画。
2. 环境光晕**每页 ≤2 处**，只出现在 hero/主卡背后，透明度 ≤20%。
3. 玻璃参数全局唯一（blur 16px + white .65 + inset top highlight），禁止页面私改。
4. 渐变只用于强调件（主按钮/状态徽标/光晕）；中性面（内容卡、输入框、面板）永远纯色。唯一例外：页面底 `--bg-page` 的暖渐变 wash（属画布氛围，不属内容面）。
5. 动效一律走 motion token，且尊重 `prefers-reduced-motion`（降级为纯淡入淡出/瞬时）。
6. 图标一律 Lucide 风线性 SVG（inline sprite 或按需组件），**禁止 emoji 当图标**（清掉 `＋`/`★` 等）。

### 2.4 字体与排版

- 沿用 Nunito Sans（拉丁/数字）+ PingFang SC / HarmonyOS Sans SC 栈；标题字重 800、letter-spacing -0.01em。
- 吸收 Editorial 方案细节（用户未选 C 但允许混搭）：菜谱**步骤采用编号排版**（01/02/03 + 橙色序号），详情页大标题可加大到 28px 档。

---

## 3. 动效体系（§2 已确认）

### 3.1 Motion tokens（双端同名；小程序用 CSS 近似弹簧）

| Token | 值 | 用途 |
|---|---|---|
| `spring/tap` | stiffness 500, damping 30 | 按钮按压、开关 |
| `spring/page` | stiffness 260, damping 32 | 页面转场、面板 |
| `spring/bounce` | stiffness 600, damping 18 | 星评、徽标、成功反馈 |
| `duration/exit` | 200ms | 一切退场 |
| `stagger/item` | 60ms | 列表入场间隔 |
| `ease-out-soft` | `cubic-bezier(.22,1,.36,1)` | CSS 降级弹簧近似 |

### 3.2 动效清单（全链路）

| # | 场景 | 行为 | Web | 小程序 |
|---|---|---|---|---|
| 1 | 页面转场 | 方向感滑动+淡入：前进右入/返回左入（`ease-out-soft` 350~550ms）；修复 `route.fullPath` 整树重挂载（改按路由 name/meta 定 key 与方向） | `<RouterView>` + `<Transition>`（按路由层级定方向）；在浏览器支持 View Transitions 时渐进增强为共享元素形变，不支持时自动退化 | 页面 onShow 内容编排入场（transform+opacity） |
| 2 | 列表 stagger | 圈子/菜品/通知/清单列表逐条上浮 60ms 间隔 | motion-v `whileInView` 或 TransitionGroup | `this.animate` 关键帧按 index 延迟 |
| 3 | 卡片交互 | hover 抬升 -2~4px + 阴影加深；press 弹簧 scale(.96) | motion-v `whileHover/whilePress` | `hover-class` 全变体补齐（primary/ghost/danger/accent + 可点卡片） |
| 4 | Toast | 玻璃胶囊弹跳进（spring/bounce）→ 上浮淡出；成功/失败/信息三态带图标 | 组件化 AppToast | 封装 showGlassToast（替代裸 `wx.showToast`） |
| 5 | 确认/操作面板 | ConfirmDialog 弹簧缩放入场 + 毛玻璃遮罩；BottomSheet 上滑 | reka-ui Dialog + 玻璃皮肤 + motion-v | 自定义 confirm/bottom-sheet 组件 |
| 6 | 烹饪切步 | 方向性推入推出（手势方向决定）；末步边界反馈 toast | Transition + motion | WXS 跟手手势 + `this.animate` 切步 |
| 7 | 星评 | 点星 spring/bounce pop + 变色 | motion-v | `this.animate` scale 弹跳 |
| 8 | 骨架屏 | shimmer 扫光替代「加载中…」 | Skeleton 组件 | 同构组件 |
| 9 | AI 流式 | 光标闪烁（web 补上）+ delta 逐段淡入；等待态保留呼吸 | CSS + 流式渲染 | 沿用光标 + 逐段淡入 |
| 10 | Tab 指示器 | 滑块滑动（spring/page）替代 class 硬切 | 布局动画（layoutId/width+transform） | `this.animate` 滑块 |
| 11 | 进度条 | width 弹簧过渡 + 完成脉冲 | motion/CSS | CSS transition |
| 12 | 共享元素 | 列表卡 → 详情头卡形变（清单/菜谱） | View Transitions（`view-transition-name`）/ motion `layoutId`；浏览器不支持时退化为场景 1 的常规页面转场 | 本期仅做内容入场（Skyline open-container 后续） |
| 13 | 登录/绑定码 | 6 位分段输入逐格点亮 | CodeInput 组件 | 同构组件 |
| 14 | 图片 | 缩略 hover 放大；点击 Lightbox 居中缩放入场 | 组件 | 同构组件 |

### 3.3 性能与可访问

- 只动 `transform`/`opacity`；60fps 预算，中低端安卓不掉帧；`will-change` 谨慎使用。
- 全部动效有 `prefers-reduced-motion: reduce` 降级（淡入淡出/瞬时）。
- 每个动效反馈必须同时有语义文案（motion for mood, copy for meaning）——如末步 toast 有文字，不只靠抖动。

---

## 4. 组件原语层（§3 已确认）

### 4.1 组件清单（双端同名同构）

`Button`（primary/ghost/danger/accent + sm/md + 流光）· `IconButton`（Lucide SVG）· `Field`/`CodeInput` · `GlassCard` · `Badge`（状态四态渐变胶囊）· `Toast`（三态）· `ConfirmDialog` · `BottomSheet` · `EmptyState`（线性插画 + 标题 + 副文案 + 主按钮）· `Skeleton` · `Tabs`（滑块指示）· `PhotoThumb`/`Lightbox` · `NavBar`/`TabBar`（滑块选中态）。

### 4.2 落位与技术底座

- **Web**：`web/src/components/` 新建；**reka-ui**（无样式可访问原语：Dialog/焦点管理/键盘导航）+ 自有玻璃皮肤（scoped CSS 消费 token）+ motion-v 动效。不引 UI 组件库皮肤（不引 Element/Vant/shadcn 全家桶）。
- **小程序**：`miniapp/components/` 自定义组件（原 `virtualHost` 等按需）；API（props/事件名）与 web 对应组件对齐；动画用 WXSS + `this.animate` + WXS。
- 页面内散装 UI（toast 状态、window.confirm、裸输入行、空状态）全部收编到组件；**文案/字段/事件/跳转逻辑一律不动**（bug 修项除外）。

---

## 5. 小程序技术路线（§4 已确认）

- **本期 WebView 打满**：WXSS transition/animation + `this.animate` 关键帧（基础库 ≥2.9）+ WXS 响应事件（烹饪滑动跟手）+ onShow 入场编排。
- **Skyline 延后为独立迭代**：worklet 弹簧、共享元素（open-container）、自定义路由（preset-route）是小程序端的「真前沿」，但涉及渲染引擎 + glass-easel 迁移、CSS 兼容差异、真机验证（本就欠账）。本期组件按双渲染兼容写法（避免 position:fixed 滥用、避免 Skyline 不支持的选择器），留好迁移缝；延后项记入 `docs/TODO.md`（🧑 真机验证后决定）。

## 6. 一致性修复 + UI backlog（§4 已确认）

### 6.1 双端漂移修复

1. AI 建议卡统一为玻璃卡（web 白卡与小程序橙底归一）。
2. 烹饪进度条轨道统一 token（`--primary-weak`）。
3. Web RecipeGenerate 补流式光标（对齐小程序）。
4. 状态语义色 9 处硬编码收编 token；按钮 hover 深色档一并 token 化。
5. 字阶/间距阶梯 token 化并归位（消灭阶外值）。
6. `.empty-main/.empty-sub` 提升为双端 EmptyState 组件的一部分。
7. 移动底栏未选中态改用 `--text-tertiary`（层级修正）。
8. `--accent` 闲置项处置：`btn-accent` 统一使用 `--accent-deep`（与现实现一致），`--accent` 保留为渐变 `--grad-done` 的浅端用途并在 tokens 文件注释说明，不再闲置。

### 6.2 UI backlog 修复（PROJECT-STATUS §5）

1. **末步 skip/继续无视觉反馈** → 边界反馈 toast（「已是最后一步」/「已是第一步」）。
2. **RecipeGenerate 取参不一致/redirect 前闪烁**：`RecipeDetail` 迭代入口以 `recipeId+comment` 跳转，而 `RecipeGenerate` 校验 `circleId+dishName` → 统一参数契约（支持两套入参并显式分支），校验失败不再先弹错误再跳转。
3. **logout 双重 reLaunch** → 幂等化（单飞/标志位）。

---

## 7. 验收标准

1. **防廉价感守则**（§2.3 六条）逐条走查通过。
2. `prefers-reduced-motion` 下全部动效降级可用；focus-visible 环保留。
3. 对比度 ≥4.5:1（玻璃面在渐变底上实测，含光晕叠加时最差点）。
4. 响应式走查 375 / 768 / 1024 / 1440 四档（玻璃/光晕不破版）。
5. 双端功能回归：点单/认领/烹饪/生成/通知/分享/登录全流程无回归（`cd server && mvn clean test` 全绿 + 手工走查）。
6. 3 个 backlog 修复各有明确复现步骤验证。
7. 全仓不再有 emoji 图标、阶外字号、状态色硬编码（grep 抽查）。
8. Web 构建产物仍输出 `web-dist/`；`npm run build` 通过；小程序开发者工具无报错。

## 8. 范围外

- 暗色模式；业务逻辑/API/数据结构改动；Skyline 迁移（延后）；tabBar 信息架构调整；组件库皮肤框架（Element/Vant/shadcn）；服务端改动。

## 9. 调研参考（案例）

- Linear UI 重设计（linear.app/now/how-we-redesigned-the-linear-ui）：降噪、LCH/elevation 层级、限制 chrome 用色 → 耐看
- Micro-Interactions 2026（creativealive.com）：弹簧物理、编排式状态转场、触觉式反馈、动效 token 化、motion 作为品牌层
- Motion for Vue（motion.dev/docs/vue）：spring/layoutId/AnimatePresence/whileInView/手势
- View Transitions API（MDN）：SPA 共享元素转场，渐进增强
- 微信小程序动画文档 + Skyline 增强特性（worklet/手势/自定义路由/共享元素）
- Reka UI（reka-ui.com）：无样式可访问原语（Dialog/焦点管理）
