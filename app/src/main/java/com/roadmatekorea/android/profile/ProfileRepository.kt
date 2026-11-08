package com.roadmatekorea.android.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.ProfileCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_profile")

/** 저장된 프로필 읽기 결과. 읽기 실패와 "아직 저장하지 않음"을 구분한다. */
sealed interface ProfileRead {
    /** [profile]이 null이면 아직 온보딩을 마치지 않았거나 값이 깨졌다. */
    data class Loaded(val profile: DriverProfile?) : ProfileRead

    /** 기기 저장소를 읽지 못했다. 잠시 뒤 다시 읽는다. */
    data object Error : ProfileRead
}

interface ProfileStore {
    val profile: Flow<ProfileRead>

    /** 실패하면 [IOException]을 던진다. */
    suspend fun save(profile: DriverProfile)
}

/** 운전자 프로필을 기기에만 저장한다(DataStore). 외부로 보내지 않는다. */
class ProfileRepository(private val dataStore: DataStore<Preferences>) : ProfileStore {

    constructor(context: Context) : this(context.applicationContext.profileDataStore)

    override val profile: Flow<ProfileRead> = readProfiles(dataStore.data)

    override suspend fun save(profile: DriverProfile) {
        val stored = ProfileCodec.encode(profile)
        dataStore.edit {
            it[LANGUAGE] = stored.language
            it[COUNTRY] = stored.country
            it[EXPERIENCE] = stored.experience
        }
    }

    companion object {
        private val LANGUAGE = stringPreferencesKey("language")
        private val COUNTRY = stringPreferencesKey("country")
        private val EXPERIENCE = stringPreferencesKey("experience")

        /**
         * 읽기에 실패하면 [ProfileRead.Error]를 내고 잠시 뒤 다시 읽는다.
         * 다시 읽은 뒤에는 저장 결과도 계속 받으므로, 일시적인 실패 뒤에도 화면이 멈추지 않는다.
         */
        fun readProfiles(data: Flow<Preferences>, retryDelayMs: (attempt: Long) -> Long = ::backoffMs): Flow<ProfileRead> =
            data
                .map<Preferences, ProfileRead> { ProfileRead.Loaded(ProfileCodec.decode(it[LANGUAGE], it[COUNTRY], it[EXPERIENCE])) }
                .retryWhen { cause, attempt ->
                    if (cause !is IOException) return@retryWhen false
                    emit(ProfileRead.Error)
                    delay(retryDelayMs(attempt))
                    true
                }

        private fun backoffMs(attempt: Long): Long = (500L shl attempt.coerceAtMost(4).toInt()).coerceAtMost(8_000L)

        /** 테스트용: 저장 형식 그대로의 Preferences를 만든다. */
        internal fun keys() = Triple(LANGUAGE, COUNTRY, EXPERIENCE)
    }
}
