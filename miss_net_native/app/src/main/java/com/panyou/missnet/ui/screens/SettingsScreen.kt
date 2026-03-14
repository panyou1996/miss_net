package com.panyou.missnet.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panyou.missnet.ui.components.BrowseSummaryCard
import com.panyou.missnet.ui.components.MissNetListDivider
import com.panyou.missnet.ui.components.StatusBadge
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.util.bouncyClick
import com.panyou.missnet.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(ContainerTokens.SectionVerticalSpacing)
        ) {
            BrowseSummaryCard(
                title = "设置与偏好",
                summary = "当前以本机存储为主，优先保证播放器、资源库与状态语言稳定。",
                helper = "主题、隐私与缓存偏好会保存在本机；账号接入不等于已开启完整云同步。",
                footer = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge(
                            text = "本机优先",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            text = "稳定优先",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        StatusBadge(
                            text = "同步后续支持",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = ContainerTokens.ScreenCompactHorizontalPadding)
            )

            SettingsSectionTitle("同步", "账号状态与本机数据策略")
            EliteSettingsCard {
                if (!uiState.isLoggedIn) {
                    ListItem(
                        headlineContent = { Text("当前使用本机存储", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text("收藏、偏好与播放进度当前仅保存在本机。云端同步将在后续版本接入。")
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(ContainerTokens.ListLeadingContainerSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AccountCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                                )
                            }
                        },
                        trailingContent = {
                            StatusBadge(
                                text = "本机模式",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("已连接云端账号", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(uiState.user?.email ?: "已登录") },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(ContainerTokens.ListLeadingContainerSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Verified,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.logout() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            SettingsSectionTitle("外观", "深色模式、动态色彩与主题颜色")
            EliteSettingsCard {
                SettingsToggleItem(
                    icon = Icons.Rounded.DarkMode,
                    title = "深色模式",
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() }
                )
                MissNetListDivider()
                SettingsToggleItem(
                    icon = Icons.Rounded.Palette,
                    title = "动态色彩",
                    subtitle = "跟随壁纸主题色 (Android 12+)",
                    checked = uiState.isDynamicColor,
                    onCheckedChange = { viewModel.toggleDynamicColor() }
                )
                MissNetListDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题颜色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val colors = listOf(
                            Color(0xFFEF4444),
                            Color(0xFF3B82F6),
                            Color(0xFF10B981),
                            Color(0xFFA855F7)
                        )
                        colors.forEachIndexed { index, color ->
                            val isSelected = uiState.themeColorIndex == index
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .bouncyClick { viewModel.setThemeColor(index) }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionTitle("隐私", "无痕模式、应用锁与本机保护")
            EliteSettingsCard {
                SettingsToggleItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "无痕模式",
                    checked = uiState.isIncognito,
                    subtitle = "浏览记录不写入历史",
                    onCheckedChange = { viewModel.toggleIncognito() }
                )
                MissNetListDivider()
                SettingsToggleItem(
                    icon = Icons.Rounded.Fingerprint,
                    title = "应用锁",
                    checked = uiState.isAppLockEnabled,
                    subtitle = "使用生物识别解锁",
                    onCheckedChange = { viewModel.toggleAppLock() }
                )
            }

            SettingsSectionTitle("存储", "设备占用参考与封面缓存管理")
            EliteSettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Storage,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("设备存储参考", fontWeight = FontWeight.Medium)
                        }
                        Text(
                            uiState.usedStorageStr,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uiState.storageProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "这里只展示设备存储参考，不代表 MissNet 单独占用空间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MissNetListDivider()
                ListItem(
                    headlineContent = { Text("清理图片缓存") },
                    supportingContent = { Text("释放封面缓存占用空间") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.clearCache()
                        Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            SettingsSectionTitle("播放与下载", "当前版本优先保证稳定性与可交付")
            EliteSettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "播放与下载设置正在收口中",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "后续会补充：仅 Wi‑Fi 下载、默认倍速、自动续播，以及更细的缓存策略。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge(
                            text = "稳定优先",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        StatusBadge(
                            text = "Wi‑Fi 下载后续",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            text = "倍速配置后续",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                        )
                        Text(
                            text = "当前版本优先保证播放器、资源库和状态语言稳定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSectionTitle("关于", "版本信息与基础说明")
            EliteSettingsCard {
                ListItem(
                    headlineContent = { Text("版本") },
                    trailingContent = {
                        Text(uiState.version, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding))
        }
    }
}

@Composable
fun SettingsSectionTitle(
    text: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .padding(
                start = ContainerTokens.ScreenCompactHorizontalPadding,
                end = ContainerTokens.ScreenCompactHorizontalPadding,
                top = 8.dp
            )
            .animateContentSize(animationSpec = MotionTokens.standard()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
fun EliteSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = ContainerTokens.ScreenCompactHorizontalPadding)
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.standard()),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
