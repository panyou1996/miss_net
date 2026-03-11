# UI/UX 设计系统草案 v1

> 用于指导 UI/UX 重构第一轮实施。目标风格：Material 3 x Google Drive 式系统感。

## 1. Design Tokens

### 1.1 Spacing
推荐统一使用：
- 4
- 8
- 12
- 16
- 20
- 24
- 32

使用建议：
- item 内部：8 / 12 / 16
- section 间距：16 / 24
- 页面边距：16
- 大区块分隔：24 / 32

### 1.2 Radius
建议目标值：
- xs: 8
- sm: 12
- md: 16
- lg: 20
- xl: 24

建议调整方向：
- 减少 32~40 这类高展示型圆角在全局的泛滥
- 保留少量大容器使用 24
- 列表项 / 卡片主要落在 12~20

### 1.3 Elevation
- 默认列表容器：低 elevation
- 可交互卡片：低到中
- 大型 hero/重点卡片：中
- 避免处处高阴影

### 1.4 Typography Roles
建立语义角色：
- PageTitle
- SectionTitle
- ItemTitle
- ItemMeta
- StatusLabel
- HelperText
- ActionLabel

建议映射：
- PageTitle -> titleLarge / headlineSmall
- SectionTitle -> titleMedium
- ItemTitle -> titleMedium
- ItemMeta -> bodySmall / bodyMedium
- StatusLabel -> labelMedium
- HelperText -> bodySmall

## 2. Color Semantics

建议新增语义层：
- appBackground
- pageSurface
- cardSurface
- subtleContainer
- primaryAction
- successState
- warningState
- errorState
- infoState
- statusNeutral

原则：
- 状态色只用于状态，不要同时承担品牌与大面积背景
- Surface 层级必须可区分，但不能乱

## 3. Core Components

### 必做组件
1. AppHeader
2. SearchHeader / SearchEntry
3. SectionHeader
4. AssetListItem
5. MediaCard
6. StatusChip
7. EmptyState
8. ErrorState
9. LoadingSkeleton
10. OverflowMenu
11. ActionBottomSheet
12. Snackbar/InlineNotice
13. ProgressBlock

## 4. Page Templates

### 4.1 Entry Page Template（Home）
- Header
- Quick actions
- Continue / Recent
- Main sections

### 4.2 Asset Page Template（Library / Downloads）
- Header
- Filter / Sort
- Status summary
- List sections
- Overflow actions

### 4.3 Detail Page Template（Player）
- Top bar
- Main content
- Metadata
- Primary actions
- Status / recovery

## 5. State Model

### 页面级
- initial
- loading
- loaded
- empty
- error
- refreshing

### 资产项级
- queued
- downloading
- paused
- completed
- exporting
- exported
- failed
- unsupported

## 6. 当前实施优先级

### P1
- AppHeader 规范
- AssetListItem 规范
- StatusChip 规范
- Home 页面结构模板
- Library / Downloads 页面结构模板

### P2
- Player 模板
- Search 模板
- Settings 分组模板
