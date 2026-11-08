package com.roadmatekorea.android.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.ProfileCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_profile")

/** 운전자 프로필을 기기에만 저장한다(DataStore). 외부로 보내지 않는다. */
class ProfileRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.profileDataStore)

    /** 저장된 프로필. 아직 온보딩을 마치지 않았거나 값이 깨졌으면 null. */
    val profile: Flow<DriverProfile?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { ProfileCodec.decode(it[LANGUAGE], it[COUNTRY], it[EXPERIENCE]) }

    suspend fun save(profile: DriverProfile) {
        val stored = ProfileCodec.encode(profile)
        dataStore.edit {
            it[LANGUAGE] = stored.language
            it[COUNTRY] = stored.country
            it[EXPERIENCE] = stored.experience
        }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val COUNTRY = stringPreferencesKey("country")
        val EXPERIENCE = stringPreferencesKey("experience")
    }
}
