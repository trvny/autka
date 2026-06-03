package com.carfinder.feature.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.carfinder.BuildConfig
import com.carfinder.R
import com.carfinder.core.model.CarOffer
import com.carfinder.ui.components.EmptyState
import com.carfinder.ui.components.formatted
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    offers: List<CarOffer>,
    onBack: () -> Unit,
    onOfferClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        // The Maps SDK needs an API key (manifest meta-data). Without one, tiles won't
        // load, so show a clear message instead of a blank map.
        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            EmptyState(stringResource(R.string.map_no_key), Modifier.padding(padding))
            return@Scaffold
        }

        val located = offers.filter { it.latitude != null && it.longitude != null }
        val focus = located.firstOrNull()
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                LatLng(focus?.latitude ?: 52.0, focus?.longitude ?: 19.0),
                if (focus != null) 5f else 3f,
            )
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize().padding(padding),
            cameraPositionState = cameraPositionState,
        ) {
            located.forEach { offer ->
                val markerState = rememberMarkerState(
                    key = offer.id,
                    position = LatLng(offer.latitude!!, offer.longitude!!),
                )
                Marker(
                    state = markerState,
                    title = offer.title,
                    snippet = offer.price.formatted(),
                    onInfoWindowClick = { onOfferClick(offer.id) },
                )
            }
        }
    }
}
