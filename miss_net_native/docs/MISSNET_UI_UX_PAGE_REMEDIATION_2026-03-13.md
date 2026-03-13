# MissNet 逐页整改清单（基于 Google Drive `missav-app` 最新截图）

- 评审日期：2026-03-13
- 截图来源：`gdrive:missav-app/`
- 评审截图：
  - `01_home.png`
  - `02_actress.png`
  - `03_tags.png`
  - `04_library.png`
  - `05_settings.png`（疑似截图错误，内容看起来仍是 Library 空态）
  - `06_player.png`
  - `07_tag_detail.png`
  - `08_library_continue.png`
  - `09_library_favorite.png`
- 结合代码范围：
  - `MainActivity.kt`
  - `ui/components/MainTabScaffold.kt`
  - `ui/screens/HomeScreen.kt`
  - `ui/screens/ActressScreen.kt`
  - `ui/screens/TagsScreen.kt`
  - `ui/screens/LibraryScreen.kt`
  - `ui/screens/PlayerScreen.kt`
  - `ui/screens/SearchScreen.kt`
  - `ui/screens/SettingsScreen.kt`
  - `ui/viewmodel/SettingsViewModel.kt`
  - `data/repository/VideoRepository.kt`

---

## 一、总原则

### 本轮目标
1. 先修“假能力 / 假承诺”
2. 再修“首页主线与浏览效率”
3. 最后修“视觉精致度与细节一致性”

### 全局设计原则
- 克制：减少无效边框、重复卡片、过多强调色
- 系统感：同类页面统一容器、间距、图标尺寸、分隔规则
- 可扫描：让用户 1~2 秒知道“这页能做什么、下一步是什么”
- 状态明确：空态、失败态、不可用态不要只展示“空”，要给动作
- 操作可预测：所有显性操作都必须真的可用，或隐藏/禁用

---

## 二、P0（必须优先修）

### P0-1 搜索文案与真实能力不一致
- 问题：搜索页文案写“搜索视频、演员、标签”，但 `VideoRepository.searchVideos()` 当前只搜 `title`
- 影响：用户会直接觉得搜索“不可信”
- 建议：
  - 方案 A：后端改为 title / actors / tags 联搜
  - 方案 B：立刻把文案降级为“搜索标题”
- 文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/SearchScreen.kt`
  - `app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`
- 验收：演员名、标签名的搜索体验与文案一致

### P0-2 Actress A-Z 筛选是伪筛选
- 问题：`ActressScreen.kt` 中 `filteredActresses` 目前仍直接返回全部列表
- 影响：用户会误以为筛选可用，但点了没变化
- 建议：
  - 方案 A：删除 A-Z，保留 `Hot`
  - 方案 B：真正做首字母/拼音筛选
- 文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/ActressScreen.kt`
- 验收：切换筛选后列表有真实变化；若无真实筛选则 UI 中不出现 A-Z

### P0-3 Settings 登录 / 云同步文案强于实现
- 问题：`SettingsViewModel.signIn()` 当前只是本地 prefs mock，但页面文案写“将收藏与进度同步到云端”
- 影响：严重损伤可信度
- 建议：
  - 方案 A：做真实 Supabase 登录与同步
  - 方案 B：把文案改成“预留账号能力 / 本地偏好保存”
- 文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/SettingsScreen.kt`
  - `app/src/main/java/com/panyou/missnet/ui/viewmodel/SettingsViewModel.kt`
- 验收：文案与真实行为完全一致

### P0-4 Player 投屏按钮是显性假能力
- 问题：Player 有投屏入口，但点击后只是“暂未接入”
- 影响：高可见主界面中的假功能很伤完成度
- 建议：
  - 未接入前隐藏
  - 或 disabled + “即将支持”
- 文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- 验收：显性操作都能完成真实任务，或具有清晰禁用态

### P0-5 Settings“本地存储”语义不真实
- 问题：当前存储占用显示的是整个设备 data 分区，而不是 MissNet 自身占用
- 影响：用户会误解为 App 占用大小
- 建议：
  - 改为统计应用 cache/files/download/export 实际占用
  - 若暂时做不到，文案改成“设备可用空间参考”
- 文件：
  - `app/src/main/java/com/panyou/missnet/ui/viewmodel/SettingsViewModel.kt`
- 验收：数值与标题语义一致

### P0-6 `05_settings.png` 疑似错图
- 问题：Google Drive 中 `05_settings.png` 与 `04_library.png` 视觉上是同类 Library 空态，不像设置页
- 影响：设计评审资料混乱
- 建议：
  - 更换为真实设置页截图
  - 截图命名按页面实际内容校对
- 验收：截图资产与页面一一对应

---

## 三、P1（结构与浏览效率优化）

## 1. Home（`01_home.png`）

### 现状观察
- 顶部搜索 + 菜单 + 头像结构合理
- “继续播放”置顶方向正确
- 下方仍然堆叠多个内容 section：`New Release / Monthly Hot / ...`

### 问题清单
- [ ] 首页主线仍不够聚焦，“继续播放”不够像绝对第一优先级
- [ ] Section 数量偏多，首页仍偏长
- [ ] 中英文混用：顶部和底栏中文，section 英文
- [ ] 元数据质量问题直接暴露：`Unknown Artist`
- [ ] 封面缺失占位块太显眼

### 建议动作
- [ ] 把首页压缩为两大区：
  - 继续你的任务
  - 今日推荐 / 精选发现
- [ ] `New Release / Monthly Hot / Weekly Hot / Subtitled / Uncensored` 收敛成 2~3 个精选入口
- [ ] section 文案统一为中文
- [ ] `Unknown Artist` 改为 `未知演员`
- [ ] 封面失败占位改为统一 skeleton / 默认图，不要直接大块灰色
- [ ] 强化继续播放卡片：增加剩余时长 / 上次播放时间 / 明确 CTA

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/panyou/missnet/ui/components/HomeComponents.kt`

### 验收标准
- 首屏不超过 2 次滚动就能完成“恢复任务 or 开始发现”决策
- 首页 section 数明显减少
- 文案语言统一

---

## 2. Actress（`02_actress.png`）

### 现状观察
- 网格规整，暗色背景和容器一致性可以
- 封面缺失时用大红字占位
- 有 `Hot / A-Z` 筛选条

### 问题清单
- [ ] A-Z 当前仍是伪筛选（P0）
- [ ] 每个卡片信息过少，浏览效率低
- [ ] 封面缺失时的大红字过强，视觉躁动
- [ ] 页面更像“头像墙”，不像成熟浏览页

### 建议动作
- [ ] 先修真实筛选或删掉 A-Z
- [ ] 每个卡片增加一个弱元数据：作品数 / 热门 / 最近更新
- [ ] 无封面时改成低饱和字母头像或统一占位图
- [ ] 若保留字母筛选，增加 sticky filter / 当前筛选反馈

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/ActressScreen.kt`
- 视情况补充 actor 数据来源

### 验收标准
- 筛选真实可用
- 用户能在 3 秒内判断“值不值得点某位演员”

---

## 3. Tags（`03_tags.png`）

### 现状观察
- 页面已经比较克制
- 目前基本是纯文字列表

### 问题清单
- [ ] 所有 tag 行视觉信息几乎一致，缺少排序依据
- [ ] leading star icon 无实际语义差异
- [ ] 页面像“字典”，不像“可浏览入口”

### 建议动作
- [ ] 每个 tag 增加次级信息：视频数 / 热度 / 更新时间
- [ ] 把星标换成更中性的 tag icon，或直接移除 leading icon
- [ ] 可按主题分组：内容类型 / 题材 / 版本
- [ ] 支持排序：最热 / 最新 / 最多内容

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/TagsScreen.kt`
- `data/repository/VideoRepository.kt`（若需要 tag count / 热度）

### 验收标准
- 用户能根据列表次级信息快速选择标签

---

## 4. Tag Detail / Category Detail（`07_tag_detail.png`）

### 现状观察
- 顶部大 Hero + 下方列表
- 列表卡片比之前更稳定

### 问题清单
- [ ] 顶部 Hero 与第一条列表存在重复表达
- [ ] 缺少排序 / 筛选能力
- [ ] 列表项仍偏“只有标题 + 少量 meta”，决策效率一般

### 建议动作
- [ ] Hero 改成“精选 / 置顶 / 最新”而不是简单放大第一条
- [ ] 增加排序：最新 / 最热 / 时长 / 字幕 / 无码
- [ ] 列表项补足关键信息：演员 / 时长 / 更新时间 / 标签数量
- [ ] 让 Hero 与列表职责区分明确

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/CategoryDetailScreen.kt`

### 验收标准
- 用户能在详情页快速筛选内容，不只是被动下滑

---

## 5. Library - 任务（`04_library.png`）

### 现状观察
- tab 结构清晰：任务 / 继续看 / 收藏
- 当前任务空态只有一张卡

### 问题清单
- [ ] 空态太“空”，像内容没做完，不像状态页
- [ ] 没有下一步引导
- [ ] tabs 上方与内容中间有较大空黑区

### 建议动作
- [ ] 即使空态也保留：任务说明 + 下一步操作
- [ ] 增加 CTA：去首页 / 去播放器 / 去搜索
- [ ] 在空态页保留轻量任务说明，例如“下载、导出、失败恢复会在这里集中管理”
- [ ] 减少 tab 下方纯空白高度

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`

### 验收标准
- 空态页面也能指导用户下一步做什么

---

## 6. Library - 继续看（`08_library_continue.png`）

### 现状观察
- 目前是纯封面 grid

### 问题清单
- [ ] 用户无法快速判断哪个值得继续
- [ ] 缺少进度、标题、时间信息
- [ ] 继续看不应只是封面墙

### 建议动作
- [ ] 改成 rich list 或大卡片样式，不建议纯封面 grid
- [ ] 每项至少显示：标题 / 已看进度 / 上次播放时间 / 剩余时长
- [ ] 支持按“最近继续 / 剩余最少 / 最近更新”排序

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`
- `app/src/main/java/com/panyou/missnet/data/local/LocalVideoStateStore.kt`

### 验收标准
- 用户能在 2 秒内判断“继续哪一个”

---

## 7. Library - 收藏（`09_library_favorite.png`）

### 现状观察
- 空态语义清楚，但仍较空

### 问题清单
- [ ] 空态无下一步引导
- [ ] 与首页/搜索/标签页没有联动动作

### 建议动作
- [ ] 增加 CTA：去首页发现 / 去标签页 / 去搜索
- [ ] 如果收藏页非空，建议不要只看封面，可适度保留演员/标签信息

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`

### 验收标准
- 收藏空态不是终点页，而是引导页

---

## 8. Player（`06_player.png`）

### 现状观察
- 主次操作区已经成型
- 结构比之前成熟很多
- 标签区和相关推荐都已有雏形

### 问题清单
- [ ] 简介文字墙过重，第一屏压迫感偏强
- [ ] 投屏仍是假能力（P0）
- [ ] 顶部播放控制层仍略有“工程态拼装感”
- [ ] 相关推荐卡片过弱，点击预期不够明确
- [ ] 相关推荐目前是“原页切换视频”，返回逻辑可能不符合直觉

### 建议动作
- [ ] 简介默认折叠，只展示 2~3 行 + 展开
- [ ] 把标签 / 演员前置到简介之上或同级
- [ ] 未接入投屏前隐藏或禁用
- [ ] 推荐项增加次级信息：演员 / 标签 / 时长
- [ ] 明确相关推荐行为：
  - 方案 A：进入新 Player route
  - 方案 B：明确标注“切换当前播放”
- [ ] 控制层按钮透明度、间距、时间条样式继续收紧

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- `app/src/main/java/com/panyou/missnet/MainActivity.kt`

### 验收标准
- 第一屏主要决策只围绕：播放 / 下载 / 收藏 / 切换相关推荐
- 长文案不再挤压主要操作区
- 返回逻辑与用户直觉一致

---

## 9. Settings（截图待更正，先基于代码）

### 现状观察
- 视觉上应该已趋于整洁，但截图资源有误
- 代码中设置项主要是外观/隐私/存储

### 问题清单
- [ ] 登录与同步是 mock（P0）
- [ ] 存储说明不真实（P0）
- [ ] 缺少高价值设置项

### 建议动作
- [ ] 增加真正影响使用体验的设置：
  - 仅 Wi‑Fi 下载
  - 默认倍速
  - 自动续播
  - 下载目录
  - 图片缓存策略
  - 导出策略说明
  - 通知开关
- [ ] 替换错误截图，确保评审材料准确

### 文件落点
- `app/src/main/java/com/panyou/missnet/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/panyou/missnet/ui/viewmodel/SettingsViewModel.kt`

### 验收标准
- 设置页不只是“主题和开关页”，而是真正能提升使用体验

---

## 四、P2（精致化与系统感）

### P2-1 统一语言
- [ ] 首页 section 英文统一改中文
- [ ] 降级文案统一中文：`Unknown Artist -> 未知演员`
- [ ] 不支持 / 导出失败 / 最近完成等状态语言继续全局统一

### P2-2 继续减少视觉噪音
- [ ] 用留白替代部分 Divider
- [ ] 减少“外层大容器 + 内层小卡片 + 边框 + 分隔线”叠加
- [ ] 对纯空白区进行收缩，尤其 tab 内容区首屏空黑区

### P2-3 提升数据质量感知
- [ ] 封面失败统一 placeholder
- [ ] 演员/标签缺失统一降级文案
- [ ] 对缺少封面、标题过长、元数据缺失的内容做视觉兜底

---

## 五、推荐执行顺序

### 第一轮（真实性修复）
1. 搜索文案/搜索实现对齐
2. Actress 伪筛选修复或删除
3. 投屏假能力处理
4. Settings mock 文案修复
5. 存储说明修复

### 第二轮（结构收口）
1. Home 收短，突出任务恢复
2. Library 空态增加引导
3. Continue Watching 从封面墙改为可决策列表
4. Tag Detail 增加排序/筛选

### 第三轮（精致化）
1. 统一语言
2. 降低边框/分隔线密度
3. Player 简介折叠
4. 继续统一列表信息密度和卡片层级

---

## 六、建议提交节奏

1. `fix: align search and settings copy with actual capabilities`
2. `fix: remove fake actress alphabet filtering or implement real filtering`
3. `refactor: shorten home and prioritize task recovery`
4. `refactor: improve library empty states and continue-watching scanability`
5. `refactor: add sort/filter controls to category detail`
6. `style: unify final copy and reduce visual noise across dark surfaces`

---

## 七、最终目标（一句话）

让 MissNet 从“已经有成熟产品外观的内容 App”，继续进化为“能力真实、主线清楚、浏览高效、长期使用不累”的成熟产品。
