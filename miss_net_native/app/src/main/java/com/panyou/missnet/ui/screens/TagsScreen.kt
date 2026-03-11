package com.panyou.missnet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.viewmodel.TagsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onTagClick: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (uiState.isLoading) {
            MissNetLoading()
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = ContainerTokens.ScreenCompactHorizontalPadding,
                            end = ContainerTokens.ScreenCompactHorizontalPadding,
                            bottom = ContainerTokens.ScreenContentPadding
                        ),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.tags) { tag ->
                            ListItem(
                                headlineContent = { Text(tag, style = MaterialTheme.typography.titleMedium) },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onTagClick(tag) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = ContainerTokens.ScreenContentPadding),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                        item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                    }
                }
            }
        }
    }
}
