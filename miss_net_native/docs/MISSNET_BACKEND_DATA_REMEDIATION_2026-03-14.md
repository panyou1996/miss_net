# MissNet 后端与数据链路整改清单（2026-03-14）

## 1. 结论摘要

当前链路：

- GitHub Actions 定时抓取 MissAV / 51CG
- 爬虫直接 upsert 到 Supabase `videos`
- Android 前端直接查询 Supabase

这条链路作为 MVP 可运行，但已经暴露出四类核心问题：

1. **数据契约不稳定**：分类、时间字段、封面字段、详情完整度没有统一标准。
2. **错误数据会直接进入主表**：没有 staging 层，也没有详情状态字段。
3. **前端在替后端做聚合**：首页、演员、标签、搜索都依赖多次直查与客户端拼装。
4. **运维能力不足**：没有 schema 版本化、抓取运行记录、验证状态、定时清理闭环。

---

## 2. 本次已确认的线上事实

### 2.1 Supabase CLI 状态

本机当前：

- 未全局安装 `supabase` 可执行文件
- 但可通过 `npx supabase` 使用 CLI
- `npx --yes supabase --version` -> `2.78.1`
- 当前 **CLI 未登录**
  - `npx --yes supabase projects list` 返回：`Access token not provided`

结论：

- **可以用 CLI**，但必须先完成 `supabase login` 或设置 `SUPABASE_ACCESS_TOKEN`
- “你用 GitHub 账号登录 Supabase Web” **不等于** CLI 已登录

### 2.2 线上 `videos` 表当前可见数据

通过 Supabase REST API（publishable key）确认：

- 可见总行数：**14085**
- `is_active = true` 的可见行数：**14085**
- `cover_url is null / ''`：**0**
- `cover_url like 'data:image%'`：**5102**
- `source_url like 'https://missav.ws/%'`：**10223**
- `source_url like 'https://51cg1.com/%'`：**169**
- `cover_url like 'data:image%' and source_url like 'https://missav.ws/%'`：**5102**
- `cover_url like 'data:image%' and source_url like 'https://51cg1.com/%'`：**0**

结论：

- “很多视频丢失封面”这个问题 **是真实存在的**
- 但不是 `NULL` 或空字符串，而是 **大量被写成了 `data:image/...` 的 1x1 占位图**
- 该问题目前 **集中在 MissAV 来源**，不是 51CG

示例：

- `stars-804`
- `huntc-427`
- `vema-254`

这些行的 `cover_url` 都被写成了 tiny base64 placeholder。

### 2.3 “老视频被清掉了”问题

进一步确认：

- 当前最早 `created_at` 约为：**2026-01-25T04:24:36Z**
- `created_at < 2026-01-01` 的可见行数：**0**
- 但 `release_date < 2025-01-01` 的行数：**3042**
- `release_date is null` 的行数：**2942**
- `release_date = 'Unknown'` 的行数：**572**

结论：

- 从“发布时间”看，库里 **确实还有不少老片**
- 但从“入库时间 / 当前表生命周期”看，这个表里的可见数据几乎都是 **2026-01-25 之后重新入库的**
- 当前可见数据里 **没有证据表明老视频是因为 `is_active=false` 被批量软删除**；至少公开可见数据里，全部都是 active
- 因此“老视频被清掉了”的根因更可能是：
  1. 某次重建 / 重灌 / 改表后只保留了新一轮导入的数据
  2. 没有做完整历史回填，只抓到了近阶段能扫到的内容
  3. 缺少 `first_seen_at / last_seen_at / scrape_runs`，导致无法追溯丢失发生在哪一轮

> 注意：由于当前没有 service-role / CLI 登录，本次无法排除“数据库里还存在不可见的 inactive 历史行”这一可能性；但**公开可见部分**没有看到它们。

---

## 3. 已定位的关键代码问题

### P0-1. `duration = "0"` 会导致详情永远不再补抓

文件：`../scraper/main.py`

关键位置：

- `normalize_video_record()`：缺失时长时写入 `"0"`
- `process_page_batch()`：把非空 `duration` 视为“已有真实 metadata”

结果：

1. 某条视频首次详情抓取失败
2. 被写成 `duration = "0"`
3. 后续批次把它认定为“已经有详情”
4. 演员、标签、时长、发布日期长期无法自动恢复

### P0-2. 封面 placeholder 被当成真实封面写入数据库

文件：`../scraper/main.py`

MissAV 列表抓取逻辑：

- 直接读取 `img.src`
- 未过滤 `data:image/...` placeholder
- 直接作为 `cover_url` upsert

结果：

- 数据库里出现大量 fake cover
- 前端无法区分“真实封面缺失”与“占位图已入库”
- 导致 UI 看起来像“丢封面”但又不是空值，难以修复

### P0-3. taxonomy 漂移，前后端字符串不一致

涉及：

- `../scraper/main.py` 中的 `SOURCES`
- `HomeViewModel.kt`
- `VideoRepository.kt`
- UI route / title mapping

典型问题：

- `Subtitled`
- `subtitled`
- `chinese_subtitle`

含义接近，但数据库与前端没有统一字典。

结果：

- 某些分类页为空
- 某些数据虽然存在，但前端查不到
- 同一分类在不同页面表现不一致

### P0-4. `created_at` 混用了“入库时间 / 排序时间 / 展示时间”

当前现状：

- scraper 能抓到 `release_date`
- 但前端多处仍主要按 `created_at` 排序和展示

结果：

- “最新发布”容易变成“最近写入数据库”
- 老片重新抓到后，会挤到最近区域
- 用户会误判“老视频被清掉 / 顺序错乱”

### P0-5. `cleanup-videos` 设计不正确，而且未见完整调度链

文件：`../supabase/functions/cleanup-videos/index.ts`

问题：

- 注释写“最久未检查”，实际按 `created_at asc`
- 没有 `last_verified_at`
- 没有 `verify_fail_count`
- 只对 `HEAD 404` 做非活跃处理
- 未看到定时调用该 Edge Function 的 workflow

结果：

- 检查目标不准确
- 重复检查同一批最老数据
- 无法处理 403 / 405 / challenge / timeout 等常见情况

### P0-6. 数据库 schema / RPC / RLS 未版本化

当前 repo 中基本未纳入：

- 表结构 SQL
- 索引 SQL
- RLS policies
- RPC 定义
- 物化视图 / 聚合视图

结果：

- 新环境无法可靠复现
- 很难知道线上真实结构
- 前端依赖的 RPC（如热门演员/标签）难以维护

---

## 4. 完整改造清单

## P0：先做，先保数据正确性

### P0-A. 修正封面字段写入策略

目标：不再把 placeholder 写成真实封面。

建议：

1. 在 scraper 里增加封面规范化函数：
   - `None` / `""` -> `NULL`
   - `data:image/...` -> `NULL`
   - `about:blank` / blob / 非 http(s) -> `NULL`
2. 对 `cover-t.jpg` -> `cover-n.jpg` 的替换保留
3. upsert 时：
   - 若新值是 `NULL`，不要覆盖已有真实封面
   - 仅在新值是合法 http(s) 图片 URL 时才更新 `cover_url`
4. 增加字段：
   - `cover_status`：`missing / placeholder / valid`
   - 或至少在迁移期先用脚本回填

建议 SQL（迁移期批量清洗）：

```sql
update videos
set cover_url = null
where cover_url like 'data:image%';
```

### P0-B. 修正详情抓取状态机制

目标：不要再靠 `duration` 猜详情是否完整。

建议新增字段：

- `detail_status`：`pending / partial / success / failed`
- `detail_fetched_at`
- `detail_fail_count`
- `last_detail_error`

同时：

- `duration = "0"` 废弃
- `duration_text` 与 `duration_seconds` 分开
- 未知值统一用 `NULL`

### P0-C. 统一 taxonomy

建立唯一来源常量表，例如：

- `new`
- `monthly_hot`
- `weekly_hot`
- `uncensored`
- `subtitled`
- `vr`
- `51cg`
- `51mrds`

要求：

- scraper 只写这套
- DB 只存这套
- Android 前端只查这套
- 页面标题映射由 UI 本地化负责，不再依赖 DB 自由文本

### P0-D. 拆分时间字段

最少拆成：

- `first_seen_at`
- `last_seen_at`
- `scraped_at`
- `source_release_date`
- `created_at`（保留为行创建时间）
- `updated_at`

规则：

- UI 展示日期优先 `source_release_date`
- “最新收录”看 `first_seen_at`
- “最近更新”看 `updated_at` 或 `scraped_at`
- “最新发布”不要再看 `created_at`

### P0-E. 把 schema / index / RLS / RPC 纳入 repo

建议建立：

- `../supabase/migrations/`
- `../supabase/functions/`
- `../supabase/seed.sql`（如需要）
- `../supabase/README.md`

必须纳入版本控制的内容：

- `videos` 表结构
- 索引
- RLS policies
- 热门演员 / 热门标签 RPC
- 后续首页聚合 RPC

---

## P1：再做，提升性能与可维护性

### P1-A. 引入 staging 层

建议表：

- `video_ingest_raw`
- `videos`

流转：

1. scraper 写 `video_ingest_raw`
2. SQL / function 做 normalize + dedupe
3. 再进入主表 `videos`

收益：

- selector 失效时不会立刻污染线上主表
- 可重放清洗逻辑
- 可对比不同轮抓取结果

### P1-B. 首页改成后端聚合

当前首页一次启动会打多次 Supabase 查询。

建议新增：

- RPC：`get_home_payload()`
- 或 Edge Function：`home-payload`

返回：

- continue watching summary
- new
- monthly_hot
- weekly_hot
- uncensored
- subtitled
- featured tags / actors（如需要）

### P1-C. 演员 / 标签做派生表或物化视图

建议：

- `actor_stats`
- `tag_stats`

字段示例：

- `name`
- `video_count`
- `latest_video_id`
- `latest_release_date`
- `cover_url`
- `hot_score`

这样前端不需要“拉最近视频再猜封面”。

### P1-D. 搜索升级

当前仅 `ilike(title)`。

建议：

- `pg_trgm` + trigram index
- 或 PostgreSQL FTS
- 支持 title / actors / tags 联搜
- 增加排序：`match_rank + recency`

### P1-E. 分页从 offset 逐步迁移到 keyset

适合：

- 分类详情页
- 搜索结果页
- 下载历史 / 收藏列表（未来）

建议排序键：

- `source_release_date desc, id desc`
- 或 `created_at desc, id desc`

### P1-F. 改造 cleanup / verify 机制

新增字段：

- `last_verified_at`
- `verify_status`
- `verify_fail_count`
- `last_verify_error`

行为：

- 按 `last_verified_at asc nulls first` 轮询
- 不是只看 `404`
- 区分 `410 / 403 / 429 / timeout / cloudflare`
- 多次失败后再进入“疑似失效”而非立刻下架

---

## P2：最后做，完善监控与运行策略

### P2-A. 抓取频率分层

建议拆成三类：

1. 高频：`new`
   - 每 1~3 小时浅抓前几页
2. 中频：`weekly_hot / monthly_hot / uncensored / subtitled`
   - 每天跑
3. 低频：长尾分类 / 回补历史
   - 每周跑

### P2-B. Early stop

当前 `MISSAV_MAX_PAGES=50` 是固定深翻。

建议：

- 连续 N 页均无新视频 -> 提前停
- 连续 M 条 `external_id` 已存在且详情完整 -> 提前停
- 对冷门源降低翻页深度

### P2-C. 运行记录表

建议表：`scrape_runs`

字段：

- `run_id`
- `started_at`
- `finished_at`
- `source`
- `pages_scanned`
- `discovered_count`
- `upserted_count`
- `detail_success_count`
- `detail_fail_count`
- `placeholder_cover_count`
- `blocked_count`
- `status`
- `error_summary`

同时把摘要写到 GitHub Action 的 `GITHUB_STEP_SUMMARY`。

### P2-D. 依赖锁版本

`scraper/requirements.txt` 当前未锁版本。

建议：

- 固定主次版本
- 或引入 `pip-tools`
- Playwright 浏览器 cache key 包含版本哈希

### P2-E. Remote config

建议把以下值做成可配置：

- source domain
- referer
- resolver strategy
- feature flags
- 首页 section 开关

可选方案：

- `app_config` 表 + 本地缓存
- Edge config / remote config
- BuildConfig + fallback

---

## 5. 数据库建议字段（建议目标模型）

### `videos`

建议保留 / 新增：

- `external_id` (unique)
- `source_site`
- `source_kind`
- `source_url`
- `title`
- `cover_url`
- `cover_status`
- `actors[]`
- `tags[]`
- `categories[]`
- `duration_text`
- `duration_seconds`
- `source_release_date`
- `detail_status`
- `detail_fetched_at`
- `detail_fail_count`
- `first_seen_at`
- `last_seen_at`
- `last_verified_at`
- `verify_status`
- `verify_fail_count`
- `is_active`
- `created_at`
- `updated_at`

### `actor_stats`

- `name`
- `video_count`
- `cover_url`
- `latest_video_id`
- `latest_release_date`
- `hot_score`

### `tag_stats`

- `name`
- `video_count`
- `latest_video_id`
- `latest_release_date`
- `hot_score`

### `scrape_runs`

- `run_id`
- `source`
- `started_at`
- `finished_at`
- `status`
- `pages_scanned`
- `discovered_count`
- `upserted_count`
- `detail_success_count`
- `detail_fail_count`
- `placeholder_cover_count`
- `error_summary`

---

## 6. 索引建议

```sql
create unique index if not exists videos_external_id_key
  on videos (external_id);

create index if not exists idx_videos_active_created_at
  on videos (is_active, created_at desc);

create index if not exists idx_videos_active_release_date
  on videos (is_active, source_release_date desc);

create index if not exists idx_videos_first_seen_at
  on videos (first_seen_at desc);

create index if not exists idx_videos_last_verified_at
  on videos (last_verified_at asc);

create index if not exists idx_videos_actors_gin
  on videos using gin (actors);

create index if not exists idx_videos_tags_gin
  on videos using gin (tags);

create index if not exists idx_videos_categories_gin
  on videos using gin (categories);

create extension if not exists pg_trgm;

create index if not exists idx_videos_title_trgm
  on videos using gin (title gin_trgm_ops);
```

---

## 7. 建议 SQL / 诊断脚本

### 7.1 查 placeholder cover 数量

```sql
select count(*)
from videos
where cover_url like 'data:image%';
```

### 7.2 查 release_date 年份分布

```sql
select left(source_release_date::text, 4) as year, count(*)
from videos
where source_release_date is not null
group by 1
order by 1;
```

### 7.3 查最近 30 天新入库但发布日期很老的数据

```sql
select external_id, title, first_seen_at, source_release_date
from videos
where first_seen_at >= now() - interval '30 days'
  and source_release_date < current_date - interval '365 days'
order by first_seen_at desc
limit 100;
```

### 7.4 清理 fake cover

```sql
update videos
set cover_url = null
where cover_url like 'data:image%';
```

---

## 8. 推荐执行顺序

### 第 1 阶段（立刻做）

1. 修 scraper：禁止写入 `data:image%` 封面
2. 修 scraper：废弃 `duration = "0"`
3. 建 migration：补 `detail_status / first_seen_at / last_seen_at / source_release_date`
4. 建一次性清洗脚本：把历史 placeholder cover 置空
5. 统一 taxonomy

### 第 2 阶段（随后做）

6. 把热门演员 / 标签改成 DB 派生表或物化视图
7. 把首页改成单次后端聚合接口
8. 搜索改成 title + actors + tags
9. 引入 `scrape_runs`

### 第 3 阶段（中期）

10. 引入 staging 表
11. 重做 cleanup / verify 机制
12. 分层抓取 + early stop + 监控

---

## 9. 当前我能继续做什么

在拿到 Supabase CLI 认证后，我可以继续直接执行：

1. `supabase login` / `SUPABASE_ACCESS_TOKEN` 配置
2. `supabase link` 到你的 project
3. 导出当前 schema / 生成 migrations
4. 跑 SQL 统计：
   - placeholder cover 分布
   - 历史视频年份分布
   - inactive/hidden 视频情况
   - taxonomy 实际取值分布
5. 为你起草第一批 migration：
   - 清洗封面
   - 补字段
   - 加索引
   - 建 `scrape_runs`
6. 修 `scraper/main.py` 并提交 PR / commit

---

## 10. 本次结论

### 封面问题
已确认：**真实存在**。根因不是简单的空值，而是大量 placeholder base64 被当成真实封面写入数据库。

### 老视频问题
已确认：**有“可见表生命周期很新”的现象**。当前表的 `created_at` 基本从 2026-01-25 开始，但 `release_date` 中仍存在大量更早年份的视频。更像是“数据重建/回填不完整/缺少追踪”，而不是当前 visible rows 被 cleanup 批量软删。

### 最应该先修的三件事

1. 停止写入 fake cover
2. 停止用 `duration = "0"` 代表未知详情
3. 把 Supabase schema / migration / RPC / RLS 正式纳入 repo
