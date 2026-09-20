# Link-Life UI 现代化重构设计（2026-09-20）

## 1. 背景与目标

当前双端 UI（Web：Vue 3 SPA；小程序：原生 WXML/WXSS）停留在微信官方风：绿色 `#07c160` 主色、灰底白卡、6-12px 小圆角、无阴影、无过渡，不符合现代审美。

目标：建立统一设计语言，双端全量重构（Web 10 页 + 小程序 11 页），视觉对齐现代移动产品（温暖柔和卡片风）。

## 2. 设计依据

- 设计系统由开源 skill `nextlevelbuilder/ui-ux-pro-max-skill`（129k stars，MIT）推理引擎生成，产品类目命中 Recipe & Cooking / Family Ordering。
- 引擎推荐 Claymorphism，因可访问性风险 conditional 且偏玩具感，校准为相邻风格 **Soft UI Evolution**（可访问性 risk:low，同为柔和卡片风）。
- 配色沿用引擎产出（暖陶土橙系）；字体针对中文内容适配（引擎推荐的 Varela Round/Nunito 为拉丁字体，仅 Web 端引入 Nunito Sans 覆盖数字与拉丁字符）。

## 3. 设计 Token

### 3.1 色彩

| Token | 值 | 用途 |
|---|---|---|
| `--primary` | `#C2410C` | 主按钮、选中态（白字对比度 ≥4.5:1） |
| `--primary-deep` | `#9A3412` | 按下态、标题强调 |
| `--primary-weak` | `#FFF1E6` | 主色弱底（选中标签、浅色徽标） |
| `--accent` | `#059669` | 认领/完成等正向操作 |
| `--accent-weak` | `#ECFDF5` | 正向弱底 |
| `--bg-page` | `#FFFBEB` | 页面底色（暖奶油） |
| `--bg-card` | `#FFFFFF` | 卡片 |
| `--text-primary` | `#0F172A` | 主文字 |
| `--text-secondary` | `#475569` | 次要文字 |
| `--text-tertiary` | `#94A3B8` | 占位/禁用 |
| `--border` | `#F2E6E2` | 卡片描边 |
| `--danger` | `#DC2626` | 危险操作 |
| `--warning` | `#F59E0B` | 警示/状态 |
| `--ring` | `#9A3412` | 键盘焦点环 |

### 3.2 圆角 / 阴影 / 间距

| Token | Web | 小程序 | 说明 |
|---|---|---|---|
| `--radius-lg` | 24px | 48rpx | 大卡片 |
| `--radius` | 16px | 32rpx | 常规卡片/输入框 |
| `--radius-sm` | 10px | 20rpx | 按钮、标签 |
| `--shadow-card` | `0 2px 8px rgba(154,52,18,.06), 0 8px 24px rgba(154,52,18,.08)` | 同（wxss） | 多层柔和阴影 |
| `--shadow-press` | `0 1px 2px rgba(154,52,18,.10)` | 同 | 按下态 |
| 间距阶梯 | 4/8/12/16/24/32px | 8/16/24/32/48/64rpx | |

### 3.3 字体

- Web：`"Nunito Sans", "PingFang SC", "HarmonyOS Sans SC", "Microsoft YaHei", system-ui, sans-serif`（Nunito Sans 经 Google Fonts 引入，仅覆盖拉丁字符与数字）
- 小程序：系统字体栈（PingFang SC / HarmonyOS / MiSans 默认），不引外部字体
- 字阶（Web px / 小程序 rpx）：12/24，14/28，16/32，20/40，24/48，28/56

### 3.4 动效

- 过渡统一 `200ms ease-out`（按压/展开 200ms，hover 250ms）
- 尊重 `prefers-reduced-motion`（Web）；小程序端仅保留按压态 `hover-class`
- 反模式：低饱和配色、无过渡的生硬状态切换

## 4. 组件规范（双端对齐）

- **按钮**：主按钮 Primary 底白字圆角 sm；次按钮白底 Primary 字 + 1px 边框；危险按钮 danger 底。按下态换 `--shadow-press` + 深一档颜色
- **卡片**：白底、`--radius`、`--shadow-card`、无边框或 1px `--border`；卡片内边距 16px/32rpx
- **状态徽标**：清单/菜品状态用 `*-weak` 底 + 深色字的 pill 形徽标（OPEN 灰、CLAIMED 橙、COOKING 蓝、DONE 绿）
- **输入框**：白底、`--radius-sm`、聚焦时 2px `--ring` 外环（Web）/ 边框变 Primary（小程序）
- **Toast / 对话框**：圆角 lg、柔和阴影、居中或底部弹出
- **图标**：SVG（Lucide 风格线性图标），禁止 emoji 当图标；小程序端用本地 SVG 转的 PNG 或 WXSS 绘制
- **空状态**：居中插画式图形 + 引导文案 + 主按钮

## 5. 页面与导航

### 5.1 Web（10 页）

- **导航**：`App.vue` 重构为响应式双形态 —— <768px 底部 TabBar（首页/圈子/菜谱/通知/我的，含未读角标）；≥768px 顶部导航 + 内容 max-width 1024px 居中。分享页 `/s/{token}` 免登录无导航
- 页面清单：Login、Circles、SheetDetail、CookMode、Notifications、Pantry、Profile、RecipeDetail、RecipeGenerate、ShareView
- 所有页面按新 token 重构：卡片化布局、状态徽标、空状态、统一间距与字阶

### 5.2 小程序（11 页）

- tabBar（`app.json`）保持现有结构，图标与选中色换 Primary
- `app.wxss` 同步 token（rpx 版），全局 `.card` `.btn` 类重写
- 页面清单：circle、cook-mode、login、notifications、order-create、pantry、profile、recipe-detail、recipe-generate、sheet-detail、sheet-list
- 每页 wxss 按 token 重构：卡片阴影、圆角、状态徽标、空状态；布局结构基本保留，不做导航形态改造（小程序自带 tabBar）

## 6. 验收标准

来自 skill 交付检查清单：

- [ ] 无 emoji 当图标（使用 SVG/Lucide 风格）
- [ ] 所有可点元素 cursor-pointer + hover 过渡（Web）
- [ ] 亮色模式文字对比度 ≥4.5:1
- [ ] 键盘焦点可见（focus-visible + `--ring`）（Web）
- [ ] 尊重 prefers-reduced-motion（Web）
- [ ] 响应式验证 375 / 768 / 1024 / 1440px（Web）
- [ ] 双端无既有功能回归（登录、点单流转、认领状态机、通知轮询、分享页）

## 7. 范围外

- 不引入组件库（element-plus / vant-weapp）
- 不做暗色模式（引擎标注 Light supported，Dark conditional，本期不做）
- 不改任何业务逻辑、API、数据结构；纯样式与页面结构层重构
- 分享页 `/s/{token}` 仅换肤，不加导航
