package com.dualsimdialer.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dualsimdialer.app.model.PhoneAccountKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.IOException

interface SimPreferencesRepository {
    fun observe(): Flow<Map<PhoneAccountKey, StoredSimPreferences>>
    suspend fun read(key: PhoneAccountKey): StoredSimPreferences
    suspend fun updateAlias(key: PhoneAccountKey, alias: String)
    suspend fun updateColor(key: PhoneAccountKey, colorArgb: Int)
    suspend fun reset(key: PhoneAccountKey)
}

data class StoredSimPreferences(
    val alias: String? = null,
    val colorArgb: Int? = null,
)

class DataStoreSimPreferencesRepository(context: Context) : SimPreferencesRepository {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("sim_profiles.preferences_pb") },
    )

    override fun observe(): Flow<Map<PhoneAccountKey, StoredSimPreferences>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            val keys = preferences.asMap().keys.mapNotNull { preferenceKey ->
                tokenFromPreferenceName(preferenceKey.name)?.let(::decodeKey)
            }.distinct()
            keys.associateWith { key ->
                val token = encodeKey(key)
                StoredSimPreferences(
                    alias = preferences[stringPreferencesKey("$KEY_PREFIX$token$ALIAS_SUFFIX")],
                    colorArgb = preferences[intPreferencesKey("$KEY_PREFIX$token$COLOR_SUFFIX")],
                )
            }
        }

    override suspend fun read(key: PhoneAccountKey): StoredSimPreferences {
        return dataStore.data.map { preferences ->
            val token = encodeKey(key)
            StoredSimPreferences(
                alias = preferences[stringPreferencesKey("$KEY_PREFIX$token$ALIAS_SUFFIX")],
                colorArgb = preferences[intPreferencesKey("$KEY_PREFIX$token$COLOR_SUFFIX")],
            )
        }.first()
    }

    override suspend fun updateAlias(key: PhoneAccountKey, alias: String) {
        val token = encodeKey(key)
        dataStore.edit { preferences ->
            val preferenceKey = stringPreferencesKey("$KEY_PREFIX$token$ALIAS_SUFFIX")
            if (alias.isBlank()) preferences.remove(preferenceKey) else preferences[preferenceKey] = alias.trim()
            preferences[stringPreferencesKey("$KEY_PREFIX$token$ACCOUNT_SUFFIX")] = key.serialized
        }
    }

    override suspend fun updateColor(key: PhoneAccountKey, colorArgb: Int) {
        val token = encodeKey(key)
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("$KEY_PREFIX$token$COLOR_SUFFIX")] = colorArgb
            preferences[stringPreferencesKey("$KEY_PREFIX$token$ACCOUNT_SUFFIX")] = key.serialized
        }
    }

    override suspend fun reset(key: PhoneAccountKey) {
        val token = encodeKey(key)
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("$KEY_PREFIX$token$ALIAS_SUFFIX"))
            preferences.remove(intPreferencesKey("$KEY_PREFIX$token$COLOR_SUFFIX"))
            preferences.remove(stringPreferencesKey("$KEY_PREFIX$token$ACCOUNT_SUFFIX"))
        }
    }

    private fun encodeKey(key: PhoneAccountKey): String = Base64.encodeToString(
        key.serialized.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP,
    )

    private fun decodeKey(value: String): PhoneAccountKey? = runCatching {
        PhoneAccountKey.parse(String(Base64.decode(value, Base64.URL_SAFE), Charsets.UTF_8))
    }.getOrNull()

    private fun tokenFromPreferenceName(name: String): String? {
        if (!name.startsWith(KEY_PREFIX)) return null
        return listOf(ALIAS_SUFFIX, COLOR_SUFFIX, ACCOUNT_SUFFIX)
            .firstOrNull { name.endsWith(it) }
            ?.let { name.removePrefix(KEY_PREFIX).removeSuffix(it) }
    }

    private companion object {
        const val KEY_PREFIX = "sim_"
        const val ALIAS_SUFFIX = "_alias"
        const val COLOR_SUFFIX = "_color"
        const val ACCOUNT_SUFFIX = "_account"
    }
}
