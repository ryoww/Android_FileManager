package com.ryo.androidfilemanager.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localFolderDataStore by preferencesDataStore(name = "local_folder")

class LocalFolderStore(
    private val context: Context,
) {
    val rootTreeUri: Flow<String?> = context.localFolderDataStore.data
        .map { preferences -> preferences[ROOT_TREE_URI] }

    suspend fun saveRootTreeUri(uri: Uri) {
        context.localFolderDataStore.edit { preferences ->
            preferences[ROOT_TREE_URI] = uri.toString()
        }
    }

    suspend fun clearRootTreeUri() {
        context.localFolderDataStore.edit { preferences ->
            preferences.remove(ROOT_TREE_URI)
        }
    }

    private companion object {
        val ROOT_TREE_URI = stringPreferencesKey("root_tree_uri")
    }
}

