# MissNet UI/UX 执行 TODO 任务单（2026-03-13）

> 用途：给 agent / 开发者直接开工使用。
> 原则：先修真实性，再修结构，再做精致化。

---

## 0. 本轮目标

### 主线目标
1. 修掉假能力 / 假承诺
2. 提升首页与浏览页的可扫描性
3. 让 Library / Player / Browse 页更像成熟产品

### 验收底线
- `./gradlew :app:assembleDebug` 通过
- `./gradlew :app:lintDebug` 通过
- Home / Library 滚动不回归
- Player 播放 / 下载 / 收藏 / 分享不回归

---

## P0 - 必须先做

## TODO-001 修正搜索能力与文案不一致
- 优先级：P0
- 页面：Search
- 问题：当前只搜标题，但文案写“搜索视频、演员、标签”
- 方案：
  - 方案 A（推荐）：改 repository，支持 title / actors / tags 联搜
  - 方案 B：若本轮不做后端，则改文案为“搜索标题”
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`
  - `app/src/main/java/com/panyou/missnet/ui/screens/SearchScreen.kt`
- 验收：
  - 搜演员名/标签名时，结果与文案一致
  - 无“搜不到但文案承诺能搜”的情况
- 推荐 commit：
  - `fix: align search copy with actual search capability`

## TODO-002 修掉 Actress 伪筛选
- 优先级：P0
- 页面：Actress
- 问题：A-Z 筛选目前无真实效果
- 方案：
  - 方案 A：先移除 A-Z，仅保留 Hot
  - 方案 B：实现首字母/拼音筛选
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/ActressScreen.kt`
- 验收：
  - 所有筛选按钮都真正改变结果
  - 若无法实现，就不展示伪筛选 UI
- 推荐 commit：
  - `fix: remove fake actress alphabet filter`

## TODO-003 修正 Settings 登录/同步文案
- 优先级：P0
- 页面：Settings
- 问题：登录是 mock，本地 prefs，但文案写“同步到云端”
- 方案：
  - 若不做真同步：改为“本地保存偏好与状态”
  - 若要做真同步：补 Supabase 登录与同步闭环
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/SettingsScreen.kt`
  - `app/src/main/java/com/panyou/missnet/ui/viewmodel/SettingsViewModel.kt`
- 验收：
  - 所有设置文案都与真实行为一致
- 推荐 commit：
  - `fix: align settings sync copy with current implementation`

## TODO-004 处理 Player 投屏假能力
- 优先级：P0
- 页面：Player
- 问题：投屏按钮可点，但只是 toast “暂未接入”
- 方案：
  - 本轮直接隐藏
  - 或 disabled + 即将支持说明
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- 验收：
  - 主界面不存在“能点但没做”的显性功能
- 推荐 commit：
  - `fix: remove inactive cast action from player`

## TODO-005 修正 Settings 存储统计语义
- 优先级：P0
- 页面：Settings
- 问题：当前显示的是设备 data 分区使用量，不是 MissNet 自身占用
- 方案：
  - 统计 app cache / files / export 目录大小
  - 或把文案改成“设备存储参考”
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/viewmodel/SettingsViewModel.kt`
  - `app/src/main/java/com/panyou/missnet/ui/screens/SettingsScreen.kt`
- 验收：
  - 数值与标题语义一致
- 推荐 commit：
  - `fix: make settings storage metrics semantically correct`

---

## P1 - 结构与浏览效率

## TODO-006 收短 Home，强化“继续你的任务”
- 优先级：P1
- 页面：Home
- 问题：section 仍偏多，首页主线不够集中
- 目标：
  - 第一屏以“继续播放 / 最近任务 / 最近收藏”为主
  - 发现内容压缩为更少的精选模块
- 调整建议：
  - 保留 1 个 Hero
  - 保留 2~3 个内容 section
  - 其余通过“查看全部”进二级页
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt`
- 验收：
  - 首页首屏主任务更明确
  - section 数减少
- 推荐 commit：
  - `refactor: shorten home and prioritize task recovery`

## TODO-007 统一 Home 文案语言
- 优先级：P1
- 页面：Home
- 问题：中文 UI 混用英文 section：`New Release / Monthly Hot / ...`
- 方案：
  - 统一中文命名
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt`
- 验收：
  - 首页不再中英混搭
- 推荐 commit：
  - `style: localize home section titles consistently`

## TODO-008 修正 Home 元数据降级文案
- 优先级：P1
- 页面：Home / Cards
- 问题：仍有 `Unknown Artist`
- 方案：
  - 统一改成 `未知演员`
  - 对缺失封面加统一 placeholder
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/components/HomeComponents.kt`
  - `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt`
- 验收：
  - 不出现英文 fallback
  - 封面缺失视觉一致
- 推荐 commit：
  - `style: normalize fallback copy and placeholders on home`

## TODO-009 改造 Library 任务空态为“引导页”
- 优先级：P1
- 页面：Library / 任务
- 问题：空态太空，缺少下一步引导
- 方案：
  - 增加 CTA：去首页 / 去搜索 / 去播放器
  - 补轻量说明文案
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`
- 验收：
  - 空态下用户知道下一步可以做什么
- 推荐 commit：
  - `refactor: improve library task empty-state guidance`

## TODO-010 改造继续看页，不再用纯封面墙
- 优先级：P1
- 页面：Library / 继续看
- 问题：当前纯封面 grid，不利于快速决策
- 方案：
  - 改成 list / rich card
  - 显示：标题、进度、上次播放、剩余时长
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`
  - `app/src/main/java/com/panyou/missnet/data/local/LocalVideoStateStore.kt`
- 验收：
  - 用户可快速判断继续哪条内容
- 推荐 commit：
  - `refactor: make continue-watching library tab more scannable`

## TODO-011 为收藏空态增加引导动作
- 优先级：P1
- 页面：Library / 收藏
- 问题：空态语义清楚但没有行动入口
- 方案：
  - CTA：去首页发现 / 去标签页 / 去搜索
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt`
- 验收：
  - 收藏空态不再是终点页
- 推荐 commit：
  - `refactor: add exploration actions to favorites empty-state`

## TODO-012 提升 Tags 页信息价值
- 优先级：P1
- 页面：Tags
- 问题：每行几乎只有名字，决策信息不足
- 方案：
  - 增加视频数 / 热度 / 更新时间之一
  - leading icon 改成 tag icon 或移除
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/TagsScreen.kt`
  - `app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`
- 验收：
  - Tags 页从字典页变成可浏览页
- 推荐 commit：
  - `refactor: enrich tags list with ranking metadata`

## TODO-013 提升 Actress 卡片可扫描性
- 优先级：P1
- 页面：Actress
- 问题：卡片只有名字/封面，不够有浏览价值
- 方案：
  - 增加作品数 / 热门 / 最近更新
  - 无封面占位降噪
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/ActressScreen.kt`
- 验收：
  - 不是单纯头像墙
- 推荐 commit：
  - `refactor: add secondary metadata to actress cards`

## TODO-014 给 Category Detail 增加排序/筛选
- 优先级：P1
- 页面：Tag Detail / Category Detail
- 问题：现在更像“展示页”，而不是可操作浏览页
- 方案：
  - 增加排序：最新 / 最热
  - 可选轻筛选：字幕 / 无码 / 高清
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/CategoryDetailScreen.kt`
- 验收：
  - 详情页支持快速筛选决策
- 推荐 commit：
  - `refactor: add lightweight sort and filter controls to category detail`

---

## P2 - Player 与精致化

## TODO-015 Player 简介折叠
- 优先级：P2
- 页面：Player
- 问题：简介文字墙太长，挤压主操作区
- 方案：
  - 默认 2~3 行
  - 增加“展开更多 / 收起”
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- 验收：
  - 第一屏主要决策聚焦播放、下载、收藏
- 推荐 commit：
  - `refactor: collapse long player descriptions by default`

## TODO-016 Player 相关推荐语义明确化
- 优先级：P2
- 页面：Player
- 问题：当前更像原页切换视频，返回逻辑可能不符合直觉
- 方案：
  - 方案 A：点击相关推荐进入新 route
  - 方案 B：保留切换，但明确是“切换当前播放”
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
  - `app/src/main/java/com/panyou/missnet/MainActivity.kt`
- 验收：
  - 点击相关推荐后的导航心理模型清晰
- 推荐 commit：
  - `fix: clarify player recommendation navigation behavior`

## TODO-017 提升 Player 推荐项信息密度
- 优先级：P2
- 页面：Player
- 问题：相关推荐卡信息过少，点击价值感弱
- 方案：
  - 增加演员 / 标签 / 时长 / 更新信息
- 修改文件：
  - `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt`
- 验收：
  - 推荐列表可扫描、可比较
- 推荐 commit：
  - `style: enrich metadata on player recommendation items`

## TODO-018 统一深色页面的空白与边框密度
- 优先级：P2
- 页面：全局
- 问题：仍有一些“黑背景 + 大空区 + 容器边框”的叠加感
- 方案：
  - 适当减少 Divider
  - 用留白替代部分边框
  - 缩小 tab 内容区首屏空白
- 修改文件：
  - `HomeScreen.kt`
  - `LibraryScreen.kt`
  - `TagsScreen.kt`
  - `CategoryDetailScreen.kt`
  - `MainActivity.kt`
- 验收：
  - 页面不再显得“空黑”，而是“克制留白”
- 推荐 commit：
  - `style: reduce visual noise across dark surfaces`

## TODO-019 统一错误/空态截图资产管理
- 优先级：P2
- 问题：GDrive 中截图命名与内容有不一致
- 方案：
  - 建立截图命名规范
  - 重新导出真实页面截图
- 验收：
  - 截图资产与页面一一对应
- 推荐 commit：
  - `docs: normalize screenshot assets for ui review`

---

## P3 - 若还有余力

## TODO-020 增加高价值设置项
- 优先级：P3
- 页面：Settings
- 建议补充：
  - 仅 Wi‑Fi 下载
  - 默认倍速
  - 自动续播
  - 下载目录
  - 图片缓存策略
  - 导出策略说明
  - 通知开关
- 文件：
  - `SettingsScreen.kt`
  - `SettingsViewModel.kt`
- 推荐 commit：
  - `feat: add practical playback and download settings`

## TODO-021 给 Home / Browse 加 skeleton 占位
- 优先级：P3
- 问题：封面缺失或加载中时仍偏生硬
- 方案：
  - 增加统一 skeleton 占位卡
- 文件：
  - `ui/components/LoadingComponents.kt`
  - `HomeComponents.kt`
  - `VideoCard.kt`
- 推荐 commit：
  - `style: add consistent skeleton placeholders for media cards`

---

## 建议开发顺序（直接照着做）

### 第一轮
1. TODO-001
2. TODO-002
3. TODO-003
4. TODO-004
5. TODO-005

### 第二轮
6. TODO-006
7. TODO-007
8. TODO-008
9. TODO-009
10. TODO-010
11. TODO-011

### 第三轮
12. TODO-012
13. TODO-013
14. TODO-014
15. TODO-015
16. TODO-016
17. TODO-017

### 第四轮
18. TODO-018
19. TODO-019
20. TODO-020
21. TODO-021

---

## 每轮必须执行

```bash
export JAVA_HOME=/home/panyou/jdk-17.0.18+8
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

建议每轮都实机验证：
1. Home 上下滑动
2. Library 三个 tab 滑动与空态
3. Player 播放 / 下载 / 收藏 / 分享
4. 冷启动恢复
5. 搜索行为与文案是否一致

---

## 一句话目标

把 MissNet 从“视觉上已经接近成熟产品”推进到“能力真实、结构清楚、浏览高效、长期使用不累”的真正成熟产品。
