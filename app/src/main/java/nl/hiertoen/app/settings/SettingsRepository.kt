package nl.hiertoen.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hiertoen_settings")

/** DataStore-backed instellingen — §11. Defaults volgen letterlijk de "Standaard"-kolom uit de tabel. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val AUTO_PHOTO_ENABLED = booleanPreferencesKey("auto_photo_enabled")
        val STOP_DELAY_SECONDS = intPreferencesKey("stop_delay_seconds")
        val SHOW_SPEED = booleanPreferencesKey("show_speed")
        val SHOW_DISTANCE_TIME = booleanPreferencesKey("show_distance_time")
        val SEARCH_RADIUS_M = intPreferencesKey("search_radius_m")
        val PREFER_OLDEST = booleanPreferencesKey("prefer_oldest")
        val WIKIMEDIA_ENABLED = booleanPreferencesKey("wikimedia_enabled")
        val MOBILE_DATA_ALLOWED = booleanPreferencesKey("mobile_data_allowed")
        val ROUTE_RETENTION_DAYS = intPreferencesKey("route_retention_days") // 0 = onbeperkt (DataStore kent geen null)
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            autoPhotoEnabled = prefs[Keys.AUTO_PHOTO_ENABLED] ?: defaults.autoPhotoEnabled,
            stopDelaySeconds = prefs[Keys.STOP_DELAY_SECONDS] ?: defaults.stopDelaySeconds,
            showSpeed = prefs[Keys.SHOW_SPEED] ?: defaults.showSpeed,
            showDistanceTime = prefs[Keys.SHOW_DISTANCE_TIME] ?: defaults.showDistanceTime,
            searchRadiusM = prefs[Keys.SEARCH_RADIUS_M] ?: defaults.searchRadiusM,
            preferOldest = prefs[Keys.PREFER_OLDEST] ?: defaults.preferOldest,
            wikimediaEnabled = prefs[Keys.WIKIMEDIA_ENABLED] ?: defaults.wikimediaEnabled,
            mobileDataAllowed = prefs[Keys.MOBILE_DATA_ALLOWED] ?: defaults.mobileDataAllowed,
            routeRetentionDays = prefs[Keys.ROUTE_RETENTION_DAYS]?.takeIf { it > 0 },
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PHOTO_ENABLED] = next.autoPhotoEnabled
            prefs[Keys.STOP_DELAY_SECONDS] = next.stopDelaySeconds
            prefs[Keys.SHOW_SPEED] = next.showSpeed
            prefs[Keys.SHOW_DISTANCE_TIME] = next.showDistanceTime
            prefs[Keys.SEARCH_RADIUS_M] = next.searchRadiusM
            prefs[Keys.PREFER_OLDEST] = next.preferOldest
            prefs[Keys.WIKIMEDIA_ENABLED] = next.wikimediaEnabled
            prefs[Keys.MOBILE_DATA_ALLOWED] = next.mobileDataAllowed
            prefs[Keys.ROUTE_RETENTION_DAYS] = next.routeRetentionDays ?: 0
        }
    }
}
