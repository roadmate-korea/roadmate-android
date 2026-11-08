package com.roadmatekorea.android.drive

import com.roadmatekorea.android.profile.ProfileRead
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.Personas
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedSessionRestorerTest {

    @Test
    fun opensTheSavedProfile_afterReadErrors() = runTest(UnconfinedTestDispatcher()) {
        val reads = MutableSharedFlow<ProfileRead>()
        val opened = mutableListOf<DriverProfile>()
        val r = SavedSessionRestorer(backgroundScope, reads) { opened += it }
        r.restore()
        reads.emit(ProfileRead.Error)
        assertEquals(emptyList<DriverProfile>(), opened)
        reads.emit(ProfileRead.Loaded(Personas.CARLOS))
        assertEquals(listOf(Personas.CARLOS), opened)
    }

    @Test
    fun noProfileYet_opensNothing() = runTest(UnconfinedTestDispatcher()) {
        val reads = MutableSharedFlow<ProfileRead>()
        val opened = mutableListOf<DriverProfile>()
        SavedSessionRestorer(backgroundScope, reads) { opened += it }.restore()
        reads.emit(ProfileRead.Loaded(null))
        assertEquals(emptyList<DriverProfile>(), opened)
    }

    @Test
    fun cancelledBeforeTheProfileArrives_doesNotReopenTheSession_butALaterRestoreDoes() = runTest(UnconfinedTestDispatcher()) {
        val reads = MutableSharedFlow<ProfileRead>()
        val opened = mutableListOf<DriverProfile>()
        val r = SavedSessionRestorer(backgroundScope, reads) { opened += it }
        r.restore()
        reads.emit(ProfileRead.Error) // 저장소 재시도 중
        r.cancel() // 최근 앱에서 제거
        reads.emit(ProfileRead.Loaded(Personas.CARLOS))
        assertEquals("종료 뒤 늦게 온 프로필로 세션을 열지 않음", emptyList<DriverProfile>(), opened)
        // 서비스가 다시 만들어지면 다시 복원
        r.restore()
        reads.emit(ProfileRead.Loaded(Personas.CARLOS))
        assertEquals(listOf(Personas.CARLOS), opened)
    }
}
