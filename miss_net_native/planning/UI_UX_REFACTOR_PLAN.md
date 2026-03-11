# UI/UX 重构执行计划

> 基于 `docs/UI_UX_REFACTOR_BLUEPRINT.md` 执行。

## 当前总目标
围绕 Google Drive App 体验，推进 MissNet 的 UI、UX、状态系统和关键 bug 修复。

## 阶段拆分

### Phase 1 - 基线与规范
状态：IN_PROGRESS

任务：
- [ ] 读取并整理 GDrive reference（TodoAI/reference）设计规律
- [x] 提炼 MissNet 当前页面差距清单
- [x] 定义 design tokens（颜色、字体、间距、圆角、层级）
- [x] 定义核心组件清单与复用策略
- [x] 定义页面/项目状态模型

交付物：
- 设计差距清单
- token 草案
- 组件与状态规范

### Phase 2 - Home / Library 首轮重构
状态：TODO

任务：
- [ ] Home 信息架构重排（基于任务恢复入口）
- [ ] Home section 体系重构
- [ ] Library / Downloads 重构为任务与资产状态中心
- [ ] 统一列表 item 结构与状态标签
- [ ] 落地筛选 / 排序 / overflow menu 规范

交付物：
- Home v1
- Library / Downloads v1

### Phase 3 - Player / Search / Settings 重构
状态：TODO

任务：
- [ ] Player 页面结构重整
- [ ] 状态区与操作区分级
- [ ] Search 一级能力化
- [ ] Settings 分组与样式统一

交付物：
- Player v1
- Search v1
- Settings v1

### Phase 4 - Bug 修复与全局打磨
状态：TODO

任务：
- [ ] 修复编译和类型问题
- [ ] 修复布局溢出和小屏问题
- [ ] 修复状态不同步问题
- [ ] 优化 loading / empty / error / success 反馈
- [ ] 真机回归验证主路径

交付物：
- 可测试 UI/UX 重构版
- 回归问题清单

## 当前优先级（Next Up）
1. 先把 Gemini 审阅修正写回文档
2. 输出 Home 首轮结构重构方案
3. 输出 Library / Downloads 首轮结构重构方案
4. 再进入代码实施

## 风险记录
- GDrive reference 尚未直接读取到，需要补齐素材对照
- UI 改造过程中容易引入页面回归
- Downloads/导出状态设计需与真实能力严格对齐，避免“假成功”

## 约束
- 不优先扩张新功能
- 不优先继续真正离线 HLS 导出
- 每一阶段都要保证可构建、可回归、可演示


## 审阅后修正

- [x] 将 Home 首轮定位修正为“任务恢复入口”
- [x] 将 Library / Downloads 首轮定位修正为“任务与资产状态中心”
- [x] 将 Player 首轮范围收敛为“信息层级 + 状态可见化”
- [x] 将动画策略收敛为“Material 默认 + 克制过渡”
- [x] 将状态系统首轮实现收敛到关键用户旅程
- [x] 将错误恢复策略调整为“可理解文案 + 一键恢复优先”


### 已完成的首轮落地
- [x] Home 重构为任务恢复入口：继续播放 / 最近任务 / 快速入口 / 降权发现内容
- [x] Library 默认聚焦任务页，并重排为：任务 / 继续看 / 收藏
- [x] Downloads 重构为任务与资产状态中心：进行中 / 需要处理 / 最近完成
- [x] 完成编译验证：assembleDebug
- [x] 完成静态检查：lintDebug
