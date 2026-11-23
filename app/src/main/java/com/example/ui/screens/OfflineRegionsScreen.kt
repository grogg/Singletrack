package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.R
import com.example.data.remote.SingletrackImageLoader
import com.example.domain.model.LocationPlace
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.OfflineRegion
import com.example.ui.components.FormatUtils

@Composable
fun OfflineRegionsScreen(
    regions: List<OfflineRegion>,
    activeLocation: LocationPlace?,
    measurementUnit: MeasurementUnit,
    loadOfflineByDefault: Boolean,
    defaultOfflineRegionId: String?,
    isDownloadingRegion: Boolean,
    downloadingRegionName: String?,
    onOpenSettings: () -> Unit,
    onToggleLoadOfflineByDefault: (Boolean) -> Unit,
    onSetDefaultOfflineRegion: (OfflineRegion) -> Unit,
    onDownloadCurrentArea: () -> Unit,
    onOpenRegionPicker: () -> Unit,
    onOpenOfflineRegion: (OfflineRegion) -> Unit,
    onUpdateRegion: (OfflineRegion) -> Unit,
    onDeleteRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    val resolvedDefaultId = regions.firstOrNull { it.id == defaultOfflineRegionId }?.id
        ?: regions.firstOrNull()?.id
    val defaultRegionName = regions.firstOrNull { it.id == resolvedDefaultId }?.name
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("offline_regions_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp + topInset,
            bottom = 20.dp + bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Trail Regions",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("offline_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Measurement units settings"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Download any ${FormatUtils.formatRadiusAdjective(unit = measurementUnit).lowercase()} region including trail ratings and GPS route shapes so you can navigate without cell service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "download_actions") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isDownloadingRegion) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Downloading ${FormatUtils.formatRadiusShort(unit = measurementUnit)} trails & geometries for ${downloadingRegionName ?: "region"}…",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    } else if (activeLocation != null) {
                        Button(
                            onClick = onDownloadCurrentArea,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_download_current_button")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download ${activeLocation.name} (${FormatUtils.formatRadiusShort(unit = measurementUnit)})")
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenRegionPicker,
                        enabled = !isDownloadingRegion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_pick_region_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search & Download Another Region")
                    }
                }
            }
        }

        item(key = "startup_default_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Load Offline Map on Launch",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when {
                                loadOfflineByDefault && defaultRegionName != null ->
                                    "Opens $defaultRegionName automatically when the app starts."
                                loadOfflineByDefault ->
                                    "Download a region below to load it automatically on startup."
                                else ->
                                    "When off, the app scans your GPS area online at launch."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = loadOfflineByDefault,
                        onCheckedChange = onToggleLoadOfflineByDefault,
                        modifier = Modifier.testTag("offline_default_switch")
                    )
                }
            }
        }

        if (regions.isEmpty()) {
            item(key = "empty_offline") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val context = LocalContext.current
                        val imageLoader = remember(context) { SingletrackImageLoader.get(context) }
                        AsyncImage(
                            model = R.drawable.img_trail_compass,
                            imageLoader = imageLoader,
                            contentDescription = "No offline regions downloaded",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Offline Regions Saved Yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Download your local riding area or an upcoming destination above to inspect trails and route maps completely offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(items = regions, key = { it.id }) { region ->
                OfflineRegionCard(
                    region = region,
                    isUpdating = isDownloadingRegion && downloadingRegionName == region.name,
                    isDefaultOnLaunch = region.id == resolvedDefaultId,
                    showDefaultControls = loadOfflineByDefault || regions.size > 1,
                    onSetDefault = { onSetDefaultOfflineRegion(region) },
                    onOpen = { onOpenOfflineRegion(region) },
                    onUpdate = { onUpdateRegion(region) },
                    onDelete = { onDeleteRegion(region.id) }
                )
            }
        }
    }
}

@Composable
private fun OfflineRegionCard(
    region: OfflineRegion,
    isUpdating: Boolean,
    isDefaultOnLaunch: Boolean,
    showDefaultControls: Boolean,
    onSetDefault: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_region_card_${region.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OfflinePin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = region.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${region.trailCount} trails • ${FormatUtils.formatBytes(region.approxSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_region_${region.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete offline region"
                    )
                }
            }

            Text(
                text = "Saved ${FormatUtils.formatTimestamp(region.downloadedAtMillis)} • " +
                    if (region.hasFullGeometry) "Full trail routes cached" else "Trail pins & details cached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showDefaultControls) {
                if (isDefaultOnLaunch) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Default Startup Offline Map",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onSetDefault,
                        modifier = Modifier.testTag("set_default_region_${region.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Default Startup Map")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_region_${region.id}")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Offline")
                }

                FilledTonalButton(
                    onClick = onUpdate,
                    enabled = !isUpdating,
                    modifier = Modifier.testTag("update_region_${region.id}")
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update")
                }
            }
        }
    }
}
