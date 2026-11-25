package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.R
import com.example.data.remote.SingletrackImageLoader
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailSortOrder
import com.example.domain.model.TrailViewMode
import com.example.ui.components.DifficultyBadge
import com.example.ui.components.FormatUtils
import com.example.ui.components.TrailRadarMap
import com.example.ui.theme.PineDeep
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: MainUiState,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshTrails: () -> Unit,
    onDownloadRegion: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectDifficulty: (TrailDifficulty?) -> Unit,
    onSelectSortOrder: (TrailSortOrder) -> Unit,
    onSelectViewMode: (TrailViewMode) -> Unit,
    onOpenTrailDetail: (Trail) -> Unit,
    onExpandFullScreenMap: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    val unit = state.measurementUnit
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletrackImageLoader.get(context) }

    Column(modifier = modifier.fillMaxSize()) {
        // Atmospheric Trail Hero Banner bleeding behind status bar when topInset > 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .defaultMinSize(minHeight = 144.dp + topInset)
        ) {
            AsyncImage(
                model = R.drawable.img_trail_hero,
                imageLoader = imageLoader,
                contentDescription = "Mountain biking ridge banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PineDeep.copy(alpha = 0.58f),
                                PineDeep.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Mountain Bike Trails • ${FormatUtils.formatRadiusAdjective(unit = unit)} Radius",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.84f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onRefreshTrails,
                            modifier = Modifier.testTag("refresh_trails_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onOpenLocationPicker,
                            modifier = Modifier.testTag("change_location_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Change riding area",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Measurement units settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Active location + offline save row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onOpenLocationPicker)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.activeLocation?.isCurrentGps == true) {
                                Icons.Filled.MyLocation
                            } else {
                                Icons.Filled.Explore
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.isLocating -> "Acquiring GPS fix…"
                                state.activeLocation != null -> state.activeLocation.name
                                else -> "Set location to scan ${FormatUtils.formatRadiusShort(unit = unit)}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (state.activeLocation != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (state.isCurrentAreaSavedOffline) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(
                                    enabled = !state.isDownloadingRegion,
                                    onClick = onDownloadRegion
                                )
                                .testTag("download_region_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (state.isDownloadingRegion) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (state.isCurrentAreaSavedOffline) {
                                            Icons.Filled.OfflinePin
                                        } else {
                                            Icons.Filled.CloudDownload
                                        },
                                        contentDescription = null,
                                        tint = if (state.isCurrentAreaSavedOffline) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            Color.White
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        state.isDownloadingRegion -> "Saving…"
                                        state.isCurrentAreaSavedOffline -> "Saved Offline"
                                        else -> "Save Offline"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.isCurrentAreaSavedOffline) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        Color.White
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Permission / Location Needed Card
        if (state.activeLocation == null && !state.isLocating) {
            LocationPermissionPromptCard(
                reason = state.locationUnavailableReason,
                hasPermission = state.hasLocationPermission,
                measurementUnit = unit,
                imageLoader = imageLoader,
                onRequestPermission = onRequestLocationPermission,
                onRetryGps = onRefreshTrails,
                onOpenLocationPicker = onOpenLocationPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
            return@Column
        }

        // Search + View Toggle + Filter Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_trails_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_search_clear)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trail_search_input")
                )

                Box {
                    FilledTonalButton(
                        onClick = { sortMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("sort_trails_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort trails",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(state.sortOrder.label)
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        TrailSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    onSelectSortOrder(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Difficulty filter chips
            val difficultyCounts = remember(state.trails) {
                state.trails.groupingBy { it.difficulty }.eachCount()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedDifficulty == null,
                    onClick = { onSelectDifficulty(null) },
                    label = { Text("All (${state.trails.size})") }
                )
                TrailDifficulty.entries.forEach { difficulty ->
                    val count = difficultyCounts[difficulty] ?: 0
                    if (count > 0 || state.selectedDifficulty == difficulty) {
                        FilterChip(
                            selected = state.selectedDifficulty == difficulty,
                            onClick = { onSelectDifficulty(difficulty) },
                            label = { Text("${difficulty.label} ($count)") }
                        )
                    }
                }
            }

            // List / 20-Mi Radar Toggle + Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {
                    SegmentedButton(
                        selected = state.viewMode == TrailViewMode.LIST,
                        onClick = { onSelectViewMode(TrailViewMode.LIST) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("view_mode_list")
                    ) {
                        Text(stringResource(R.string.tab_list))
                    }
                    SegmentedButton(
                        selected = state.viewMode == TrailViewMode.MAP,
                        onClick = { onSelectViewMode(TrailViewMode.MAP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                Icons.Filled.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("view_mode_map")
                    ) {
                        Text(stringResource(R.string.tab_map))
                    }
                }
            }

            if (state.isFromOfflineCache) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Viewing Offline Region${state.activeOfflineRegionName?.let { ": $it" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Main Content Area
        when {
            state.isLoadingTrails || state.isLocating -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (state.isLocating) {
                                "Locating your position…"
                            } else {
                                "Scanning OpenStreetMap trails within ${FormatUtils.formatRadiusLong(unit = unit)}…"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            state.trailErrorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Unable to Scan Trails",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.trailErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefreshTrails) {
                                Text("Retry Scan")
                            }
                        }
                    }
                }
            }

            state.filteredTrails.isEmpty() -> {
                EmptyTrailsCard(
                    onClearFilter = {
                        onSearchQueryChange("")
                        onSelectDifficulty(null)
                    },
                    onOpenLocationPicker = onOpenLocationPicker,
                    hasAnyTrails = state.trails.isNotEmpty(),
                    measurementUnit = unit,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                )
            }

            state.viewMode == TrailViewMode.MAP && state.activeLocation != null -> {
                TrailRadarMap(
                    trails = state.filteredTrails,
                    origin = state.activeLocation,
                    onOpenTrailDetail = onOpenTrailDetail,
                    onToggleFullScreen = onExpandFullScreenMap,
                    initialSelectedTrail = state.selectedTrail,
                    measurementUnit = unit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp + bottomInset)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("trails_lazy_column"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp + bottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.usedSingletrackFallback) {
                        item(key = "fallback_notice") {
                            Text(
                                text = "Showing nearby mapped singletrack & unpaved bike paths within ${FormatUtils.formatRadiusLong(unit = unit)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    items(
                        items = state.filteredTrails,
                        key = { it.osmKey }
                    ) { trail ->
                        TrailCard(
                            trail = trail,
                            isSelected = state.selectedTrail?.osmKey == trail.osmKey,
                            measurementUnit = unit,
                            onClick = { onOpenTrailDetail(trail) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TrailCard(
    trail: Trail,
    isSelected: Boolean,
    measurementUnit: MeasurementUnit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trail_card_${trail.osmId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DifficultyBadge(difficulty = trail.difficulty)
                    Text(
                        text = "${FormatUtils.formatDistance(trail.distanceFromUserMiles, measurementUnit)} ${trail.bearingCardinal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (trail.isOfflineCached) {
                        Icon(
                            imageVector = Icons.Filled.OfflinePin,
                            contentDescription = "Available offline",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = trail.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = trail.surface,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    trail.lengthMiles?.let { len ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${FormatUtils.formatDistance(len, measurementUnit)} trail",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    trail.elevationGainFeet?.let { gainFt ->
                        Text(
                            text = "• ${FormatUtils.formatElevation(gainFt, measurementUnit, prefix = "+")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    trail.networkName?.takeIf { it.isNotBlank() }?.let { net ->
                        Text(
                            text = "• $net",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open trail details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocationPermissionPromptCard(
    reason: String?,
    hasPermission: Boolean,
    measurementUnit: MeasurementUnit,
    imageLoader: coil3.ImageLoader,
    onRequestPermission: () -> Unit,
    onRetryGps: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = R.drawable.img_trail_compass,
                imageLoader = imageLoader,
                contentDescription = "Compass on topographic trail map",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reason ?: "Grant foreground location access to scan OpenStreetMap for mountain biking trails within ${FormatUtils.formatRadiusLong(unit = measurementUnit)} of your current position.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = if (hasPermission) onRetryGps else onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_location_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (hasPermission) "Retry Current GPS" else stringResource(R.string.permission_cta)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenLocationPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_location_button")
            ) {
                Icon(Icons.Filled.Terrain, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.manual_location_cta))
            }
        }
    }
}

@Composable
private fun EmptyTrailsCard(
    hasAnyTrails: Boolean,
    measurementUnit: MeasurementUnit,
    imageLoader: coil3.ImageLoader,
    onClearFilter: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = R.drawable.img_trail_compass,
                    imageLoader = imageLoader,
                    contentDescription = "Empty trail search compass",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (hasAnyTrails) "No Matching Trails" else stringResource(R.string.empty_trails_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasAnyTrails) {
                        "Clear your search or difficulty filter to see all trails in the ${FormatUtils.formatRadiusAdjective(unit = measurementUnit).lowercase()} radius."
                    } else {
                        stringResource(R.string.empty_trails_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (hasAnyTrails) {
                    Button(onClick = onClearFilter) {
                        Text("Show All Trails")
                    }
                } else {
                    AssistChip(
                        onClick = onOpenLocationPicker,
                        label = { Text("Try Another Riding Area") }
                    )
                }
            }
        }
    }
}
