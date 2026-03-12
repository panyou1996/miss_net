# MissNet Player UI 改造计划

基于 NextPlayer、Media3 官方最佳实践的改造方案

---

## 一、现状分析

### 当前实现 (PlayerScreen.kt)
- 使用旧的 `AndroidView + PlayerView` 方式
- 自定义 `PlayerControls` Composable
- 全屏/竖屏切换使用 `requestedOrientation`
- 无手势控制（亮度/音量/快进）
- Material 3 基础支持，但组件不够现代化

### 问题点
1. **技术栈过时**: 使用 `AndroidView` 而非 Media3 1.6+ 的 `PlayerSurface`
2. **交互缺失**: 没有手势控制（NextPlayer 的核心亮点）
3. **控件粗糙**: SeekBar、按钮使用基础 Compose，未充分使用 MD3 组件
4. **状态管理**: Player 状态散落，未使用 Media3 `PlayerState`

---

## 二、改造目标

### 短期 (MVP)
- [ ] 迁移到 Media3 `PlayerSurface` 或现代 `AndroidView` 写法
- [ ] 现代化控件：使用 MD3 `Slider` 替代基础实现
- [ ] 添加基础手势：点击显示/隐藏控制层

### 中期 (NextPlayer 风格)
- [ ] 手势控制：左右滑动快进快退
- [ ] 亮度/音量手势：左侧上下滑、右侧上下滑
- [ ] MD3 风格控制层：TopBar、BottomBar
- [ ] 播放速度选择器

### 长期 (完善)
- [ ] 画中画模式 (PiP)
- [ ] 弹幕/字幕支持 UI
- [ ] 画质选择 UI
- [ ] 锁屏控制

---

## 三、具体改造方案

### Phase 1: 基础设施升级

#### 1.1 依赖更新
```kotlin
// build.gradle.kts
dependencies {
    // Media3 1.6.0+ 带来了 Compose 支持
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")
    implementation("androidx.media3:media3-session:1.6.0")
    // 如需 Compose 状态绑定
    implementation("androidx.media3:media3-ui-compose:1.6.0")
}
```

#### 1.2 迁移 PlayerSurface (推荐) 或保留 AndroidView 优化
```kotlin
// 方案 A: 使用 PlayerSurface (Media3 1.6.0+)
import androidx.media3.ui.compose.PlayerSurface

@Composable
fun PlayerContainer(player: ExoPlayer?) {
    player?.let {
        PlayerSurface(
            player = it,
            modifier = Modifier.fillMaxSize(),
            useController = false  // 使用自定义控制器
        )
    }
}

// 方案 B: 保留 AndroidView 但优化
@Composable
fun PlayerContainer(player: Player?) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                // 使用新的 MD3 样式
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier.fillMaxSize()
    )
}
```

---

### Phase 2: 现代化控件

#### 2.1 MD3 Slider 进度条
```kotlin
@Composable
fun PlayerSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentPosition.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = duration.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```

#### 2.2 MD3 IconButton 控制按钮
```kotlin
@Composable
fun OverlayControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}
```

---

### Phase 3: 手势控制 (NextPlayer 核心功能)

#### 3.1 手势检测组件
```kotlin
@Composable
fun GestureDetectorPlayer(
    player: ExoPlayer,
    onGestureChange: (GestureType) -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var gestureType by remember { mutableStateOf(GestureType.NONE) }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offsetX = 0f },
                    onDragEnd = {
                        when {
                            offsetX > 300 -> player.seekForward()
                            offsetX < -300 -> player.seekBack()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            offsetY > 200 -> {/* 亮度降低 */}
                            offsetY < -200 -> {/* 亮度增加 */}
                        }
                        offsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        offsetY += dragAmount
                    }
                )
            }
    ) {
        content()
    }
}
```

#### 3.2 手势反馈 Overlay
```kotlin
@Composable
fun GestureFeedbackOverlay(
    gestureType: GestureType,
    progress: Float,  // 0.0 - 1.0
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = gestureType != GestureType.NONE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            // 根据手势类型显示不同 UI
            when (gestureType) {
                GestureType.SEEK_FORWARD -> {
                    Icon(Icons.Default.Forward10, contentDescription = null)
                }
                GestureType.SEEK_BACK -> {
                    Icon(Icons.Default.Replay10, contentDescription = null)
                }
                GestureType.BRIGHTNESS -> {
                    // 显示亮度进度条
                    BrightnessIndicator(progress = progress)
                }
                GestureType.VOLUME -> {
                    // 显示音量进度条
                    VolumeIndicator(progress = progress)
                }
                else -> {}
            }
        }
    }
}
```

---

### Phase 4: 播放速度选择器

```kotlin
@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Column {
                speeds.forEach { speed ->
                    ListItem(
                        headlineContent = { Text("${speed}x") },
                        leadingContent = {
                            RadioButton(
                                selected = currentSpeed == speed,
                                onClick = { onSpeedSelected(speed) }
                            )
                        },
                        modifier = Modifier.clickable { onSpeedSelected(speed) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
```

---

## 四、文件结构改造

```
app/src/main/java/com/panyou/missnet/
├── ui/
│   ├── screens/
│   │   └── PlayerScreen.kt        # 主播放器页面
│   ├── components/
│   │   ├── player/
│   │   │   ├── PlayerContainer.kt     # 播放器容器
│   │   │   ├── PlayerControls.kt       # 控制层
│   │   │   ├── PlayerSeekBar.kt        # 进度条
│   │   │   ├── PlayerTopBar.kt         # 顶部栏
│   │   │   ├── PlayerBottomBar.kt      # 底部栏
│   │   │   ├── GestureDetector.kt      # 手势检测
│   │   │   ├── GestureFeedback.kt      # 手势反馈
│   │   │   └── SpeedSelector.kt       # 速度选择
│   │   └── ...
│   └── theme/
│       └── PlayerTokens.kt         # 播放器专用 Design Tokens
```

---

## 五、风险与注意事项

### 兼容性
- Media3 1.6.0+ 需要 API 21+
- `PlayerSurface` 是新 API，需要测试覆盖

### 性能
- 手势检测需要使用 `remember` 避免重组
- 进度条更新使用 `derivedStateOf` 减少计算

### 回归测试
- 现有播放功能（播放/暂停/跳转）
- 全屏切换
- 下载/分享功能
- 历史记录同步

---

## 六、实施顺序建议

1. **先做 Phase 1**: 升级依赖，保留功能，只改实现方式
2. **再做 Phase 2**: 控件现代化，不影响交互
3. **然后 Phase 3**: 添加手势（核心差异化功能）
4. **最后 Phase 4**: 速度选择等锦上添花功能

---

## 七、参考资源

- [NextPlayer GitHub](https://github.com/anilbeesetti/NextPlayer)
- [Media3 UI 官方文档](https://developer.android.com/media/media3/ui/customization)
- [Media3 1.6.0 Release Notes](https://developer.android.com/media/media3/release-notes)
- [PlayerSurface 迁移指南](https://proandroiddev.com/from-androidview-to-playersurface-modernizing-exoplayer-with-media3s-compose-ui-74e40ce81f94)
