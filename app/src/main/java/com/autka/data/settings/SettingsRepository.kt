package com.autka.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autka.core.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide user settings persisted via Preferences DataStore, so choices like the
 * display currency survive process death and are shared across every screen.
 */
interface SettingsRepository {
    val displayCurrency: Flow<Currency>
    suspend fun setDisplayCurrency(currency: Currency)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val displayCurrency: Flow<Currency> =
        dataStore.data.map { prefs ->
            prefs[DISPLAY_CURRENCY]?.let { stored ->
                runCatching { Currency.valueOf(stored) }.getOrNull()
            } ?: DEFAULT_CURRENCY
        }

    override suspend fun setDisplayCurrency(currency: Currency) {
        dataStore.edit { prefs -> prefs[DISPLAY_CURRENCY] = currency.name }
    }

    private companion object {
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val DEFAULT_CURRENCY = Currency.PLN
    }
}
