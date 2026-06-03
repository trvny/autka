package com.carfinder.ui.components

import com.carfinder.BuildConfig

/**
 * Offer image URLs may be absolute (straight from a marketplace) or relative paths
 * the backend serves from R2 (e.g. "/images/offers/..."). Resolve relative paths
 * against the configured backend base URL so Coil can load either form.
 */
fun resolveImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    return base + (if (url.startsWith("/")) url else "/$url")
}
