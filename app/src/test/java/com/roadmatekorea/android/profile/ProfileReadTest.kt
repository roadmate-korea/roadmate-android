package com.roadmatekorea.android.profile

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.roadmatekorea.android.profile.model.Personas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ProfileReadTest {

    private val keys = ProfileRepository.keys()
    private val carlos = preferencesOf(keys.first to "ES", keys.second to "OTHER", keys.third to "FIRST_TIME_IN_KOREA")

    /** 처음 [failures]번은 IOException, 그 뒤에는 [values]를 차례로 낸다. */
    private fun source(failures: Int, vararg values: Preferences): Flow<Preferences> {
        var attempts = 0
        return flow {
            if (attempts++ < failures) throw IOException("disk")
            values.forEach { emit(it) }
        }
    }

    @Test
    fun savedProfile_isLoaded() = runTest {
        assertEquals(
            listOf(ProfileRead.Loaded(Personas.CARLOS)),
            ProfileRepository.readProfiles(source(0, carlos)).toList(),
        )
    }

    @Test
    fun nothingSaved_isLoadedNull_notError() = runTest {
        assertEquals(
            listOf(ProfileRead.Loaded(null)),
            ProfileRepository.readProfiles(source(0, emptyPreferences())).toList(),
        )
    }

    @Test
    fun readFailure_reportsError_thenRecoversAndKeepsReceivingUpdates() = runTest {
        val reads = ProfileRepository.readProfiles(source(2, emptyPreferences(), carlos)) { 10 }.take(4).toList()
        assertEquals(
            listOf(ProfileRead.Error, ProfileRead.Error, ProfileRead.Loaded(null), ProfileRead.Loaded(Personas.CARLOS)),
            reads,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun nonIoFailure_isNotHidden() = runTest {
        ProfileRepository.readProfiles(flow<Preferences> { throw IllegalStateException("bug") }).toList()
    }
}
