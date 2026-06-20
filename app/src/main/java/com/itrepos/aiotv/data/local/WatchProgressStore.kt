package com.itrepos.aiotv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.itrepos.aiotv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun getProgress(id: String): Flow<WatchProgress?> = dataStore.data.map { prefs ->
        val pos = prefs[longPreferencesKey("wp_pos_$id")] ?: return@map null
        val dur = prefs[longPreferencesKey("wp_dur_$id")] ?: 0L
        val ts = prefs[longPreferencesKey("wp_ts_$id")] ?: 0L
        WatchProgress(id, pos, dur, ts)
    }

    suspend fun saveProgress(id: String, positionMs: Long, durationMs: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("wp_pos_$id")] = positionMs
            prefs[longPreferencesKey("wp_dur_$id")] = durationMs
            prefs[longPreferencesKey("wp_ts_$id")] = System.currentTimeMillis()
        }
    }

    fun getAllProgress(): Flow<List<WatchProgress>> = dataStore.data.map { prefs ->
        prefs.asMap()
            .keys
            .filter { it.name.startsWith("wp_pos_") }
            .map { key ->
                val id = key.name.removePrefix("wp_pos_")
                val pos = prefs[longPreferencesKey("wp_pos_$id")] ?: 0L
                val dur = prefs[longPreferencesKey("wp_dur_$id")] ?: 0L
                val ts = prefs[longPreferencesKey("wp_ts_$id")] ?: 0L
                WatchProgress(id, pos, dur, ts)
            }
            .sortedByDescending { it.lastWatchedMs }
    }
}
