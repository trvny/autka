package com.carfinder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * Loads an offer image with a neutral placeholder while loading and a car-icon
 * fallback when the URL is missing or fails to load.
 */
@Composable
fun OfferImage(url: String?, modifier: Modifier = Modifier) {
    val resolved = resolveImageUrl(url)
    if (resolved == null) {
        Placeholder(modifier)
        return
    }
    SubcomposeAsyncImage(
        model = resolved,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { Placeholder(Modifier.fillMaxSize()) },
        error = { Placeholder(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
