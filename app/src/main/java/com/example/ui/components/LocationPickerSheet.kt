package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.domain.model.LocationPlace
import com.example.domain.model.MeasurementUnit
import java.util.Locale

private val POPULAR_MTB_HUBS = listOf(
    LocationPlace("Moab, UT", 38.5733, -109.5498),
    LocationPlace("Bentonville, AR", 36.3729, -94.2088),
    LocationPlace("Sedona, AZ", 34.8697, -111.7610),
    LocationPlace("Bellingham, WA", 48.7519, -122.4787),
    LocationPlace("Brevard, NC", 35.2334, -82.7343),
    LocationPlace("Santa Cruz, CA", 36.9741, -122.0308)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationPickerSheet(
    query: String,
    results: List<LocationPlace>,
    isSearching: Boolean,
    searchError: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onUseCurrentGps: () -> Unit,
    onSelectPlace: (LocationPlace) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose Riding Area",
    measurementUnit: MeasurementUnit = MeasurementUnit.IMPERIAL
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scan or download all MTB trails within a ${FormatUtils.formatRadiusAdjective(unit = measurementUnit).lowercase()} radius.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    onUseCurrentGps()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("use_current_gps_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use My Current GPS Location")
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search city, park, or trailhead…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search place")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("place_search_input")
            )

            if (!searchError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = searchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(results) { place ->
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = {
                                Text(String.format(Locale.US, "%.3f°, %.3f°", place.lat, place.lon))
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier.clickable {
                                onSelectPlace(place)
                                onDismiss()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Popular Trail Destinations",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        POPULAR_MTB_HUBS.forEach { hub ->
                            AssistChip(
                                onClick = {
                                    onSelectPlace(hub)
                                    onDismiss()
                                },
                                label = { Text(hub.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
