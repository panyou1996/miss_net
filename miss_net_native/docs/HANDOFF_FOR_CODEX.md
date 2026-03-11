# MissNet 交接说明（给本地 Codex CLI）

## 一、接手目标
本仓库当前进入“UI/UX 主线重构 + 稳定性修复”的持续阶段。
接手后建议优先顺序：

1. 保持当前可编译、可打包、可测试状态
2. 延续 Home / Library / Downloads 的产品化重构
3. 继续推进 Player 第一轮轻量收口
4. 在需要时再推进更彻底的主 tab 页面骨架统一

## 二、必须先读的文档
优先阅读以下文件：

- `docs/UI_UX_REFACTOR_BLUEPRINT.md`
- `docs/UI_UX_CURRENT_AUDIT.md`
- `docs/UI_UX_DESIGN_SYSTEM_V1.md`
- `docs/UI_UX_REVIEW_NOTES_GEMINI.md`
- `planning/UI_UX_REFACTOR_PLAN.md`
- `planning/UI_UX_PROGRESS.md`
- `docs/PROJECT_SUMMARY.md`
- `docs/CURRENT_STATUS_AND_NEXT_STEPS.md`

## 三、当前主线判断
### 已稳定交付过的方向
- Home 首屏说明性文案已移除，整体更干净
- Home 已具备任务恢复入口雏形
- Library / Downloads 已具备任务与资产状态中心雏形
- Home / Library 的滚动问题修过一轮，不能轻易回退

### 当前未完成但重要的方向
- Player 第一轮轻量收口（信息层级、主次操作区、状态语言统一）
- MainActivity 主 tab 页面骨架统一（但要以不破坏当前稳定性为前提）
- 设计系统统一项择优吸收，不要一次性铺太猛

## 四、接手约束
1. **不要为统一而统一**：先保证稳定版 APK 可持续交付
2. **Home / Library 滚动链路是高风险区**：任何主 scaffold / padding / nestedScroll 调整都要谨慎
3. **Player 改造先做轻量收口**：不要先碰深业务逻辑
4. **下载 / 导出状态语言要和 Library 保持一致**
5. 每一轮都应尽量跑：
   - `./gradlew :app:assembleDebug`
   - `./gradlew :app:lintDebug`

## 五、建议的接手顺序
### Phase A（优先）
- 审查当前 MainActivity、HomeScreen、LibraryScreen、PlayerScreen
- 明确哪些改动已经稳定交付，哪些还只是尝试方向
- 在不破坏现状的前提下推进 Player 第一轮收口

### Phase B
- 轻量统一主 tab 骨架
- 优先抽公共外壳，不做过深结构重写

### Phase C
- 持续收紧 Design System：badge、容器、shape、间距 token
- 优先在 Home / Library / Player 生效

## 六、关键文件
- `app/src/main/java/com/panyou/missnet/MainActivity.kt`
- `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`
- `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- `app/src/main/java/com/panyou/missnet/ui/theme/Shape.kt`
- `app/src/main/java/com/panyou/missnet/ui/theme/ContainerTokens.kt`
- `app/src/main/java/com/panyou/missnet/ui/components/Badge.kt`
- `app/src/main/java/com/panyou/missnet/ui/components/MainTabScaffold.kt`

## 七、Git 交接建议
- 先从本次 handoff 提交开始
- 接手后优先用小步提交
- 每轮交付前产出可测 APK
