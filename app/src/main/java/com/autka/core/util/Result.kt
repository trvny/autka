package com.autka.core.util

/** Minimal result wrapper so adapter failures degrade gracefully per-source. */
sealed interface FetchResult<out T> {
    data class Success<T>(val data: T) : FetchResult<T>
    data class Failure(val sourceId: String, val error: Throwable) : FetchResult<Nothing>
}
