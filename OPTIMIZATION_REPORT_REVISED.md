# miss_net 优化方案复审与修订版

> 复审时间：2026-03-28  
> 复审对象：`/root/.openclaw/workspace/miss_net_review/OPTIMIZATION_REPORT.md`  
> 代码基线：`/root/.openclaw/workspace/miss_net_review/miss_net_native`

---

## 结论先说

原报告的 10 个点里，**大方向多数是对的**，但有几类明显问题：

1. **部分建议本身不准确或不完整**：
   - `Supabase publishable key` 不能简单按“完全隐藏客户端 key”来定义成纯代码问题；真正关键是 **RLS / 权限边界**。
   - `ffmpeg-kit-full -> min` 这一条 **不能直接替换**，因为当前代码确实在做 **HLS m3u8 remux 导出**，不是简单 metadata 读取。
   - `AnimatedTransitionApi 死代码清理` 这一点 **原报告判断错误**：当前项目确实在使用 Shared Transition，不是死代码。

2. **不少步骤不够“交给开发就能做”**：
   - 缺少明确文件路径、改动位置、验证命令、回归清单。
   - 个别建议给了“示意代码”，但与现有项目结构不对齐。

3. **优先级需要重排**：
   - 当前真正高优先级应该是：**安全权限边界、异常可观测性、首页 fallback 重复请求、依赖/编译版本一致性**。
   - 架构性改造（UseCase、UI 大拆分）应该后置，且要分阶段做。

下面给出逐点评审和一个可执行修订方案。

---

## 项目基线观察

### 关键文件

- `app/src/main/java/com/panyou/missnet/di/NetworkModule.kt:26-35`
- `app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt:19-449`
- `app/src/main/java/com/panyou/missnet/data/media/PublicVideoExporter.kt:26-307`
- `app/src/main/java/com/panyou/missnet/ui/viewmodel/HomeViewModel.kt:37-143`
- `app/src/main/java/com/panyou/missnet/ui/viewmodel/SearchViewModel.kt:26-162`
- `app/src/main/java/com/panyou/missnet/ui/viewmodel/ActressViewModel.kt:21-64`
- `app/src/main/java/com/panyou/missnet/ui/screens/HomeScreen.kt:86-743`
- `app/src/main/java/com/panyou/missnet/ui/screens/LibraryScreen.kt:97-1092`
- `app/src/main/java/com/panyou/missnet/ui/screens/PlayerScreen.kt:96-644`
- `app/build.gradle.kts:51-120`
- `build.gradle.kts:1-7`

### 已确认事实

- Supabase key 硬编码：`NetworkModule.kt:33-34`
- ffmpeg 依赖：`app/build.gradle.kts:115`
- ffmpeg 实际用途是 **HLS -> mp4 remux 导出**：`PublicVideoExporter.kt:198-307`
- Repository 内确实存在大量 `catch (_: Exception)`：如 `VideoRepository.kt:58,100,135,147,186,208,304`
- 首页 fallback 确实会发 7 次查询：`VideoRepository.kt:251-258`
- 版本组合确实存在风险：
  - Kotlin plugin `1.9.20`：`build.gradle.kts:3,6`
  - Compose compiler ext `1.5.4`：`app/build.gradle.kts:54-56`
  - Compose BOM `2025.02.00`：`app/build.gradle.kts:72`
- UI 文件过大属实：
  - `LibraryScreen.kt` 1092 行
  - `HomeScreen.kt` 743 行
  - `PlayerScreen.kt` 644 行
- 测试并非“完全没有”，但**业务层测试明显不足**：当前仅有 4 个测试文件，且未覆盖 `VideoRepository` / 关键业务路径。
- `AnimatedTransitionApi` **不是死代码**：`MainActivity.kt:106-116`、`HomeScreen.kt:86-95`、`LibraryScreen.kt:97-106`、`PlayerScreen.kt:96-105` 都在使用相关 API。

---

# 逐点评审与修订方案

---

## 1. Supabase Key 硬编码安全加固

### 当前代码位置

- `app/src/main/java/com/panyou/missnet/di/NetworkModule.kt:31-34`

```kotlin
return createSupabaseClient(
    supabaseUrl = "https://gapmmwdbxzcglvvdhhiu.supabase.co",
    supabaseKey = "sb_publishable_08qYVl69uwJs444rqwodug_wKjj6eD0"
)
```

### 可行性 / 正确性评估

**结论：可做，但原报告需要修正。**

- 如果这是 **publishable / anon key**，那它**理论上就是可下发到客户端的**，所以“代码里有 key”本身不是根因。
- 真正风险在于：**后端 RLS、RPC 权限、表权限是否最小化**。
- 因此：
  - **方案一（RLS + 最小权限）是正确的，而且应该作为主方案。**
  - **方案二（Cloudflare Worker 代理）只有在你要隐藏更高权限能力、加限流/审计时才值得做。**
  - **方案三（本地 BuildConfig/gradle 注入）只能减少源码泄露，不提升客户端逆向后的真实安全性。**

### 风险

- 最大风险不是 key 泄露，而是：
  1. RPC 函数使用 `SECURITY DEFINER` 且未严控；
  2. 表没开 RLS；
  3. anon role 对写操作/敏感表有权限；
  4. 客户端可以绕过应用逻辑直接打 PostgREST / RPC。

### 原报告是否足够交付开发

**不够。**
缺少：
- 如何验证当前 key 类型；
- 如何验证表/RPC 权限；
- Android 侧如何改为构建注入；
- 改完如何验收。

### 修订后的执行方案

#### Phase A：先做权限审计（必须先做）

执行位置：Supabase SQL Editor

```sql
-- 1) 查看表是否启用 RLS
select schemaname, tablename, rowsecurity
from pg_tables
where schemaname = 'public'
order by tablename;

-- 2) 查看 public schema 下策略
select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

-- 3) 查看 anon / authenticated / service_role 的表权限
select grantee, table_schema, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and grantee in ('anon', 'authenticated', 'service_role')
order by table_name, grantee, privilege_type;
```

#### Phase B：收紧权限

若 `videos / actors / tags` 允许匿名读取，则保留只读；禁止匿名写。

```sql
alter table public.videos enable row level security;
alter table public.actors enable row level security;
alter table public.tags enable row level security;

create policy if not exists "anon can read videos"
on public.videos
for select
to anon, authenticated
using (true);

create policy if not exists "anon can read actors"
on public.actors
for select
to anon, authenticated
using (true);

create policy if not exists "anon can read tags"
on public.tags
for select
to anon, authenticated
using (true);

revoke insert, update, delete on public.videos from anon, authenticated;
revoke insert, update, delete on public.actors from anon, authenticated;
revoke insert, update, delete on public.tags from anon, authenticated;
```

> 注：`CREATE POLICY IF NOT EXISTS` 是否可用取决于 PG 版本；如果不支持，用 `DROP POLICY IF EXISTS` + `CREATE POLICY`。

#### Phase C：Android 侧改为 gradle 注入，而非继续硬编码

**文件 1：** `app/build.gradle.kts`  
在 `defaultConfig` 增加：

```kotlin
defaultConfig {
    applicationId = "com.panyou.missnet"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    buildConfigField("String", "SUPABASE_URL", '"https://gapmmwdbxzcglvvdhhiu.supabase.co"')
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", '"' + (project.findProperty("SUPABASE_PUBLISHABLE_KEY") as String? ?: "") + '"')
}

buildFeatures {
    compose = true
    buildConfig = true
}
```

**文件 2：** `~/.gradle/gradle.properties` 或项目本地未提交配置

```properties
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx
```

**文件 3：** `NetworkModule.kt:31-34`

```kotlin
return createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
)
```

### 验证命令

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

### 最终建议优先级

**P0（马上做）**，但重点是 **后端权限**，不是“藏 key”。

---

## 2. ffmpeg-kit-full 依赖裁剪

### 当前代码位置

- 依赖：`app/build.gradle.kts:115`
- 实际使用：`app/src/main/java/com/panyou/missnet/data/media/PublicVideoExporter.kt:198-307`

### 可行性 / 正确性评估

**结论：原报告“可能换成 min 版”判断不严谨，当前不能直接这么改。**

原因：当前逻辑明确在做：
- 读取 HLS m3u8：`PublicVideoExporter.kt:255-307`
- `-protocol_whitelist`
- 自定义 UA / headers
- `-map 0:v:0? -map 0:a? -map 0:s?`
- `-c copy`
- remux 输出 mp4

这不是简单 trim，也不是 metadata 读取。**MediaMuxer/MediaExtractor 不能直接替代网络 HLS playlist 重封装流程。**

### 风险

- 直接替换为 `ffmpeg-kit-min` 可能导致：
  - HLS 协议、demuxer、bitstream filter 缺失；
  - 字幕流或音频流 remux 失败；
  - 某些 m3u8 资源无法导出。
- `ffmpeg-kit` 生态本身维护状态也需要评估，后续可能要考虑迁移策略，但不是这个阶段的最小变更。

### 原报告是否足够交付开发

**不够。**
缺少：
- 先验证 `min` 包是否支持当前命令；
- 导出回归矩阵；
- APK 体积对比方式。

### 修订后的执行方案

#### 方案 A：保守方案（推荐）——先做数据验证，再决定是否裁剪

1. 保持 `ffmpeg-kit-full:6.0-2` 不动。
2. 先测真实 APK 体积和导出成功率。

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native
./gradlew :app:assembleDebug
ls -lh app/build/outputs/apk/debug/*.apk
```

若要看依赖树：

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath > /tmp/missnet-deps.txt
```

#### 方案 B：试验性切到 `ffmpeg-kit-min`（仅做分支实验）

**文件：** `app/build.gradle.kts:115`

```kotlin
implementation("com.arthenica:ffmpeg-kit-min:6.0-2")
```

然后执行：

```bash
./gradlew :app:assembleDebug
```

并做至少以下手测：
- HLS 下载后导出 mp4
- 含音频 HLS 导出
- 含字幕流 HLS 导出
- 需要 Referer/Origin 的源站导出
- 无网络情况下错误提示是否可读

#### 方案 C：真正降包的正确方向

把“导出单文件视频”和“HLS 重封装”继续分流：
- `exportSingleVideo()` 已经是原生 copy：`PublicVideoExporter.kt:160-196`
- `exportHlsVideo()` 才需要 ffmpeg：`198-253`

后续可选优化：
1. **把 ffmpeg 导出做成可选 product flavor**；
2. 或拆成独立 feature/module；
3. 或仅 release 带，debug 不带（如果业务允许）。

### 最终建议优先级

**P1**。先验证，再改；**不要按原报告直接降依赖包。**

---

## 3. Repository 静默吞异常问题

### 当前代码位置

`VideoRepository.kt` 中典型位置：
- `52-60`
- `77-105`
- `123-157`
- `173-191`
- `196-225`
- `239-277`
- `291-306`

### 可行性 / 正确性评估

**结论：完全正确，且是高优先级。**

当前大量 `catch (_: Exception)` 会直接丢失上下文，尤其在以下关键路径：
- 首页加载
- 演员页聚合
- 标签页聚合
- 搜索
- 分类/演员 fallback

这会让线上问题只能表现成“空列表”或统一错误文案，开发难排查。

### 风险

- 若直接把所有异常完整打印到 Logcat，需注意不要泄露敏感 URL / token / query。
- 如果后续接入埋点，不能把异常全量上报而无脱敏。

### 原报告是否足够交付开发

**一般。**
方向对，但给出的 `AppResult.kt` 替代代码与当前项目已有 `AppResult` 结构不一致，会增加额外重构成本。

### 修订后的执行方案

#### 方案：保留现有 `AppResult`，补一个统一日志 helper

**文件：** 新建 `app/src/main/java/com/panyou/missnet/data/result/AppErrors.kt`

```kotlin
package com.panyou.missnet.data.result

import android.util.Log

private const val REPO_TAG = "VideoRepository"

fun logRepoError(stage: String, error: Throwable) {
    Log.e(REPO_TAG, stage, error)
}

fun failure(message: String, stage: String, error: Throwable): AppResult.Failure {
    logRepoError(stage, error)
    return AppResult.Failure(message, error)
}
```

#### 替换示例 1：`getPopularActors()`

**位置：** `VideoRepository.kt:52-60`

```kotlin
suspend fun getPopularActors(limit: Int = 20): List<String> {
    return try {
        supabase.postgrest
            .rpc("get_popular_actors", buildJsonObject { put("limit_count", limit) })
            .decodeList<ActorRpcResult>()
            .map { it.actor }
    } catch (error: Exception) {
        logRepoError("getPopularActors failed", error)
        emptyList()
    }
}
```

#### 替换示例 2：`getHomePayloadResult()`

**位置：** `VideoRepository.kt:239-277`

```kotlin
} catch (rpcError: Exception) {
    logRepoError("getHomePayloadResult rpc failed", rpcError)
    ...
}
```

#### 替换示例 3：`searchVideosResult()`

**位置：** `VideoRepository.kt:291-306`

```kotlin
} catch (error: Exception) {
    logRepoError("searchVideosResult rpc failed; query=$query offset=$offset limit=$limit", error)
    searchVideosFallbackResult(query, limit, offset)
}
```

> 若担心 query 日志敏感，可只打长度/hash。

### 验证命令

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

### 最终建议优先级

**P0。**

---

## 4. videoCache LRU 实现改进

### 当前代码位置

- 声明：`VideoRepository.kt:26`
- 读取：`281`
- 写入和淘汰：`442-448`

### 可行性 / 正确性评估

**结论：问题判断正确，但原报告示例实现不严谨。**

当前确实是“插入顺序淘汰”，不是“访问顺序淘汰”。如果要实现真正 LRU，原生 `LinkedHashMap(accessOrder = true)` 就够了，不一定非得用 `android.util.LruCache`。

原报告里这段：
- `LruCache(maxMemory()/8)`
- `sizeOf()` 返回 `1`

逻辑上自相矛盾：如果 `sizeOf()` 始终返回 1，那么 max size 的单位就不是 KB，而是“条目数”。

### 风险

- `VideoRepository` 可能在多个协程线程访问；当前 `LinkedHashMap` 不是线程安全的。
- 若切 `LruCache` 或 `LinkedHashMap(accessOrder = true)`，最好同步访问或限定线程。

### 原报告是否足够交付开发

**不够。**
未说明线程模型、容量单位、回归方式。

### 修订后的执行方案

#### 推荐方案：使用 access-order LinkedHashMap，最小改动

**位置：** `VideoRepository.kt:26`

```kotlin
private val videoCache = object : LinkedHashMap<String, Video>(1500, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Video>?): Boolean {
        return size > 1500
    }
}
```

然后：
- `getVideoByIdResult()` 保持 `videoCache[id]`，这会刷新访问顺序；
- `rememberVideo()` 可简化为：

```kotlin
private fun rememberVideo(video: Video) {
    if (video.id.isBlank()) return
    synchronized(videoCache) {
        videoCache[video.id] = video
    }
}
```

`getVideoByIdResult()` 读取也同步：

```kotlin
synchronized(videoCache) {
    videoCache[id]?.let { return AppResult.Success(it) }
}
```

### 验证建议

新增测试：
- 连续访问 A,B,C；再次访问 A；插入 D；验证淘汰的是 B 而不是 A。

### 最终建议优先级

**P1。**

---

## 5. getHomePayload 重复请求优化

### 当前代码位置

- `VideoRepository.kt:229-277`
- fallback 多次查询：`251-258`
- `getRecentVideosDirectResult()`：`309-336`

### 可行性 / 正确性评估

**结论：正确，而且收益高。**

当 `get_home_payload` RPC 失败时，当前会：
- `new`
- `monthly_hot`
- `weekly_hot`
- `uncensored`
- `subtitled`
- `vr`
- `51cg`

分别各打一遍查询。虽然每次 limit 不是 800，但总请求数仍高，冷启动时非常浪费。

### 风险

- 原报告里“统一查 800 条然后靠 tags 分类”这个思路可行，但有前提：
  - `monthly_hot / weekly_hot / 51cg / subtitled / vr / uncensored` 必须能在 `tags/categories` 或 URL 规则中稳定还原；
  - 否则 fallback 结果会和 RPC 主路径语义不一致。

### 原报告是否足够交付开发

**基本可用，但缺少对当前数据模型的适配细节。**

### 修订后的执行方案

#### 推荐方案：一次拉取基础集，再本地派生 section

新增 helper，放在 `VideoRepository.kt` 的 `403` 行后面附近：

```kotlin
private fun Video.matchesSection(section: String): Boolean {
    val normalizedTags = tags.map { it.trim().lowercase() }
    val normalizedCategories = categoriesForBrowseFallback().map { it.trim().lowercase() }
    val bucket = (normalizedTags + normalizedCategories).toSet()
    return when (section) {
        "new" -> true
        "monthly_hot" -> "monthly_hot" in bucket
        "weekly_hot" -> "weekly_hot" in bucket
        "uncensored" -> "uncensored" in bucket || sourceUrl.contains("uncensored", ignoreCase = true)
        "subtitled" -> "subtitled" in bucket || bucket.any { it in setOf("subtitle", "subtitles", "chinese_subtitle") }
        "vr" -> "vr" in bucket
        "51cg" -> "51cg" in bucket
        else -> false
    }
}
```

把 `getHomePayloadResult()` 的 catch 改为：

```kotlin
} catch (rpcError: Exception) {
    logRepoError("getHomePayloadResult rpc failed", rpcError)

    val baseResult = getRecentVideosDirectResult(limit = maxOf(120, sectionLimit * 8, weeklyLimit * 4))
    when (baseResult) {
        AppResult.Empty -> AppResult.Empty
        is AppResult.Failure -> AppResult.Failure("首页加载失败，请稍后重试。", rpcError)
        is AppResult.Success -> {
            val allVideos = baseResult.data
            val payload = HomePayload(
                newVideos = allVideos.take(sectionLimit),
                monthlyVideos = allVideos.filter { it.matchesSection("monthly_hot") }.take(sectionLimit),
                weeklyVideos = allVideos.filter { it.matchesSection("weekly_hot") }.take(weeklyLimit),
                uncensoredVideos = allVideos.filter { it.matchesSection("uncensored") }.take(sectionLimit),
                subtitleVideos = allVideos.filter { it.matchesSection("subtitled") }.take(sectionLimit),
                vrVideos = allVideos.filter { it.matchesSection("vr") }.take(sectionLimit),
                chiguaVideos = allVideos.filter { it.matchesSection("51cg") }.take(sectionLimit),
            )
            cacheHomePayload(payload)
            homeCache = TimedCache(payload)
            payload.toAppResult()
        }
    }
}
```

### 验证命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

手测：断掉 `get_home_payload` RPC，让 fallback 生效，确认首页仍正常分栏。

### 最终建议优先级

**P0/P1 边界，建议排 P0.5。**

---

## 6. Kotlin / Compose 版本对齐

### 当前代码位置

- 顶层插件：`build.gradle.kts:2-6`
- app compose compiler：`app/build.gradle.kts:54-56`
- BOM：`app/build.gradle.kts:72`

### 可行性 / 正确性评估

**结论：问题判断基本正确，应尽快处理。**

当前组合：
- AGP `8.5.0`
- Kotlin `1.9.20`
- KSP `1.9.20-1.0.14`
- Compose compiler ext `1.5.4`
- Compose BOM `2025.02.00`

这套搭配大概率不是官方推荐矩阵。即使现在能编，也属于“漂着跑”。

### 风险

- 编译器告警 / 不稳定
- Compose runtime 与 compiler plugin 行为不一致
- 升级某个库后突然编译崩

### 原报告是否足够交付开发

**还行，但版本建议不够贴当前项目。**

### 修订后的执行方案

#### 推荐：先收敛到 Kotlin 1.9 稳定组合

**文件：** `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
```

**文件：** `app/build.gradle.kts`

```kotlin
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.14"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
}
```

> 具体版本号可再按官方对照表微调，但原则是：**Kotlin / Compose compiler / BOM 一套走同一代。**

### 验证命令

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native
./gradlew --stop
./gradlew clean :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

### 最终建议优先级

**P1。**

---

## 7. UI 单文件行数过多

### 当前代码位置

- `HomeScreen.kt:1-743`
- `LibraryScreen.kt:1-1092`
- `PlayerScreen.kt:1-644`

### 可行性 / 正确性评估

**结论：判断正确，但属于维护性问题，不是立刻阻塞问题。**

这些文件过大，会带来：
- 难 review
- 难定位状态流转
- 预览/重组成本高
- 合并冲突频繁

### 风险

- 这类拆分最容易引入：
  - 参数透传错误
  - remember/状态提升位置变化
  - SharedTransitionScope 丢失
  - 回调连线断开

### 原报告是否足够交付开发

**不够。**
给了理想目录，但没有结合当前真实文件内容和共享转场参数。

### 修订后的执行方案

#### 拆分原则

1. **先拆纯 UI 组件，不动 ViewModel。**
2. **先从 Home / Library 开始，Player 最后拆。**
3. 每次 PR 只拆 1 个 screen。

#### 第一阶段建议目录

```text
app/src/main/java/com/panyou/missnet/ui/screens/home/
app/src/main/java/com/panyou/missnet/ui/screens/library/
app/src/main/java/com/panyou/missnet/ui/screens/player/
```

#### HomeScreen 拆分建议

**源文件：** `ui/screens/HomeScreen.kt:86-743`

优先拆成：
- `home/HomeContent.kt`
- `home/HomeDiscoverySections.kt`
- `home/HomeWorkspaceSections.kt`
- `home/HomeEmptyState.kt`

#### LibraryScreen 拆分建议

**源文件：** `ui/screens/LibraryScreen.kt:97-1092`

优先拆成：
- `library/LibrarySnapshotCard.kt`
- `library/LibraryDownloadsTab.kt`
- `library/LibraryHistoryTab.kt`
- `library/LibraryLikesTab.kt`

#### PlayerScreen 拆分建议

Player 已有部分子文件：
- `ui/screens/player/PlayerControls.kt`
- `PlayerSurface.kt`
- `PlayerDetailsPane.kt`

所以应先做 **状态和 side-effect 收敛**，不是盲拆 UI。

### 验收标准

- 主入口 screen 文件控制在 `< 250` 行
- 无功能变更
- 手测导航、返回、shared transition、列表滚动状态

### 最终建议优先级

**P2。**

---

## 8. 缺少 UseCase / Interactor 层

### 当前代码位置

- Repository 被 ViewModel 直接依赖：
  - `HomeViewModel.kt:38-40`
  - `SearchViewModel.kt:27-29`
  - `ActressViewModel.kt:22-23`

### 可行性 / 正确性评估

**结论：方向正确，但不要全量上来就做。**

现在项目规模约 11k 行，直接引入完整 clean architecture 很容易“过度设计”。
真正值得抽 UseCase 的，是那些：
- 有复杂 fallback / 聚合 / 规则判断 的流程；
- 需要单测；
- 会被多个 ViewModel 复用。

### 风险

- 一次性大改会把简单项目复杂化；
- Hilt 注入链会变长；
- 如果没有测试托底，重构风险高。

### 原报告是否足够交付开发

**一般。**
例子是对的，但没有说明“只抽复杂业务，不要机械一层套一层”。

### 修订后的执行方案

#### 只抽 3 个高价值 UseCase

新目录：

```text
app/src/main/java/com/panyou/missnet/domain/usecase/
```

建议第一批仅新增：
- `GetHomePayloadUseCase`
- `GetActorsWithCoversUseCase`
- `SearchVideosUseCase`

#### 示例：`SearchVideosUseCase.kt`

```kotlin
package com.panyou.missnet.domain.usecase

import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.result.AppResult
import com.panyou.missnet.data.model.Video
import javax.inject.Inject

class SearchVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(query: String, limit: Int, offset: Int): AppResult<List<Video>> {
        val normalized = query.trim()
        if (normalized.isBlank()) return AppResult.Empty
        return repository.searchVideosResult(normalized, limit, offset)
    }
}
```

然后只改 `SearchViewModel.kt:75-160` 这一条链路，验证模式可行后再推广。

### 最终建议优先级

**P2。**

---

## 9. 缺少单元测试覆盖

### 当前测试现状

已有测试文件：
- `app/src/test/java/com/panyou/missnet/data/local/LocalVideoStateStoreIncognitoTest.kt`
- `app/src/test/java/com/panyou/missnet/data/result/AppResultTest.kt`
- `app/src/test/java/com/panyou/missnet/ui/screens/player/PlayerGestureSupportTest.kt`
- `app/src/test/java/com/panyou/missnet/ui/theme/MediaSurfaceTokensTest.kt`

### 可行性 / 正确性评估

**结论：原报告“完全没有测试”不准确，但“核心业务缺测试”这个判断正确。**

### 风险

- 当前 `VideoRepository` 强依赖 `SupabaseClient`，直接 mock 成本不低；
- 若先不做轻量抽象，测试会很痛苦。

### 原报告是否足够交付开发

**不够。**
给出的测试示例使用 Mockito 风格，但项目当前未引入 Mockito；同时 `repository.getVideoById()` 也不是一个适合用来验证 cache 淘汰的测试入口。

### 修订后的执行方案

#### 先补测试依赖

**文件：** `app/build.gradle.kts:117-120` 后追加

```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
testImplementation("io.mockk:mockk:1.13.10")
testImplementation("app.cash.turbine:turbine:1.1.0")
testImplementation("androidx.arch.core:core-testing:2.2.0")
```

#### 第一批必须补的测试

1. `HomePayloadSectionMappingTest`
   - 测 `matchesSection()` / fallback 分类规则
2. `SearchVideosUseCaseTest`
   - 空 query
   - 正常 query
3. `VideoRepositoryCacheTest`
   - access-order LRU 行为
4. `HomeViewModelTest`
   - `AppResult.Success`
   - `AppResult.Failure`
   - `AppResult.Empty`

#### 建议测试目录

```text
app/src/test/java/com/panyou/missnet/data/repository/
app/src/test/java/com/panyou/missnet/domain/usecase/
app/src/test/java/com/panyou/missnet/ui/viewmodel/
```

### 验证命令

```bash
./gradlew :app:testDebugUnitTest
```

### 最终建议优先级

**P1/P2 之间。** 如果开始做 fallback/架构改造，测试就应同步前置。

---

## 10. AnimatedTransitionApi 死代码清理

### 当前代码位置

- `MainActivity.kt:106-116` 使用 `ExperimentalSharedTransitionApi`
- `HomeScreen.kt:86-95` 接收 `SharedTransitionScope` / `AnimatedVisibilityScope`
- `LibraryScreen.kt:97-106`
- `PlayerScreen.kt:96-105`
- `ui/components/VideoCard.kt:34-45`
- `ui/components/HomeComponents.kt:63-69, 125-132`

### 可行性 / 正确性评估

**结论：原报告这一点是错误的，不应该执行“删除 SharedTransition 相关 import/API”。**

虽然 `MainActivity.kt` 第 9 行用了通配符 import：

```kotlin
import androidx.compose.animation.*
```

看起来像“没显式用到某几个类”，但实际上：
- `ExperimentalSharedTransitionApi` 被 `@OptIn` 使用；
- `SharedTransitionLayout` 在 `MainScreen()` 中被使用；
- 各 screen / component 也都在传递相关 scope。

所以这不是死代码。

### 风险

如果按原报告删除：
- 会直接破坏共享转场；
- 编译可能失败；
- 或运行时失去预期视觉效果。

### 原报告是否足够交付开发

**不够，而且方向错误。**

### 修订后的执行方案

#### 正确操作：只做“未使用 import”自动整理，不删 Shared Transition 能力

执行：

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native
./gradlew :app:lintDebug
```

如果想查 unused import，用 IDE 的 Optimize Imports，但**先 review diff**，不要批量删动画相关代码。

### 最终建议优先级

**取消此项。** 改为：
- 做一次 lint/import 清理即可，
- **不要把 SharedTransition 当死代码移除。**

---

# 修订后的优先级路线图

## P0：先稳住安全与可观测性

### 1) Supabase 权限审计 + RLS 收紧
- 目标：确认 anon key 只能访问允许的数据
- 涉及：后端 SQL + `NetworkModule.kt`
- 预计：0.5 ~ 1 天

### 2) Repository 异常日志补齐
- 目标：所有关键 fallback 路径都能定位异常
- 涉及：`VideoRepository.kt`
- 预计：0.5 天

### 3) 首页 fallback 去重请求
- 目标：RPC 失败时把 7 次查询压成 1 次基础查询 + 内存分类
- 涉及：`VideoRepository.kt`
- 预计：0.5 ~ 1 天

---

## P1：解决“会炸但不一定今天炸”的问题

### 4) Kotlin / Compose 版本对齐
- 目标：收敛到官方兼容矩阵
- 涉及：`build.gradle.kts`、`app/build.gradle.kts`
- 预计：0.5 天

### 5) videoCache 改成真正 LRU + 基本线程保护
- 目标：提升缓存命中语义正确性
- 涉及：`VideoRepository.kt`
- 预计：0.5 天

### 6) 增补关键业务单测
- 目标：给后续重构托底
- 涉及：`app/src/test/...`
- 预计：1 ~ 1.5 天

### 7) ffmpeg 依赖裁剪调研
- 目标：基于真实导出能力做选择，不盲裁
- 涉及：`PublicVideoExporter.kt`、`app/build.gradle.kts`
- 预计：0.5 ~ 1 天

---

## P2：维护性 / 架构演进

### 8) Home / Library UI 拆分
- 目标：降低单文件复杂度
- 预计：1 ~ 2 天

### 9) 抽 3 个高价值 UseCase
- 目标：降低 ViewModel 与 Repository 耦合
- 预计：1 天

### 10) lint / imports 清理
- 目标：代码整洁
- 预计：0.5 天
- 备注：**不删除 Shared Transition 能力**

---

# 建议执行顺序（最终版）

```text
1. Supabase 权限审计 + RLS
2. Repository 异常日志补齐
3. getHomePayload fallback 优化
4. Kotlin / Compose 版本对齐
5. videoCache 真正 LRU 化
6. 补关键业务测试
7. ffmpeg 裁剪调研（不要直接改）
8. HomeScreen / LibraryScreen 拆分
9. 逐步引入 UseCase
10. lint / import 清理
```

---

# 开发交付清单

## 本轮建议至少提交 3 个 PR

### PR-1：安全与可观测性
- RLS / 权限 SQL
- `NetworkModule.kt` 改 BuildConfig 注入
- `VideoRepository.kt` 日志补齐

### PR-2：首页与构建稳定性
- `getHomePayloadResult()` fallback 重构
- Kotlin / Compose 版本对齐
- 基础回归

### PR-3：缓存与测试托底
- 真 LRU
- Home/Search/ViewModel/section mapping 测试

---

# 验证命令总表

```bash
cd /root/.openclaw/workspace/miss_net_review/miss_net_native

# 1) 编译
./gradlew clean :app:assembleDebug

# 2) Lint
./gradlew :app:lintDebug

# 3) 单测
./gradlew :app:testDebugUnitTest

# 4) 依赖树 / 体积观察
./gradlew :app:dependencies --configuration debugRuntimeClasspath > /tmp/missnet-deps.txt
ls -lh app/build/outputs/apk/debug/*.apk
```

---

# 最终复审结论

- **建议保留并优先推进**：1、3、4、5、6、8、9
- **建议修正后推进**：2、7
- **建议撤回原结论**：10

如果只选最值得先做的 3 件事：

1. **Supabase 权限边界（RLS）**  
2. **Repository 异常日志补齐**  
3. **首页 fallback 重复请求优化**

这三件最能立刻提升安全性、排障能力和真实用户体验。
