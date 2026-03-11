package com.panyou.missnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.util.bouncyClick
import com.panyou.missnet.ui.viewmodel.ActressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActressScreen(
    onActressClick: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ActressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val alphabet = listOf("Hot") + ('A'..'Z').map { it.toString() }
    var selectedLetter by remember { mutableStateOf("Hot") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (uiState.isLoading) {
            MissNetLoading()
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                // Alphabet Filter
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = ContainerTokens.ScreenContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(alphabet) { letter ->
                        FilterChip(
                            selected = selectedLetter == letter,
                            onClick = { selectedLetter = letter },
                            label = { Text(letter) }
                        )
                    }
                }

                // Grid of Actresses
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = ContainerTokens.ScreenCompactHorizontalPadding,
                            end = ContainerTokens.ScreenCompactHorizontalPadding,
                            bottom = ContainerTokens.ScreenContentPadding
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(ContainerTokens.SectionPadding)
                    ) {
                        items(uiState.actresses) { actor ->
                            ActressItem(actor = actor, onClick = { onActressClick(actor.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActressItem(actor: ActorInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (actor.coverUrl != null) {
                AsyncImage(
                    model = actor.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = actor.name.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = actor.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
