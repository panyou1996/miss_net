# UI/UX 现状审计（Phase 1 起点）

> 基于当前 Kotlin/Compose 主线代码的首轮结构审计。

## 1. 当前导航结构

入口位于 `app/src/main/java/com/panyou/missnet/MainActivity.kt`

### Root tabs
- Home
- Actress
- Tags
- Library

### Secondary routes
- Search
- Settings
- CategoryDetail
- Player

## 2. 当前架构特征

### 优点
1. 已有明确 root navigation
2. Home / Library / Player / Search / Settings 已具备基本页面骨架
3. 现有项目已使用 Compose + Material 3
4. 已有主题、motion、组件、viewmodel 基础设施
5. Library > Downloads 已有真实下载/导出状态链路

### 主要问题

#### 2.1 信息架构偏“功能堆叠”
- Home / Actress / Tags / Library 的组织方式更接近功能分栏，而不是面向用户任务的产品结构
- 对标 GDrive 时，`Library / Downloads` 应提升为资产中心，而不仅是一个 tab 内子页

#### 2.2 顶栏和底栏虽统一，但页面内结构差异大
- MainActivity 中 TopBar/BottomBar 有统一外壳
- 但页面内部布局规则并未真正统一
- Home 偏内容陈列
- Library 混合 Likes/History/Downloads 三种完全不同密度和交互模型
- Player 为单独重交互页面，尚未纳入统一“状态系统”语言

#### 2.3 视觉语言偏“表达型”，与 GDrive 对标目标有差距
当前主题特征：
- 大圆角（extraLarge 40dp）
- 更偏 expressive / showcase 风格
- Hero carousel 比重较高

这和新的目标（克制、系统化、成熟、耐用）之间存在偏差。

#### 2.4 Downloads 已有真实状态，但 UX 还不够产品化
已有优点：
- 下载状态
- 导出状态
- 错误信息
- 打开导出文件

仍存在问题：
- 仍偏“功能卡片堆叠”
- 信息密度偏低
- 操作层级偏重按钮堆积
- 缺少更接近 GDrive 的资产管理感（排序、筛选、overview、section、overflow）

#### 2.5 Home 仍以内容区块堆叠为主
- hero + 多 section 横滑
- 可读性和信息效率不错，但“继续使用 / 最近内容 / 快速入口”还不够强
- 更像内容首页，而非高效率入口页

## 3. 当前页面机会点

### Home
建议强化：
- 继续播放
- 最近下载
- 最近使用
- 快速入口
- 降低 hero 的视觉占比

### Library
建议拆分为更清晰的资产层：
- 下载中
- 导出中
- 已完成
- 失败项
- 收藏/历史不应与下载页共享完全相同的心智层级

### Player
建议重点重构：
- 主体区 / 元信息区 / 操作区 / 状态区
- 减少按钮堆叠
- 加强状态闭环

### Search
建议升级为一级能力：
- 历史
- 建议
- 统一结果 item

### Settings
建议按分组重构：
- 播放
- 下载与导出
- 外观
- 关于

## 4. 当前主题系统审计

### 现状
- Color.kt 颜色 token 数量少，业务语义不明确
- Shape.kt 圆角偏大，偏 expressive
- Type.kt 基于 M3，但尚未建立“页面/列表/状态”语义层映射

### 建议方向
- 引入语义 token 而非只保留原始颜色
- 将组件圆角从“展示型大圆角”收敛到“系统型圆角”
- 为列表项、状态标签、标题层建立明确映射

## 5. 当前首要任务

1. 建立 token 草案
2. 建立组件清单与复用规则
3. 建立状态系统规范
4. 先重构 Home 与 Library / Downloads
