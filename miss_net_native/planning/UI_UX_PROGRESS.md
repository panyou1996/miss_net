# UI/UX 重构进度追踪

## 总状态
- 蓝图：已建立
- 执行计划：已建立
- 开发：Phase 1 进行中

## 已完成
- [x] 完成 Home / Library 第二轮视觉收敛与状态打磨
- [x] 明确重构北极星目标：对标 Google Drive App
- [x] 形成《MissNet UI/UX 重构蓝图 v1》
- [x] 形成阶段性执行计划
- [x] 明确本轮不优先继续离线 HLS 导出
- [x] 完成当前 UI 架构首轮审计
- [x] 完成设计系统草案 v1（token / 组件 / 状态模型）
- [x] 完成 Gemini 视角审阅并吸收关键修正意见
- [x] 完成 Home 首轮 UI 重构落地（任务恢复入口）
- [x] 完成 Library / Downloads 首轮 UI 重构落地（任务与资产状态中心）
- [x] assembleDebug 通过
- [x] lintDebug 通过
- [x] 修复 Home / Library 上下滑动失效问题（滚动链路收敛）
- [x] 完成 Home 第二轮层级优化（摘要卡 / 最近收藏 / 入口层级）
- [x] 完成 Downloads 第二轮扫描效率优化（排序 / 分组计数 / 失败提示强化）

## 进行中
- [ ] 整理 GDrive reference 设计规律
- [x] 建立设计 token 与组件规范

## 待开始
- [ ] Home 首轮重构
- [ ] Library / Downloads 首轮重构
- [ ] Player 重构
- [ ] Search 重构
- [ ] Settings 重构
- [ ] bug 修复与回归

## 阻塞项
- 尚未直接读取 GDrive reference 文件夹内容
- 需要后续将 reference 里的页面截图与现有页面一一对照

## 决策记录
- 主 tab 页面因自定义顶栏未真正消费 scrollBehavior，却给内部滚动容器挂了 nestedScroll，导致滚动链路脆弱；已对 Home / Library 收敛滚动链路并恢复 Library 内容承载层。

## 决策记录
- 以 Google Drive App 作为 UI/UX 对标对象
- 当前阶段优先做 UI/UX 主线和 bugs 修复
- 先做蓝图、计划、追踪，再进入页面级实施
- Home 首轮被重新定义为“任务恢复入口”
- Library / Downloads 首轮被重新定义为“任务与资产状态中心”
- Player 首轮范围收敛，优先处理信息层级和状态可见化
