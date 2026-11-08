package com.roadmatekorea.android.ui.onboarding

import com.roadmatekorea.android.profile.ProfileRead
import com.roadmatekorea.android.profile.ProfileStore
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeStore(var failNext: Int = 0) : ProfileStore {
        val saved = mutableListOf<DriverProfile>()
        override val profile: Flow<ProfileRead> = emptyFlow()
        override suspend fun save(profile: DriverProfile) {
            if (failNext > 0) {
                failNext--
                throw IOException("disk full")
            }
            saved += profile
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun OnboardingViewModel.chooseCarlos() {
        selectLanguage(AppLanguage.ES)
        selectCountry(DrivingCountry.OTHER)
        selectExperience(KoreaExperience.FIRST_TIME_IN_KOREA)
    }

    @Test
    fun start_savesAndCallsOnSaved() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = OnboardingViewModel(store, existing = null)
        vm.chooseCarlos()
        var done: DriverProfile? = null
        vm.start { done = it }
        advanceUntilIdle()
        assertEquals(listOf(Personas.CARLOS), store.saved)
        assertEquals(Personas.CARLOS, done)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun saveFailure_keepsChoices_showsError_andRetryWorks() = runTest(dispatcher) {
        val store = FakeStore(failNext = 1)
        val vm = OnboardingViewModel(store, existing = null)
        vm.chooseCarlos()
        var doneCount = 0
        vm.start { doneCount++ }
        advanceUntilIdle()
        assertEquals(0, doneCount)
        assertTrue(vm.state.value.saveFailed)
        assertEquals(Personas.CARLOS, vm.state.value.toProfile())
        assertTrue(vm.state.value.canStart)

        vm.start { doneCount++ }
        advanceUntilIdle()
        assertEquals(1, doneCount)
        assertFalse(vm.state.value.saveFailed)
        assertEquals(listOf(Personas.CARLOS), store.saved)
    }

    @Test
    fun start_ignoredWhileSaving_orIncomplete() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = OnboardingViewModel(store, existing = null)
        vm.selectLanguage(AppLanguage.EN)
        vm.start { }
        advanceUntilIdle()
        assertTrue(store.saved.isEmpty())

        vm.selectCountry(DrivingCountry.JP)
        vm.selectExperience(KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE)
        vm.start { }
        assertFalse(vm.state.value.canStart) // 저장 중
        vm.start { }
        advanceUntilIdle()
        assertEquals(listOf(Personas.NAKAMURA), store.saved)
    }
}
