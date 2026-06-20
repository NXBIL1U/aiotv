package com.itrepos.aiotv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_TORBOX_API_KEY = stringPreferencesKey("torbox_api_key")
        val KEY_XTREAM_SERVER = stringPreferencesKey("xtream_server")
        val KEY_XTREAM_USER = stringPreferencesKey("xtream_user")
        val KEY_XTREAM_PASS = stringPreferencesKey("xtream_pass")
        val KEY_M3U_URL = stringPreferencesKey("m3u_url")
        val KEY_XMLTV_URL = stringPreferencesKey("xmltv_url")
        val KEY_ADDON_URLS = stringSetPreferencesKey("addon_urls")
    }

    val torBoxApiKey: Flow<String> = dataStore.data.map { it[KEY_TORBOX_API_KEY] ?: "" }
    val xtreamServer: Flow<String> = dataStore.data.map { it[KEY_XTREAM_SERVER] ?: "" }
    val xtreamUser: Flow<String> = dataStore.data.map { it[KEY_XTREAM_USER] ?: "" }
    val xtreamPass: Flow<String> = dataStore.data.map { it[KEY_XTREAM_PASS] ?: "" }
    val m3uUrl: Flow<String> = dataStore.data.map { it[KEY_M3U_URL] ?: "" }
    val xmltvUrl: Flow<String> = dataStore.data.map { it[KEY_XMLTV_URL] ?: "" }
    val addonUrls: Flow<Set<String>> = dataStore.data.map { it[KEY_ADDON_URLS] ?: emptySet() }

    suspend fun setTorBoxApiKey(key: String) = dataStore.edit { it[KEY_TORBOX_API_KEY] = key }
    suspend fun setXtreamServer(url: String) = dataStore.edit { it[KEY_XTREAM_SERVER] = url }
    suspend fun setXtreamUser(user: String) = dataStore.edit { it[KEY_XTREAM_USER] = user }
    suspend fun setXtreamPass(pass: String) = dataStore.edit { it[KEY_XTREAM_PASS] = pass }
    suspend fun setM3uUrl(url: String) = dataStore.edit { it[KEY_M3U_URL] = url }
    suspend fun setXmltvUrl(url: String) = dataStore.edit { it[KEY_XMLTV_URL] = url }
    suspend fun addAddonUrl(url: String) = dataStore.edit {
        val current = it[KEY_ADDON_URLS] ?: emptySet()
        it[KEY_ADDON_URLS] = current + url
    }
    suspend fun removeAddonUrl(url: String) = dataStore.edit {
        val current = it[KEY_ADDON_URLS] ?: emptySet()
        it[KEY_ADDON_URLS] = current - url
    }
}
