# miss_net UI/UX 专项审核报告

> 审查时间：2026-03-28
> 审查范围：`miss_net_native/app/src/main/java/com/panyou/missnet/ui/`
> 基于：设计系统草案 + 源码分析 + 已知缺陷

---

## 目录

1. [导航架构](#1-导航架构)
2. [HomeScreen 滚动性能](#2-homescreen-滚动性能)
3. [Player 播放器 UI](#3-player-播放器-ui)
4. [加载/错误/空状态](#4-加载错误空状态)
5. [收藏/历史/下载列表](#5-收藏历史下载列表)
6. [图片加载体验](#6-图片加载体验)
7. [搜索体验](#7-搜索体验)
8. [设置页](#8-设置页)
9. [设计 Token 一致性](#9-设计-token-一致性)
10. [可访问性](#10-可访问性)

---

## 1. 导航架构

### 问题 1.1：BottomNavigation 没有持久化状态

**文件：** `MainActivity.kt`

**现状：** App 切换后台再切回来，导航状态有时会丢失或重置到首页。

**原因：** `rememberNavController()` 没有配合 `NavHost` 设置 `saveState` / `restoreState`。

**修复：**

```kotlin
// MainActivity.kt — 修改 NavHost
val navController = rememberNavController()

NavHost(
    navController = navController,
    startDestination = Screen.Home.route,
    saveState = true,          // 新增：保存状态
    restoreState = true        // 新增：恢复状态
) {
    composable(Screen.Home.route) { HomeScreen(...) }
    composable(Screen.Library.route) { LibraryScreen(...) }
    // ...
}
```

**Step by Step：**
1. 打开 `MainActivity.kt`
2. 找到 `NavHost(`
3. 添加 `saveState = true` 和 `restoreState = true`
4. 验证：切后台 5 分钟，再打开，确认不在首页

---

## 2. HomeScreen 滚动性能

### 问题 2.1：LazyColumn 嵌套 LazyRow 导致合成度嵌套

**文件：** `HomeScreen.kt`（743行）

**现状：** `LazyColumn` 内嵌多个 `LazyRow`（section），每个 `LazyRow` 内部又是 `items()` 加载 `VideoCard`。在低端机上滚动帧率差。

**原因：** 每帧都在做嵌套 item 合成，缺少 `key` 和 `contentType` 优化。

**修复（分两步）：**

**Step 1：** 给所有 `items()` 添加稳定 key：

```kotlin
// HomeScreen.kt — 现有
items(videos) { video -> VideoCard(...) }

// 改为
items(videos, key = { it.id }) { video -> VideoCard(...) }
```

**Step 2：** 添加 `contentType` 区分卡片类型，减少重组：

```kotlin
items(
    videos,
    key = { it.id },
    contentType = { video -> "video_card" }  // 或按类型区分
) { video -> VideoCard(...) }
```

---

### 问题 2.2：首页每次进来都重新请求

**文件：** `HomeScreen.kt`

**现状：** 每次进首页都发 API 请求，没有利用 `PullToRefreshBox` 的状态判断。

**修复：**

```kotlin
// HomeScreen.kt — ViewModel 中添加
private val _homeRefreshTrigger = MutableStateFlow(0)

fun refresh() {
    viewModelScope.launch {
        _homeRefreshTrigger.value++  // 触发刷新
    }
}

// 监听刷新触发器，只在真正需要时请求
LaunchedEffect(_homeRefreshTrigger.value) {
    loadHomePayload(forceRefresh = true)
}
```

---

## 3. Player 播放器 UI

### 问题 3.1：PlayerControls 覆盖层没有渐进式显示/隐藏

**文件：** `ui/screens/player/PlayerControls.kt`（288行）

**现状：** 点击屏幕控制栏显示/隐藏是瞬时的，没有过渡动画，用户体验粗糙。

**修复（Step by Step）：**

```kotlin
// PlayerControls.kt — 添加 AnimatedVisibility
val controller by viewModel.controller.collectAsState()
var controlsVisible by remember { mutableStateOf(true) }

AnimatedVisibility(
    visible = controlsVisible,
    enter = fadeIn(animationSpec = tween(200)),
    exit = fadeOut(animationSpec = tween(200))
) {
    // 控制栏 UI
    PlayerControlOverlay(...)
}
```

**Step by Step：**
1. 打开 `PlayerControls.kt`
2. 找到控制栏的 `Box` 包装
3. 用 `AnimatedVisibility` 替换原来的 `Box`
4. 添加 `tween(200)` 的 fadeIn/fadeOut

---

### 问题 3.2：播放器封面图和实际视频切换时有视觉跳跃

**文件：** `PlayerScreen.kt`（644行）

**现状：** 进入 Player 时，先显示封面图，等 m3u8 解析完再切换到视频，中间没有平滑过渡。

**修复（Step by Step）：**

```kotlin
// PlayerScreen.kt
val playerState by viewModel.playerState.collectAsState()
val fadeAlpha by animateFloatAsState(
    targetValue = if (playerState == PlayerState.Playing) 1f else 0f,
    animationSpec = tween(300),
    label = "video-fade"
)

Box(modifier = Modifier.fillMaxSize()) {
    // 封面
    MissNetCoverImage(
        coverUrl = video.coverUrl,
        modifier = Modifier
            .fillMaxSize()
            .alpha(1f - fadeAlpha)
    )
    // 视频
    VideoPlayer(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeAlpha)
    )
}
```

---

## 4. 加载/错误/空状态

### 问题 4.1：Loading 状态使用 `CircularProgressIndicator` 过于单调

**文件：** `LoadingComponents.kt`（297行）

**现状：** 全局统一使用同一个 `MissNetLoading`，但不同页面场景（首页、搜索、收藏）没有差异化提示文字。

**修复：** 全局已统一 `MissNetLoading` 可接受，但建议扩展场景定制：

```kotlin
// MissNetLoading.kt — 新增场景枚举
enum class LoadingScenario {
    HOME, SEARCH, LIBRARY, ACTOR_DETAIL, PLAYER
}

@Composable
fun MissNetLoading(
    scenario: LoadingScenario = LoadingScenario.HOME,
    modifier: Modifier = Modifier
) {
    val (title, subtitle) = when (scenario) {
        LoadingScenario.HOME -> "正在整理页面内容" to "请稍候，当前页面正在同步最新状态。"
        LoadingScenario.SEARCH -> "正在搜索" to "正在从海量内容中筛选..."
        LoadingScenario.LIBRARY -> "正在加载资源库" to "资源整理中，请稍候。"
        LoadingScenario.PLAYER -> "正在加载视频" to "视频解析中，请稍候。"
    }
    // ... 原有 UI 用新 title/subtitle
}
```

**Step by Step：**
1. 打开 `LoadingComponents.kt`
2. 添加 `enum class LoadingScenario`
3. 给 `MissNetLoading` 添加 `scenario` 参数
4. 用 `when` 映射场景化文字
5. 各 Screen 调用时传入对应场景

---

### 问题 4.2：错误状态提示文案没有区分错误类型

**文件：** `LoadingComponents.kt`

**现状：** `MissNetErrorState` 只有一个通用错误文案，无法帮助用户判断是网络问题还是服务器问题。

**修复：**

```kotlin
enum class ErrorType {
    NETWORK, SERVER, TIMEOUT, UNKNOWN
}

@Composable
fun MissNetErrorState(
    errorType: ErrorType,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (title, subtitle) = when (errorType) {
        ErrorType.NETWORK -> "网络连接失败" to "请检查网络后点击重试。"
        ErrorType.SERVER -> "服务器异常" to "服务端开小差了，请稍后再试。"
        ErrorType.TIMEOUT -> "请求超时" to "加载超时，请重试。"
        ErrorType.UNKNOWN -> "暂时无法显示内容" to "遇到未知错误，请稍后重试。"
    }
    // ...
}
```

---

## 5. 收藏/历史/下载列表

### 问题 5.1：LibraryScreen 单文件 1092 行，Tab 切换没有状态保持

**文件：** `LibraryScreen.kt`

**现状：** 在「下载/历史/收藏」三个 Tab 间切换，每次都会重新 recompose，滚动位置不保持。

**修复（Step by Step）：**

```kotlin
// LibraryScreen.kt — 改造 Tab 结构
var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.DOWNLOADS) }
val scrollPositions = rememberSaveable { mutableStateMapOf<LibraryTab, Int>() }

LazyColumn(
    state = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPositions[selectedTab] ?: 0
    )
) {
    // 监听滚动位置变化
    LaunchedEffect(selectedTab) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { scrollPositions[selectedTab] = it }
    }
}

// Tab 切换时恢复位置
LaunchedEffect(selectedTab) {
    lazyListState.scrollToItem(scrollPositions[selectedTab] ?: 0)
}
```

---

## 6. 图片加载体验

### 问题 6.1：封面图加载时没有骨架屏/占位

**文件：** `MediaImage.kt`（47行）

**现状：** 封面图加载时直接显示空白，加载完成后直接跳变。

**修复：**

```kotlin
// MediaImage.kt — 使用 Coil 的 AsyncImage 做骨架屏
import coil.compose.AsyncImage
import androidx.compose.foundation.ContentDrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme

AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(coverUrl)
        .crossfade(true)
        .build(),
    contentDescription = title,
    modifier = modifier,
    onLoading = { loadingModifier ->
        // 显示骨架屏
        Box(loadingModifier.background(MaterialTheme.colorScheme.surfaceVariant))
    },
    onSuccess = { successModifier ->
        // 可选：加载成功后闪一下
    },
    onError = { errorModifier ->
        Box(errorModifier.background(MaterialTheme.colorScheme.errorContainer))
    }
)
```

**Step by Step：**
1. 打开 `MediaImage.kt`
2. 替换 `MissNetCoverImage` 的实现，底层使用 `AsyncImage`
3. 配置 `crossfade(true)` 和 `onLoading` 骨架屏

---

## 7. 搜索体验

### 问题 7.1：搜索没有防抖，连续输入每个字符都触发请求

**文件：** `SearchScreen.kt`（312行）

**现状：** 用户输入时每打一个字就发一次搜索请求，浪费 API 配额且体验差。

**修复（Step by Step）：**

```kotlin
// SearchViewModel.kt — 添加 debounce
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

init {
    viewModelScope.launch {
        _searchQuery
            .debounce(350)  // 350ms 防抖
            .filter { it.length >= 2 }
            .distinctUntilChanged()
            .collectLatest { query ->
                doSearch(query)
            }
    }
}

fun onQueryChange(query: String) {
    _searchQuery.value = query
    // 即时更新 UI，不等待 debounce
    _uiState.update { it.copy(searchQuery = query) }
}
```

---

## 8. 设置页

### 问题 8.1：设置项没有分组，视觉上杂乱

**文件：** `SettingsScreen.kt`（389行）

**现状：** 所有 ListItem 平铺，没有分组标题和分割线。

**修复（Step by Step）：**

```kotlin
// SettingsScreen.kt — 添加分组
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                content()
            }
        }
    }
}

// 使用
SettingsSection(title = "显示设置") {
    ListItem(...)
    ListItem(...)
}
SettingsSection(title = "存储") {
    ListItem(...)
}
```

---

## 9. 设计 Token 一致性

### 问题 9.1：Badge 组件 Token 存在但其他地方直接写死数值

**文件：** `Badge.kt`，多处引用

**现状：** `BadgeTokens` 定义了标准值，但 `VideoCard.kt`、`HomeComponents.kt` 等多处直接写 `padding(8.dp)`、`size(32.dp)` 等，没有引用 Token。

**修复：**

```kotlin
// BadgeTokens.kt — 已有定义
object BadgeTokens {
    val OverlayBadgeSize = 32.dp
    val OverlayBadgeIconSize = 18.dp
    val StatusBadgeHeight = 24.dp
    // ...
}

// VideoCard.kt — 改前
modifier = Modifier.padding(8.dp)

// 改后（引用 Token）
modifier = Modifier.padding(BadgeTokens.OverlayBadgePadding)
```

**Step by Step：**
1. 搜索项目中对 `8.dp` 的直接使用（特别是在 ui/components/ 下）
2. 替换为对应的 Token 值
3. 如果 Token 不存在，先补充定义再替换

---

## 10. 可访问性

### 问题 10.1：Image 没有 contentDescription

**文件：** `MissNetCoverImage.kt`（在 `MediaImage.kt` 中）

**现状：** `Image` composable 如果没有设置 `contentDescription`，TalkBack 会读不出内容。

**修复：**

```kotlin
// MissNetCoverImage — 确认 contentDescription 传到了底层
@Composable
fun MissNetCoverImage(
    coverUrl: String?,
    contentDescription: String?,  // 非空
    modifier: Modifier = Modifier
) {
    MissNetImage(
        imageModel = { coverUrl },
        modifier = modifier,
        contentDescription = contentDescription,  // 确保不为 null
        // ...
    )
}
```

**Step by Step：**
1. 全局搜索 `contentDescription = null`
2. 对于装饰性图片设置为 `contentDescription = null`
3. 对于有意义的图片（如封面、头像）确保传入了描述文本

---

## 执行优先级汇总

| # | 问题 | 优先级 | 预计工时 | 风险 |
|---|------|--------|---------|------|
| 7 | 搜索防抖 | 🔴 P0 | 1h | 低 |
| 2.1 | LazyColumn key 优化 | 🔴 P0 | 0.5h | 低 |
| 1 | NavController 状态保持 | 🟡 P1 | 1h | 低 |
| 6 | 骨架屏图片加载 | 🟡 P1 | 2h | 中 |
| 3.1 | Player 控制栏动画 | 🟡 P1 | 1h | 低 |
| 5.1 | Library Tab 位置保持 | 🟡 P1 | 1.5h | 中 |
| 3.2 | Player 封面过渡 | 🟢 P2 | 1h | 低 |
| 4.1 | Loading 场景化 | 🟢 P2 | 1h | 低 |
| 8.1 | 设置页分组 | 🟢 P2 | 1h | 低 |
| 9.1 | Token 数值统一引用 | 🟢 P2 | 2h | 低 |
| 4.2 | ErrorType 区分 | 🟢 P2 | 0.5h | 低 |
| 10.1 | contentDescription | 🟢 P2 | 1h | 低 |
