package com.cloud.launcher.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud_launcher_prefs")

private fun String?.toPkgList(): List<String> =
    if (this.isNullOrEmpty()) emptyList() else this.split(",").filter { it.isNotBlank() }

private fun String?.toLongMap(): Map<String, Long> {
    if (this.isNullOrEmpty()) return emptyMap()
    val result = LinkedHashMap<String, Long>()
    for (entry in this.split(",")) {
        val parts = entry.split(":")
        if (parts.size == 2) {
            val value = parts[1].toLongOrNull()
            if (value != null) result[parts[0]] = value
        }
    }
    return result
}

private fun Map<String, Long>.encode(): String =
    this.entries.joinToString(",") { it.key + ":" + it.value }

class PrefsManager(context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.cloudDataStore

    private object Keys {
        val PROTECTED = stringPreferencesKey("protected_apps")
        val FAVORITES = stringPreferencesKey("favorite_apps")
        val RECENTS = stringPreferencesKey("recent_apps")
        val LAUNCH_COUNTS = stringPreferencesKey("launch_counts")
        val DISMISSED = stringPreferencesKey("dismissed_recents")
        val SORT_BY_USAGE = booleanPreferencesKey("sort_by_usage")
        val SETUP_SHOWN = booleanPreferencesKey("setup_shown")
    }

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val protectedApps: Flow<List<String>> = data.map { it[Keys.PROTECTED].toPkgList() }
    val favoriteApps: Flow<List<String>> = data.map { it[Keys.FAVORITES].toPkgList() }
    val recentApps: Flow<List<String>> = data.map { it[Keys.RECENTS].toPkgList() }
    val launchCounts: Flow<Map<String, Long>> = data.map { it[Keys.LAUNCH_COUNTS].toLongMap() }
    val dismissedRecents: Flow<Map<String, Long>> = data.map { it[Keys.DISMISSED].toLongMap() }
    val sortByUsage: Flow<Boolean> = data.map { it[Keys.SORT_BY_USAGE] ?: false }
    val setupShown: Flow<Boolean> = data.map { it[Keys.SETUP_SHOWN] ?: false }

    suspend fun setProtected(list: List<String>) {
        store.edit { it[Keys.PROTECTED] = list.joinToString(",") }
    }

    suspend fun setFavorites(list: List<String>) {
        store.edit { it[Keys.FAVORITES] = list.joinToString(",") }
    }

    suspend fun setRecents(list: List<String>) {
        store.edit { it[Keys.RECENTS] = list.joinToString(",") }
    }

    suspend fun setDismissed(map: Map<String, Long>) {
        store.edit { it[Keys.DISMISSED] = map.encode() }
    }

    suspend fun setSortByUsage(value: Boolean) {
        store.edit { it[Keys.SORT_BY_USAGE] = value }
    }

    suspend fun setSetupShown(value: Boolean) {
        store.edit { it[Keys.SETUP_SHOWN] = value }
    }

    suspend fun incrementLaunch(pkg: String) {
        store.edit { prefs ->
            val map = prefs[Keys.LAUNCH_COUNTS].toLongMap().toMutableMap()
            map[pkg] = (map[pkg] ?: 0L) + 1L
            prefs[Keys.LAUNCH_COUNTS] = map.encode()
        }
    }
}
