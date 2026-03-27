# miss_net 最终实施方案

> 整合来源：
> - `OPTIMIZATION_REPORT.md`（原始 10 条，Arc 撰写）
> - `OPTIMIZATION_REPORT_REVISED.md`（GPT-5.4 复审修订版）
> - `UI_UX_REVIEW.md`（UI/UX 专项审核，Arc 撰写）
>
> 整合时间：2026-03-28

---

## 一、核心结论（GPT-5.4 复审修正）

原报告有 3 处明显错误，需先纠正再执行：

| # | 原报告问题 | 修正结论 |
|---|-----------|---------|
| 1 | 把 Supabase key 硬编码定性为"直接安全问题" | 真正问题：**RLS/权限边界**，而非 key 本身 |
| 2 | ffmpeg-kit-full 可直接换成 min 版 | 错误！当前代码确实在用 full 做 HLS remux，需先验证具体调用再定 |
| 10 | AnimatedTransitionApi 是死代码 | 错误！项目确实在用 SharedTransition，`AnimatedVisibilityScope` 等是真实依赖 |

---

## 二、综合优先级总表

### 🔴 P0（立即执行，安全/稳定性）

| 编号 | 问题 | 来源 | 工时 | 风险 |
|------|------|------|------|------|
| S-1 | Supabase RLS 权限收紧 + 安全审计 | OPTIMIZATION_REPORT_REVISED | 1-2h | 低 |
| S-2 | VideoRepository 异常日志补全（`catch(_)` 改进） | OPTIMIZATION_REPORT_REVISED | 1-2h | 低 |
| U-1 | 搜索防抖（SearchViewModel debounce 350ms） | UI_UX_REVIEW | 1h | 低 |

### 🟡 P1（下一个 Sprint）

| 编号 | 问题 | 来源 | 工时 | 风险 |
|------|------|------|------|------|
| S-3 | `getHomePayloadResult()` fallback 重复请求优化 | OPTIMIZATION_REPORT_REVISED | 1-2h | 低 |
| S-4 | Kotlin / Compose 版本一致性对齐 | OPTIMIZATION_REPORT_REVISED | 2-3h | 中 |
| S-5 | videoCache LRU 改为 Android LruCache | OPTIMIZATION_REPORT_REVISED | 1h | 低 |
| S-6 | ffmpeg-kit 依赖诊断（确认实际使用范围后再定） | OPTIMIZATION_REPORT_REVISED | 2h | 中 |
| U-2 | LazyColumn items key 优化（HomeScreen 性能） | UI_UX_REVIEW | 0.5h | 低 |
| U-3 | NavController saveState/restoreState | UI_UX_REVIEW | 1h | 低 |
| U-4 | 骨架屏图片加载（MissNetCoverImage + AsyncImage） | UI_UX_REVIEW | 2h | 中 |

### 🟢 P2（持续改进）

| 编号 | 问题 | 来源 | 工时 | 风险 |
|------|------|------|------|------|
| U-5 | Player 控制栏 AnimatedVisibility 过渡 | UI_UX_REVIEW | 1h | 低 |
| U-6 | Player 封面 → 视频淡入淡出过渡 | UI_UX_REVIEW | 1h | 低 |
| U-7 | LibraryScreen Tab 切换滚动位置保持 | UI_UX_REVIEW | 1.5h | 中 |
| U-8 | Loading 场景化文字（按 scenario 区分） | UI_UX_REVIEW | 1h | 低 |
| U-9 | ErrorType 错误类型区分 | UI_UX_REVIEW | 0.5h | 低 |
| U-10 | SettingsScreen 分组（Section + 分割线） | UI_UX_REVIEW | 1h | 低 |
| U-11 | Badge Token 数值统一引用 | UI_UX_REVIEW | 2h | 低 |
| U-12 | contentDescription 可访问性检查 | UI_UX_REVIEW | 1h | 低 |
| S-7 | UI 单文件拆分（HomeScreen 743行等） | OPTIMIZATION_REPORT_REVISED | 3-4h | 中 |
| S-8 | 引入 UseCase/Interactor 层 | OPTIMIZATION_REPORT_REVISED | 4-6h | 中 |
| S-9 | 单元测试覆盖（VideoRepository 为核心） | OPTIMIZATION_REPORT_REVISED | 3-4h | 低 |

---

## 三、P0 详细执行方案

---

### S-1：Supabase RLS 权限收紧

**目标：** 确保即使 key 泄露，攻击者也只能读数据，无法写入或删除。

**Step by Step：**

1. 登录 Supabase Dashboard → SQL Editor
2. 执行以下 SQL：

```sql
-- 开启 RLS
ALTER TABLE videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE actors ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags ENABLE ROW LEVEL SECURITY;

-- 只读策略：允许匿名用户 SELECT
CREATE POLICY "Allow anon read" ON videos FOR SELECT USING (true);
CREATE POLICY "Allow anon read" ON actors FOR SELECT USING (true);
CREATE POLICY "Allow anon read" ON tags FOR SELECT USING (true);

-- 确认没有 INSERT/UPDATE/DELETE 策略
DROP POLICY IF EXISTS "Allow anon insert" ON videos;
DROP POLICY IF EXISTS "Allow anon update" ON videos;
DROP POLICY IF EXISTS "Allow anon delete" ON videos;
-- 对 actors/tags 同样操作
```

3. 验证：在 APKPac 对任意用户执行写入操作，确认被拒绝

**验证命令：**
```bash
# 用匿名 token 尝试插入，应返回 401/403
curl -X POST "https://gapmmwdbxzcglvvdhhiu.supabase.co/rest/v1/videos" \
  -H "apikey: sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0" \
  -H "Authorization: Bearer sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0" \
  -d '{"title":"test"}' \
  -w "\nHTTP_CODE:%{http_code}"
# 预期返回 401 或 403
```

---

### S-2：VideoRepository 静默异常改进

**目标：** 所有 `catch(_: Exception)` 改为有日志记录，线上可排查。

**修改文件：** `miss_net_native/app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`

**需修改的具体位置（基于代码扫描）：**

在以下方法中，将 `catch(_: Exception)` 替换：

```kotlin
// 改前
catch (_: Exception) { emptyList() }

// 改后
catch (e: Exception) {
    android.util.Log.e("VideoRepository", "getRecentVideos failed: ${e.message}", e)
    emptyList()
}
```

**辅助工具（推荐添加）：**

创建 `data/util/RepositoryLogger.kt`：

```kotlin
package com.panyou.missnet.data.util

import android.util.Log

private const val TAG = "MissNet Repository"

fun logError(context: String, e: Exception) {
    Log.e(TAG, context, e)
}

fun logErrorWithFallback(context: String, e: Exception, fallback: String) {
    Log.e(TAG, "$context — falling back to $fallback", e)
}
```

**涉及需要改的 10+ 处 catch 块完整清单：**

| 方法名 | 现状 | 目标 |
|--------|------|------|
| `getRecentVideos` | `catch(_)` | `catch(e) { Log.e(...) }` |
| `getVideosByCategory` | `catch(_)` | 同上 |
| `getVideosByActor` | `catch(_)` + fallback | 同上 |
| `getHomePayloadResult` | `catch(rpcError)` | 已有日志，增强 |
| `getActorsWithCoversResult` | `catch(_)` | 同上 |
| `getPopularTagsResult` | 多层 `catch(_)` | 全部加日志 |
| `searchVideosFallbackResult` | `catch(_)` | 同上 |

**验证：**
1. 关闭网络，用 Logcat 过滤 `VideoRepository` 标签
2. 触发任意加载操作
3. 确认有 `E/VideoRepository: xxx failed: java.net.UnknownHostException` 类日志

---

### U-1：搜索防抖

**目标：** 避免每字符触发请求，节省 API 配额，提升体验。

**修改文件：** `ui/viewmodel/SearchViewModel.kt`

**当前代码（推测）：**

```kotlin
// 推测现有实现
fun onQueryChange(query: String) {
    viewModelScope.launch {
        doSearch(query)  // 每个字符都触发！
    }
}
```

**改为：**

```kotlin
// SearchViewModel.kt — 完整改造
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

init {
    viewModelScope.launch {
        _searchQuery
            .debounce(350)                        // 350ms 防抖
            .filter { it.length >= 2 }           // 至少 2 个字符
            .distinctUntilChanged()               // 防止重复
            .collectLatest { query ->            // 取最新，丢弃中间
                performSearch(query)
            }
    }
}

fun onQueryChange(query: String) {
    _searchQuery.value = query
    // UI 即时响应，不等 debounce
    _uiState.update { it.copy(query = query, results = emptyList()) }
}

private suspend fun performSearch(query: String) {
    _uiState.update { it.copy(isLoading = true) }
    try {
        val results = repository.searchVideos(query)
        _uiState.update { it.copy(results = results, isLoading = false) }
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.message, isLoading = false) }
    }
}
```

**Step by Step：**
1. 打开 `SearchViewModel.kt`
2. 找到 `onQueryChange` 方法
3. 按上述改造替换实现
4. 添加 `import kotlinx.coroutines.flow.*`
5. 编译验证：`./gradlew :app:compileDebugKotlin`

---

## 四、P1 详细执行方案（摘要）

### S-3：getHomePayload fallback 重复请求优化

**问题核心：** RPC 失败时，7 个 section 各自调用 `getRecentVideosDirectResult(limit=800)`，总数据传输量约 5600 条记录。

**目标：** 合并为 1 次查询，内存中按 tag 分类。

```kotlin
// 在 getHomePayloadResult() 的 catch (rpcError) 分支中替换
val allVideos = when (val r = getRecentVideosDirectResult(limit = 800)) {
    is AppResult.Success -> r.data
    else -> return AppResult.Failure("首页加载失败", rpcError)
}

val grouped = allVideos.groupBy { video ->
    video.tags.firstOrNull { it in setOf("monthly_hot","weekly_hot","uncensored","subtitled","vr","51cg") } ?: "new"
}

HomePayload(
    newVideos = grouped["new"].orEmpty().take(sectionLimit),
    monthlyVideos = grouped["monthly_hot"].orEmpty().take(sectionLimit),
    // ... 其他 section
)
```

---

### S-4：Kotlin/Compose 版本对齐（推荐组合 A）

**推荐版本组合：**

```kotlin
// 项目根 build.gradle.kts
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

// app/build.gradle.kts
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"  // 对应 Kotlin 1.9.22
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
}
```

**Step by Step：**
1. 修改根目录 `build.gradle.kts` 的 plugin 版本
2. 修改 `gradle/wrapper/gradle-wrapper.properties` 确认 Gradle 8.2+
3. `./gradlew clean`
4. `./gradlew :app:assembleDebug`
5. 确认编译成功，无 Compose 版本冲突警告

---

### S-6：ffmpeg-kit 依赖诊断

**Step by Step：**

```bash
# 1. 确认实际使用
grep -rn "ffmpeg\|FFmpeg\|ar.ffmpeg" \
  /root/.openclaw/workspace/miss_net_review/miss_net_native/app/src/main/java/ \
  --include="*.kt"

# 2. 查看 PublicVideoExporter.kt 具体调用
cat app/src/main/java/com/panyou/missnet/data/media/PublicVideoExporter.kt
```

根据结果：
- 如果只用 `MediaExtractor + MediaMuxer`（不转码），可直接移除 ffmpeg 依赖
- 如果用到了 `-c copy`（remux），考虑保留 full 但限制 ABI
- 如果用了编解码，改用 `min` 版

---

## 五、P2 执行方案（完整见各原始文件）

| 编号 | 完整方案 |
|------|---------|
| U-2 | `UI_UX_REVIEW.md` 第 2 节 |
| U-3 | `UI_UX_REVIEW.md` 第 1 节 |
| U-4 | `UI_UX_REVIEW.md` 第 6 节 |
| U-5 | `UI_UX_REVIEW.md` 第 3.1 节 |
| U-6 | `UI_UX_REVIEW.md` 第 3.2 节 |
| U-7 | `UI_UX_REVIEW.md` 第 5 节 |
| U-8/9 | `UI_UX_REVIEW.md` 第 4 节 |
| U-10 | `UI_UX_REVIEW.md` 第 8 节 |
| U-11 | `UI_UX_REVIEW.md` 第 9 节 |
| U-12 | `UI_UX_REVIEW.md` 第 10 节 |
| S-7 | `OPTIMIZATION_REPORT_REVISED.md` 第 7 节 |
| S-8 | `OPTIMIZATION_REPORT_REVISED.md` 第 8 节 |
| S-9 | `OPTIMIZATION_REPORT_REVISED.md` 第 9 节 |

---

## 六、推荐执行顺序

```
第 1 周（Sprint 1）：
  S-1（RLS收紧）→ S-2（异常日志）→ U-1（搜索防抖）

第 2 周（Sprint 2）：
  S-3（HomePayload优化）→ S-4（版本对齐）→ S-5（LRU Cache）
  → U-2（LazyColumn key）→ U-3（NavController）

第 3 周（Sprint 3）：
  S-6（ffmpeg诊断）→ U-4（骨架屏）→ U-5（Player动画）
  → U-6（封面过渡）→ U-7（Tab位置）

持续：
  S-7（UI拆分）→ S-8（UseCase）→ S-9（测试）
  → U-8~U-12（体验完善）
```

---

## 七、原始文档索引

| 文档 | 说明 |
|------|------|
| `OPTIMIZATION_REPORT.md` | 原始 10 条 Arc 撰写 |
| `OPTIMIZATION_REPORT_REVISED.md` | GPT-5.4 复审修订版（1112行） |
| `UI_UX_REVIEW.md` | UI/UX 专项审核 Arc 撰写 |
| `FINAL_IMPLEMENTATION_PLAN.md` | 本文档，综合三份报告 |
