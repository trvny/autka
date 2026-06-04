package com.autka.feature.map

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autka.R
import com.autka.core.model.CarOffer
import com.autka.ui.components.formatted
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // One MapView reused across recompositions. OpenStreetMap tiles need no API key;
        // the required non-default User-Agent is set in AutkaApplication.
        val mapView = remember {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.0)
                controller.setCenter(GeoPoint(52.0, 19.0)) // Poland, default view
            }
        }

        // osmdroid's MapView is a plain Android View - forward lifecycle and clean up.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDetach()
            }
        }

        // Whether we've already centred on a real offer, so updating offers doesn't yank
        // the camera back while the user is panning. Plain array = no recomposition.
        val centeredOnce = remember { booleanArrayOf(false) }

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().padding(padding),
            update = { map ->
                map.overlays.clear()
                val located = offers.filter { it.latitude != null && it.longitude != null }
                located.forEach { offer ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(offer.latitude!!, offer.longitude!!)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = offer.title
                        snippet = offer.price.formatted()
                        setOnMarkerClickListener { _, _ ->
                            onOfferClick(offer.id)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
                if (!centeredOnce[0] && located.isNotEmpty()) {
                    val first = located.first()
                    map.controller.setCenter(GeoPoint(first.latitude!!, first.longitude!!))
                    centeredOnce[0] = true
                }
                map.invalidate()
            },
        )
    }
}
