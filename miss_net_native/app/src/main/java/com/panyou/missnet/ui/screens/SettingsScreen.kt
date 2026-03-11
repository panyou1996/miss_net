package com.panyou.missnet.ui.screens

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.ui.viewmodel.SettingsViewModel
import com.panyou.missnet.ui.util.bouncyClick

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            SettingsSectionTitle("Account", primaryColor)
            EliteSettingsCard {
                if (!uiState.isLoggedIn) {
                    ListItem(
                        headlineContent = { Text("Sign In", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Sync your favorites to cloud") },
                        leadingContent = {
                            Box(modifier = Modifier.size(48.dp).background(primaryColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AccountCircle, null, tint = primaryColor)
                            }
                        },
                        modifier = Modifier.bouncyClick { viewModel.signIn() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("User Account", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(uiState.user?.email ?: "Logged In") },
                        leadingContent = {
                            Box(modifier = Modifier.size(48.dp).background(primaryColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Verified, null, tint = primaryColor)
                            }
                        },
                        trailingContent = { 
                            IconButton(onClick = { viewModel.logout() }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            SettingsSectionTitle("Appearance", primaryColor)
            EliteSettingsCard {
                SettingsToggleItem(
                    Icons.Rounded.DarkMode, 
                    "Dark Mode", 
                    uiState.isDarkMode, 
                    primaryColor, 
                    onCheckedChange = { viewModel.toggleDarkMode() }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                
                // Theme Color Picker
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val colors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFA855F7))
                        colors.forEachIndexed { index, color ->
                            val isSelected = uiState.themeColorIndex == index
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .bouncyClick { viewModel.setThemeColor(index) }
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isSelected,
                                    enter = scaleIn() + fadeIn(),
                                    exit = scaleOut() + fadeOut()
                                ) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionTitle("Privacy", primaryColor)
            EliteSettingsCard {
                SettingsToggleItem(
                    Icons.Rounded.VisibilityOff, 
                    "Incognito Mode", 
                    uiState.isIncognito, 
                    primaryColor, 
                    "History is not saved",
                    onCheckedChange = { viewModel.toggleIncognito() }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleItem(
                    Icons.Rounded.Fingerprint, 
                    "App Lock", 
                    uiState.isAppLockEnabled, 
                    primaryColor, 
                    "Biometric authentication",
                    onCheckedChange = { viewModel.toggleAppLock() }
                )
            }

            SettingsSectionTitle("Storage", primaryColor)
            EliteSettingsCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Device Storage", fontWeight = FontWeight.Medium)
                        }
                        Text(uiState.usedStorageStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uiState.storageProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text("Clear Image Cache") },
                    supportingContent = { Text("Free up space used by thumbnails") },
                    leadingContent = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { 
                        viewModel.clearCache()
                        Toast.makeText(context, "Cache Cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            SettingsSectionTitle("About", primaryColor)
            EliteSettingsCard {
                ListItem(
                    headlineContent = { Text("Version") },
                    trailingContent = { Text(uiState.version, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String, color: Color) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp))
}

@Composable
fun EliteSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp, shadowElevation = 1.dp) {
        Column(content = content)
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector, 
    title: String, 
    checked: Boolean, 
    primaryColor: Color, 
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface) },
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
                } else null
            ) 
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}