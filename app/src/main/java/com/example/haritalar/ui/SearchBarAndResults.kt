package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.db.SearchHistoryItem
import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.HouseNumberStatus
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.SearchUiStatus

@Composable
fun SearchHeader(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    isSearching: Boolean,
    searchStatus: SearchUiStatus = SearchUiStatus.IDLE,
    searchErrorMessage: String? = null,
    searchActiveProvider: String? = null,
    isSearchFocused: Boolean = false,
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onSubmitSearch: () -> Unit = {},
    onRetrySearch: () -> Unit = {},
    searchResults: List<SearchResult>,
    onSelectResult: (SearchResult) -> Unit,
    selectedCategory: PoiCategory?,
    onSelectCategory: (PoiCategory?) -> Unit,
    favorites: List<FavoritePlace>,
    onSelectFavorite: (FavoritePlace) -> Unit,
    onDeleteFavorite: (FavoritePlace) -> Unit = {},
    recentSearches: List<SearchHistoryItem>,
    onSelectRecentSearch: (SearchHistoryItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val providerLabel = searchActiveProvider?.let {
        when {
            it.contains("Nominatim", ignoreCase = true) || it.contains("Photon", ignoreCase = true) -> "OSM Harita"
            it.contains("LANU", ignoreCase = true) -> it
            else -> "Harita Araması"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search Bar Box
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_bar_surface")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSubmitSearch,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    placeholder = {
                        Text(
                            "Adres, işletme, cadde veya mekan ara...",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                        .testTag("search_text_input")
                )

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("search_loading_indicator"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = onClearQuery,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Temizle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Results / Empty State / Error State / Recent Searches
        AnimatedVisibility(
            visible = isSearchFocused && (
                    searchQuery.trim().length >= 2 ||
                    (searchQuery.isEmpty() && (recentSearches.isNotEmpty() || favorites.isNotEmpty()))
                ),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 320.dp)
                    .testTag("search_results_container")
            ) {
                when {
                    // 1. Success State with Results
                    searchResults.isNotEmpty() -> {
                        Column {
                            if (providerLabel != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${searchResults.size} sonuç bulundu",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = providerLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }

                            LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                                items(searchResults, key = { it.id }) { result ->
                                    SearchResultRow(
                                        result = result,
                                        onClick = {
                                            focusManager.clearFocus()
                                            onSelectResult(result)
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }

                    // 2. Searching In Progress (Waiting for network)
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Adres aranıyor...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Empty State (No matching results found)
                    searchStatus == SearchUiStatus.EMPTY || (searchResults.isEmpty() && searchQuery.trim().length >= 2 && !isSearching && searchStatus != SearchUiStatus.ERROR) -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                                .testTag("search_empty_state"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Bu aramayla eşleşen adres bulunamadı",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "“$searchQuery” için sonuç bulunamadı. Adresin ilçe, mahalle veya cadde bilgisini ekleyerek tekrar deneyin.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // 4. Error State (Network / API failure)
                    searchStatus == SearchUiStatus.ERROR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                                .testTag("search_error_state"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Arama servisine ulaşılamıyor",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = searchErrorMessage ?: "Arama servisine şu anda ulaşılamıyor. Lütfen tekrar deneyin.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRetrySearch,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("search_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene", fontSize = 13.sp)
                            }
                        }
                    }

                    // 5. Saved places + recent searches
                    searchQuery.isEmpty() && (favorites.isNotEmpty() || recentSearches.isNotEmpty()) -> {
                        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                            if (favorites.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmarks,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Kayıtlı Yerler",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "${favorites.size}/500",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(favorites.take(500), key = { "fav_${it.id}" }) { fav ->
                                    SavedPlaceRow(
                                        favorite = fav,
                                        onClick = {
                                            focusManager.clearFocus()
                                            onSelectFavorite(fav)
                                        },
                                        onDelete = { onDeleteFavorite(fav) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }

                            if (recentSearches.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Son Aramalar",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(recentSearches.take(5), key = { "recent_${it.id}" }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focusManager.clearFocus()
                                                onSelectRecentSearch(item)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                            .testTag("recent_search_item_${item.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.query,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.displayName.isNotBlank()) {
                                                Text(
                                                    text = item.displayName,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.NorthWest,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick POI Category Chips
        if (searchResults.isEmpty() && searchQuery.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    title = "Benzinlik",
                    icon = Icons.Default.LocalGasStation,
                    isSelected = selectedCategory == PoiCategory.FUEL,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.FUEL) null else PoiCategory.FUEL)
                    }
                )
                CategoryChip(
                    title = "Eczane",
                    icon = Icons.Default.LocalPharmacy,
                    isSelected = selectedCategory == PoiCategory.PHARMACY,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.PHARMACY) null else PoiCategory.PHARMACY)
                    }
                )
                CategoryChip(
                    title = "Hastane",
                    icon = Icons.Default.LocalHospital,
                    isSelected = selectedCategory == PoiCategory.HOSPITAL,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.HOSPITAL) null else PoiCategory.HOSPITAL)
                    }
                )
                CategoryChip(
                    title = "Otopark",
                    icon = Icons.Default.LocalParking,
                    isSelected = selectedCategory == PoiCategory.PARKING,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.PARKING) null else PoiCategory.PARKING)
                    }
                )
                CategoryChip(
                    title = "Restoran",
                    icon = Icons.Default.Restaurant,
                    isSelected = selectedCategory == PoiCategory.RESTAURANT,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.RESTAURANT) null else PoiCategory.RESTAURANT)
                    }
                )
                CategoryChip(
                    title = "Şarj",
                    icon = Icons.Default.EvStation,
                    isSelected = selectedCategory == PoiCategory.CHARGING_STATION,
                    onClick = {
                        onSelectCategory(if (selectedCategory == PoiCategory.CHARGING_STATION) null else PoiCategory.CHARGING_STATION)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit
) {
    val (icon, bgTint, iconTint) = when (result.resultType) {
        AddressResultType.ADDRESS -> Triple(Icons.Default.Home, Color(0xFFFFF7ED), Color(0xFFEA580C))
        AddressResultType.STREET -> Triple(Icons.Default.Signpost, Color(0xFFEFF6FF), Color(0xFF2563EB))
        AddressResultType.NEIGHBORHOOD -> Triple(Icons.Default.LocationCity, Color(0xFFFAF5FF), Color(0xFF9333EA))
        AddressResultType.DISTRICT, AddressResultType.CITY -> Triple(Icons.Default.Place, Color(0xFFECFDF5), Color(0xFF059669))
        AddressResultType.POI -> Triple(Icons.Default.Storefront, Color(0xFFFEF2F2), Color(0xFFDC2626))
        AddressResultType.PLACE -> Triple(Icons.Default.LocationOn, Color(0xFFE0F2FE), Color(0xFF0284C7))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag("search_result_item_${result.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (result.resultType != AddressResultType.PLACE) {
                    val badgeText = when (result.resultType) {
                        AddressResultType.ADDRESS -> "Bina / No"
                        AddressResultType.STREET -> "Cadde/Sokak"
                        AddressResultType.NEIGHBORHOOD -> "Mahalle"
                        AddressResultType.DISTRICT -> "İlçe"
                        AddressResultType.CITY -> "İl"
                        AddressResultType.POI -> "POI"
                        else -> ""
                    }
                    if (badgeText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = iconTint
                        )
                    }
                }

                if (result.houseNumberStatus == HouseNumberStatus.VERIFIED) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("house_number_verified_badge")
                    ) {
                        Text(
                            text = "✓ Doğrulandı",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                } else if (result.houseNumberStatus == HouseNumberStatus.UNVERIFIED) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("house_number_unverified_badge")
                    ) {
                        Text(
                            text = "Bina no yok",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val secondaryText = result.shortAddress.ifBlank { result.displayName }
            Text(
                text = secondaryText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Bottom preview card shown when user selects a search result or taps on the map.
 * Displays title, detailed address, and action buttons to calculate routes or start navigation.
 */
@Composable
fun DestinationPreviewCard(
    destination: SearchResult,
    onCalculateRoutes: () -> Unit,
    onStartNavigation: () -> Unit,
    onSavePlace: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("destination_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header: Icon, Titles, Dismiss button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = destination.displayName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (destination.houseNumberStatus == HouseNumberStatus.VERIFIED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("destination_verified_badge")
                        ) {
                            Text(
                                text = "✓ Bina Numarası Doğrulandı",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    } else if (destination.houseNumberStatus == HouseNumberStatus.UNVERIFIED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("destination_unverified_badge")
                        ) {
                            Text(
                                text = "Cadde bulundu, bina numarası doğrulanamadı",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_destination_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showSaveDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("save_place_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adresi Kaydet", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCalculateRoutes,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("route_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Rotalar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = onStartNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("start_navigation_direct_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Navigasyonu Başlat",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePlaceDialog(
            defaultTitle = destination.name,
            onDismiss = { showSaveDialog = false },
            onSave = { title, category ->
                onSavePlace(title, category)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun SavedPlaceRow(
    favorite: FavoritePlace,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (favorite.category) {
        "HOME" -> Icons.Default.Home
        "WORK" -> Icons.Default.Work
        else -> Icons.Default.Bookmark
    }
    val label = when (favorite.category) {
        "HOME" -> "Ev"
        "WORK" -> "İş"
        else -> "Kayıtlı"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = favorite.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$label • ${favorite.address}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Kaydı sil",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SavePlaceDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle.take(80)) }
    var category by remember { mutableStateOf("CUSTOM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
        title = { Text("Adresi Kaydet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Kayıt adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("HOME", "Ev", Icons.Default.Home),
                        Triple("WORK", "İş", Icons.Default.Work),
                        Triple("CUSTOM", "Özel", Icons.Default.Bookmark)
                    ).forEach { (value, label, icon) ->
                        FilterChip(
                            selected = category == value,
                            onClick = { category = value },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
                Text(
                    text = "Ev ve İş kayıtları tekil tutulur. Toplam kapasite 500 adrestir.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), category) }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

@Composable
private fun CategoryChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
