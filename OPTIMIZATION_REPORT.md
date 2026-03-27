# miss_net 项目优化方案报告

> 审查时间：2026-03-28
> 审查范围：`miss_net_native/` (Kotlin/Android, 64 个源文件, 约 11k 行)

---

## 目录

1. [Supabase Key 硬编码安全加固](#1-supabase-key-硬编码安全加固)
2. [ffmpeg-kit-full 依赖裁剪](#2-ffmpeg-kit-full-依赖裁剪)
3. [Repository 静默吞异常问题](#3-repository-静默吞异常问题)
4. [videoCache LRU 实现改进](#4-videocache-lru-实现改进)
5. [getHomePayload 重复请求优化](#5-gethomepayload-重复请求优化)
6. [Kotlin / Compose 版本对齐](#6-kotlin--compose-版本对齐)
7. [UI 单文件行数过多](#7-ui-单文件行数过多)
8. [缺少 UseCase/Interactor 层](#8-缺少-usecaseinteractor-层)
9. [缺少单元测试覆盖](#9-缺少单元测试覆盖)
10. [AnimatedTransitionApi 死代码清理](#10-animatedtransitionapi-死代码清理)

---

## 1. Supabase Key 硬编码安全加固

### 问题

`NetworkModule.kt` 中 Supabase publishable key 直接硬编码在源代码里：

```kotlin
// NetworkModule.kt
supabaseKey = "sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0"
```

任何反编译 APK 的人都能拿到此 key，可以直接访问你的 Supabase 项目。

### 方案一：Row Level Security (RLS) + 最小权限（推荐，最快落地）

**步骤：**

1. 登录 Supabase Dashboard → SQL Editor，执行：

```sql
-- 创建一个只读公开表策略（只允许读取，禁用写）
ALTER TABLE videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE actors ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags ENABLE ROW LEVEL SECURITY;

-- 允许匿名/已认证用户只读访问 videos
CREATE POLICY "Allow anon read videos"
ON videos FOR SELECT
USING (true);

-- 禁用匿名写入（确保没有 insert/update/delete 策略）
DROP POLICY IF EXISTS "Allow anon insert videos" ON videos;
DROP POLICY IF EXISTS "Allow anon update videos" ON videos;
DROP POLICY IF EXISTS "Allow anon delete videos" ON videos;
```

2. 你的 publishable key 设计上就是给客户端用的，**只授权 SELECT**，不授权写操作。即使 key 泄露，攻击者也只能读数据，无法写入或删除。

**优点：** 无需改动任何代码，5 分钟内生效  
**缺点：** 无法阻止恶意刷接口读数据

### 方案二：Cloudflare Workers 代理（中等成本）

```
用户 APK → Cloudflare Workers → Supabase (key 只在 Worker 环境变量里)
```

**Workers 代码示例 (`worker.ts`)：**

```typescript
export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    // 只代理特定路径
    if (!url.pathname.startsWith('/rest/v1/')) {
      return new Response('Not found', { status: 404 });
    }

    const apiUrl = `https://gapmmwdbxzcglvvdhhiu.supabase.co${url.pathname}${url.search}`;

    const response = await fetch(apiUrl, {
      method: request.method,
      headers: {
        'apikey': SUPABASE_SERVICE_ROLE_KEY, // Worker 环境变量
        'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        'Content-Type': 'application/json',
      },
      body: request.method !== 'GET' ? await request.text() : undefined,
    });

    return new Response(response.body, {
      status: response.status,
      headers: { 'Content-Type': 'application/json' },
    });
  },
};
```

**优点：** key 完全不在客户端，任意读写权限可控  
**缺点：** 需要额外部署 Workers，有免费额度

### 方案三：本地配置文件（最适合开发/测试阶段）

```kotlin
// BuildConfig.kt (gitignore 此文件)
object BuildConfig {
    const val SUPABASE_URL = "https://gapmmwdbxzcglvvdhhiu.supabase.co"
    const val SUPABASE_KEY = "your-key-here"
}

// NetworkModule.kt
@Provides
@Singleton
fun provideSupabaseClient(): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY,
        // ...
    )
}
```

并在 `.gitignore` 中加入：
```
app/buildconfig/
```

---

## 2. ffmpeg-kit-full 依赖裁剪

### 问题

```kotlin
// build.gradle.kts
implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
```

`ffmpeg-kit-full` 全功能包体积约 20-30MB（压缩后），即使限制了 ABI (`arm64-v8a`) 依然很大。

### 诊断：确认实际用到了哪些 ffmpeg 能力

在项目中搜索 ffmpeg 调用：

```bash
grep -r "ffmpeg" --include="*.kt" /path/to/miss_net_native/
```

常见使用场景及对应包：

| 实际用途 | 推荐包 | 体积节省 |
|---------|--------|---------|
| 视频元数据读取（时长/分辨率） | `ffmpeg-kit-full` 或 MediaMetadataRetriever | 视方案而定 |
| 视频 concat / trim | `ffmpeg-kit-min` (轻量版) | ~15MB |
| 视频转码/压缩 | 保留 full（无替代） | - |
| 音频提取 | `ffmpeg-kit-min` | ~15MB |

### 方案：按需替换

**步骤 1：** 先确认 `PublicVideoExporter.kt` 等文件具体用 ffmpeg 做什么：

```kotlin
// 读一下 PublicVideoExporter.kt，看实际调用方式
```

**步骤 2：** 如果只是 `-i input.mp4 -ss 00:01:00 -to 00:02:00 -c copy output.mp4`（trim，不转码）：

```kotlin
// build.gradle.kts — 替换为 min 版
implementation("com.arthenica:ffmpeg-kit-min:6.0-2")
```

**步骤 3：** 如果用到了实际编解码（转码、压缩），但实际场景不需要，考虑用 Android 原生 API：

```kotlin
// 用 MediaCodec + MediaMuxer 做无损 trim，不依赖 ffmpeg
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer

fun trimVideo(inputPath: String, outputPath: String, startUs: Long, endUs: Long) {
    val extractor = MediaExtractor()
    extractor.setDataSource(inputPath)
    val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    // 遍历轨道，只复制不解码（无损剪切）
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            muxer.addTrack(format)
            break
        }
    }
    muxer.start()
    // ... copy samples
    muxer.stop()
}
```

---

## 3. Repository 静默吞异常问题

### 问题

多处代码：

```kotlin
catch (_: Exception) {
    // 完全不知道是什么错误
}
```

线上出问题无法排查，debug 模式也没有日志。

### 方案：统一日志 + 改进错误类型

**步骤 1：** 引入 Kotlinx logger（已有 `android.util.Log` 可用）：

```kotlin
// 添加依赖
implementation("org.jetbrains.kotlinx:kotlinx-logging:1.7.3")
```

**步骤 2：** 创建统一错误处理工具：

```kotlin
// data/result/AppResult.kt
package com.panyou.missnet.data.result

import android.util.Log
import kotlin.math.min

private const val TAG = "MissNet Repository"

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val silent: Boolean = false
    ) : AppResult<Null>() {
        init {
            if (!silent) {
                Log.e(TAG, "Failure: $message", cause)
            }
        }
    }
    companion object Empty : AppResult<Null>()
}

inline fun <T> runCatching(receiver: () -> T): AppResult<T> {
    return try {
        receiver().toAppResult()
    } catch (e: Exception) {
        Log.e(TAG, "Exception in runCatching", e)
        AppResult.Failure("操作失败，请稍后重试。", e)
    }
}
```

**步骤 3：** 重写 `VideoRepository.kt` 中的 catch 块：

```kotlin
// 改前
catch (_: Exception) {
    emptyList()
}

// 改后
catch (e: Exception) {
    Log.e(TAG, "getRecentVideos failed", e)
    emptyList()
}
```

或使用包装方法：

```kotlin
// 统一入口
private suspend fun <T> safeApiCall(
    fallbackOnError: (suspend () -> T)? = null,
    errorMessage: String = "操作失败，请稍后重试。",
    block: suspend () -> T
): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: Exception) {
        Log.e(TAG, "$errorMessage", e)
        fallbackOnError?.let {
            try {
                AppResult.Success(it())
            } catch (fallbackError: Exception) {
                AppResult.Failure(errorMessage, e)
            }
        } ?: AppResult.Failure(errorMessage, e)
    }
}

// 使用示例
suspend fun getRecentVideos(...): List<Video> {
    return when (val result = safeApiCall(errorMessage = "内容加载失败") {
        supabase.postgrest["videos"].select { ... }.decodeList<Video>()
    }) {
        is AppResult.Success -> result.data
        else -> emptyList()
    }
}
```

---

## 4. videoCache LRU 实现改进

### 问题

```kotlin
// VideoRepository.kt
private val videoCache = LinkedHashMap<String, Video>()

private fun rememberVideo(video: Video) {
    if (video.id.isBlank()) return
    videoCache[video.id] = video
    if (videoCache.size > 1500) {
        val firstKey = videoCache.entries.firstOrNull()?.key ?: return
        videoCache.remove(firstKey) // 删除的是"最老"的（插入顺序），不是"最久未访问"
    }
}
```

`LinkedHashMap` 的 `firstOrNull()` 删除的是**最早插入**的条目，不是**最久未访问**（true LRU）。在缓存命中场景下，这个语义差异会导致热数据被提前驱逐。

### 方案：使用 Android LruCache

```kotlin
import android.util.LruCache

// 替换 private val videoCache = LinkedHashMap<String, Video>()
private val videoCache: LruCache<String, Video> = object : LruCache<String, Video>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(256)
) {
    override fun sizeOf(key: String, video: Video): Int {
        // 每个 Video 对象按 1KB 算，控制总缓存不超过 1MB 左右
        return 1
    }
}

// rememberVideo 改为
private fun rememberVideo(video: Video) {
    if (video.id.isBlank()) return
    videoCache.put(video.id, video)
}

// 读取时
private fun getCachedVideo(id: String): Video? = videoCache.get(id)
```

**优点：**
- `LruCache` 自动维护访问顺序，最近访问的项永远不会先被驱逐
- 内存控制精确（`maxMemory()/8` 通常是安全的应用内存上限的 1/8）

---

## 5. getHomePayload 重复请求优化

### 问题

`getHomePayloadResult` 的 RPC fallback 路径中，每个 section 独立调用：

```kotlin
val newVideos = getRecentVideosDirectResult(sectionLimit, "new")
val monthlyVideos = getRecentVideosDirectResult(sectionLimit, "monthly_hot")
val weeklyVideos = getRecentVideosDirectResult(sectionLimit, "weekly_hot")
// ... 7 个 section × 每次 800 条 = 5600 条记录重复拉取
```

当 RPC 失败时，fallback 行为是灾难性的重复请求。

### 方案：统一 fallback 结果 + 共享一次查询

```kotlin
suspend fun getHomePayloadResult(...): AppResult<HomePayload> {
    // ...
    } catch (rpcError: Exception) {
        // 只查一次 800 条，用内存 filter 分类
        val allVideos = when (val r = getRecentVideosDirectResult(limit = 800)) {
            is AppResult.Success -> r.data
            else -> return AppResult.Failure("首页加载失败，请稍后重试。", rpcError)
        }

        if (allVideos.isEmpty()) return AppResult.Empty

        val categorized = allVideos.groupBy { video ->
            when {
                // 用 tags/categories 判断 section
                video.tags.contains("monthly_hot") -> "monthly_hot"
                video.tags.contains("weekly_hot") -> "weekly_hot"
                video.tags.contains("uncensored") -> "uncensored"
                video.tags.contains("subtitled") -> "subtitled"
                video.tags.contains("vr") -> "vr"
                video.tags.contains("51cg") -> "51cg"
                else -> "new"
            }
        }

        return AppResult.Success(
            HomePayload(
                newVideos = categorized["new"].orEmpty().take(sectionLimit),
                monthlyVideos = categorized["monthly_hot"].orEmpty().take(sectionLimit),
                weeklyVideos = categorized["weekly_hot"].orEmpty().take(sectionLimit),
                uncensoredVideos = categorized["uncensored"].orEmpty().take(sectionLimit),
                subtitleVideos = categorized["subtitled"].orEmpty().take(sectionLimit),
                vrVideos = categorized["vr"].orEmpty().take(sectionLimit),
                chiguaVideos = categorized["51cg"].orEmpty().take(sectionLimit),
            ).also { cacheHomePayload(it) }
        )
    }
}
```

**效果：** 7 个 fallback 调用 → 1 次查询，结果按内存分类。

---

## 6. Kotlin / Compose 版本对齐

### 问题

| 组件 | 当前版本 | 说明 |
|------|---------|------|
| Kotlin | 未指定（隐式 1.9.x） | 由 Compose Compiler Extension 版本反推 |
| `kotlinCompilerExtensionVersion` | `1.5.4` | 对应 Kotlin 1.9.x |
| Compose BOM | `2025.02.00` | 对应 Kotlin 2.0+ |

这意味着你用的是 Kotlin 1.9 + Compose BOM 2025.02 的不兼容组合，可能导致运行时不稳定。

### 方案：统一到稳定版本组合（两组二选一）

**方案 A：Kotlin 1.9 + Compose BOM 2024.x（推荐当前稳定）**

```kotlin
// 项目根 build.gradle.kts
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}

// app/build.gradle.kts
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8" // 对应 Kotlin 1.9.22
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    // composeCompiler 需要额外指定版本以匹配 BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation:1.6.1")
}
```

**方案 B：Kotlin 2.0 + Compose BOM 2025.x（面向未来）**

```kotlin
// 项目根 build.gradle.kts
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// app/build.gradle.kts — 不再需要 kotlinCompilerExtensionVersion
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

composeOptions {
    // 已废弃，改为 plugins { id("org.jetbrains.kotlin.plugin.compose") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
}
```

**推荐方案 A**，当前生态更稳定。

---

## 7. UI 单文件行数过多

### 问题

| 文件 | 行数 | 建议上限 |
|------|------|---------|
| `LibraryScreen.kt` | 1092 | 300 |
| `HomeScreen.kt` | 743 | 300 |
| `PlayerScreen.kt` | 644 | 300 |

### 方案：按 Feature 拆分组件

**当前结构：**
```
ui/screens/
  LibraryScreen.kt  (1092 lines)
  HomeScreen.kt     (743 lines)
```

**目标结构：**
```
ui/screens/
  LibraryScreen.kt  (主入口，<100 lines，组装各子组件)
  library/
    LibraryViewModel.kt
    LibraryTabs.kt       (Tab 分段逻辑)
    DownloadList.kt      (下载列表)
    WatchHistoryList.kt  (观看历史)
    FavoriteList.kt      (收藏列表)
    components/
      DownloadCard.kt
      HistoryCard.kt

  HomeScreen.kt     (主入口，<100 lines)
  home/
    HomeViewModel.kt
    sections/
      NewSection.kt      (最新)
      HotSection.kt      (热门)
      RecommendSection.kt (推荐)
    components/
      VideoRow.kt
      SectionHeader.kt
```

**拆分示例：`HomeScreen.kt` 主入口变为：**

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoClick: (String) -> Unit,
    onSeeAllClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 最新
            item { NewSection(uiState.newVideos, onVideoClick, onSeeAllClick) }
            // 热门
            item { HotSection(uiState.weeklyVideos, onVideoClick) }
            // ... 其他 section
        }
    }
}

@Composable
private fun NewSection(
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    onSeeAllClick: (String) -> Unit
) {
    Column {
        SectionHeader(title = "最新", onSeeAllClick = { onSeeAllClick("new") })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video.id) })
            }
        }
    }
}
```

---

## 8. 缺少 UseCase/Interactor 层

### 问题

所有业务逻辑直接堆在 `VideoRepository` 里：
- `getHomePayloadResult` 混合了 RPC 调用 + 7 个 fallback + 多层降级
- `getActorsWithCoversResult` 混合了 RPC + fallback + 去重 + 排序 + 合并
- 无法单独测试每条业务路径

### 方案：引入 UseCase 层

```
presentation (ViewModel) → usecase (业务规则) → repository (数据获取)
```

**步骤 1：** 新建 `domain/usecase/` 目录

**步骤 2：** 抽取核心 UseCase：

```kotlin
// domain/usecase/GetHomePayloadUseCase.kt
class GetHomePayloadUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(
        sectionLimit: Int = 10,
        weeklyLimit: Int = 15,
        forceRefresh: Boolean = false
    ): HomePayload {
        return repository.getHomePayload(sectionLimit, weeklyLimit, forceRefresh)
    }
}

// domain/usecase/SearchVideosUseCase.kt
class SearchVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): AppResult<List<Video>> {
        if (query.isBlank()) return AppResult.Empty
        if (query.length < 2) return AppResult.Failure("搜索词至少2个字符")
        return repository.searchVideosResult(query, limit)
    }
}
```

**步骤 3：** ViewModel 只依赖 UseCase，不直接依赖 Repository：

```kotlin
// HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomePayloadUseCase: GetHomePayloadUseCase,
    private val getPopularActorsUseCase: GetPopularActorsUseCase
) : ViewModel() {
    // ...
    private suspend fun loadHome() {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val payload = getHomePayloadUseCase()
            _uiState.update { it.copy(homePayload = payload, isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
```

---

## 9. 缺少单元测试覆盖

### 问题

`testImplementation` 只有 Robolectric 基础依赖，没有实际测试文件。

### 方案：补充核心路径测试

**步骤 1：** 添加测试依赖：

```kotlin
// app/build.gradle.kts
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("app.cash.turbine:turbine:1.0.0")
testImplementation("androidx.arch.core:core-testing:2.2.0")
```

**步骤 2：** 创建测试目录结构：

```
app/src/test/java/com/panyou/missnet/
  data/
    repository/
      VideoRepositoryTest.kt
  domain/
    usecase/
      SearchVideosUseCaseTest.kt
      GetHomePayloadUseCaseTest.kt
```

**步骤 3：** `VideoRepositoryTest.kt` 示例：

```kotlin
@RunWith(AndroidRobolectricTestRunner::class)
class VideoRepositoryTest {

    @Mock private lateinit var mockSupabase: SupabaseClient
    @Mock private lateinit var mockPostgrest: Postgrest
    @Mock private lateinit var mockQuery: PostgrestQueryBuilder

    private lateinit var repository: VideoRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockSupabase.postgrest).thenReturn(mockPostgrest)
        whenever(mockPostgrest["videos"]).thenReturn(mockQuery)
        repository = VideoRepository(mockSupabase)
    }

    @Test
    fun `getRecentVideos returns empty list on error`() = runTest {
        whenever(mockQuery.select()).thenThrow(RuntimeException("Network error"))

        val result = repository.getRecentVideos()

        assertThat(result).isEmpty()
    }

    @Test
    fun `searchVideos with blank query returns empty`() = runTest {
        val result = repository.searchVideos("   ")

        assertThat(result).isEmpty()
    }

    @Test
    fun `videoCache evicts oldest when over capacity`() = runTest {
        val mockVideo = Video(id = "test-id", title = "Test")
        repeat(1501) { i ->
            repository.getVideoById("video-$i") // 先通过 repository 添加 cache
        }
        // 验证最早的 entry 已被驱逐
        val cached = repository.getVideoById("video-0")
        assertThat(cached).isNull()
    }
}
```

**最少需要覆盖的测试场景（按优先级）：**

| 优先级 | 场景 | 原因 |
|--------|------|------|
| P0 | `searchVideos` 空字符串 / 特殊字符 | 崩溃防御 |
| P0 | `getRecentVideos` 网络异常 fallback | 降级路径 |
| P0 | `videoCache` LRU 驱逐逻辑 | 内存安全 |
| P1 | `getHomePayloadResult` RPC 失败 → fallback | 首页降级 |
| P1 | `getActorsWithCovers` primary + fallback 合并去重 | 演员页准确性 |
| P2 | `Video.toHomePayload()` section 分类 | 数据转换 |

---

## 10. AnimatedTransitionApi 死代码清理

### 问题

`MainActivity.kt` 导入了未使用的 API：

```kotlin
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibilityScope
```

这些在 `@Composable fun MainApp()` 中没有被使用，属于死代码。

### 方案：移除死导入

**步骤 1：** 在 Android Studio / IntelliJ 中：`Code → Optimize Imports → Remove unused imports`

或在文件中手动删除：

```kotlin
// MainActivity.kt — 删除以下三行
import androidx.compose.animation.SharedTransitionScope        // 删除
import androidx.compose.animation.ExperimentalSharedTransitionApi // 删除
import androidx.compose.animation.AnimatedVisibilityScope     // 删除
```

**步骤 2：** 如果后续想使用 SharedTransition（分享页到详情页的共享元素转场），参考：

```kotlin
// 启用方式：NavHost 添加 sharedElement 修饰符
NavHost(
    navController = navController,
    startDestination = Screen.Home.route,
    modifier = Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(key = "video-card"),
        animatedVisibilityScope = this@AnimatedVisibilityScope
    )
) {
    composable(Screen.Home.route) { HomeScreen(...) }
    composable(Screen.Player.route) { PlayerScreen(...) }
}
```

**验证：** 改完后执行：

```bash
cd miss_net_native && ./gradlew :app:lintDebug 2>&1 | grep -i "unused\|dead\|warning"
```

确保没有新的 lint 警告。

---

## 执行优先级汇总

| # | 问题 | 优先级 | 预计工时 | 风险 |
|---|------|--------|---------|------|
| 1 | Supabase Key 安全 | 🔴 P0 | 1-2h | 低 |
| 2 | ffmpeg 裁剪 | 🔴 P0 | 2-4h | 中（需验证功能） |
| 3 | 静默吞异常 | 🔴 P0 | 1-2h | 低 |
| 4 | LRU 改进 | 🟡 P1 | 1h | 低 |
| 5 | 重复请求优化 | 🟡 P1 | 1-2h | 低 |
| 6 | 版本对齐 | 🟡 P1 | 2-3h | 中（涉及编译环境） |
| 7 | UI 文件拆分 | 🟡 P1 | 3-4h | 中（需功能验证） |
| 8 | UseCase 层 | 🟢 P2 | 4-6h | 中（架构改动） |
| 9 | 单元测试 | 🟢 P2 | 3-4h | 低 |
| 10 | 死代码清理 | 🟢 P2 | 0.5h | 极低 |

**建议执行顺序：** 1 → 3 → 2 → 4 → 5 → 6 → 7 → 10 → 9 → 8

（安全和稳定性问题优先，架构演进放后期）
