package com.ryo.androidfilemanager.data.smb

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.smbConnectionDataStore by preferencesDataStore(name = "smb_connection")

class SmbConnectionStore(
    private val context: Context,
) {
    val savedConnection: Flow<SmbConnectionInfo?> = context.smbConnectionDataStore.data
        .map { preferences ->
            val host = preferences[HOST]?.takeIf { it.isNotBlank() } ?: return@map null
            val shareName = preferences[SHARE_NAME]?.takeIf { it.isNotBlank() } ?: return@map null

            SmbConnectionInfo(
                host = host,
                shareName = shareName,
                username = preferences[USERNAME]?.takeIf { it.isNotBlank() },
                password = preferences[PASSWORD]?.takeIf { it.isNotBlank() },
                domain = preferences[DOMAIN]?.takeIf { it.isNotBlank() },
                port = preferences[PORT] ?: 445,
            )
        }

    suspend fun saveConnection(info: SmbConnectionInfo) {
        context.smbConnectionDataStore.edit { preferences ->
            preferences[HOST] = info.host
            preferences[SHARE_NAME] = info.shareName
            info.username.saveOrRemove(preferences, USERNAME)
            info.password.saveOrRemove(preferences, PASSWORD)
            info.domain.saveOrRemove(preferences, DOMAIN)
            preferences[PORT] = info.port
        }
    }

    suspend fun clearConnection() {
        context.smbConnectionDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun String?.saveOrRemove(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
    ) {
        if (isNullOrBlank()) {
            preferences.remove(key)
        } else {
            preferences[key] = this
        }
    }

    private companion object {
        val HOST = stringPreferencesKey("host")
        val SHARE_NAME = stringPreferencesKey("share_name")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val DOMAIN = stringPreferencesKey("domain")
        val PORT = intPreferencesKey("port")
    }
}
