package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.KoreaExperience.DROVE_IN_KOREA
import com.roadmatekorea.android.profile.model.KoreaExperience.FIRST_TIME_IN_KOREA
import com.roadmatekorea.android.profile.model.KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE
import com.roadmatekorea.android.profile.model.Personas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GuidancePolicyTest {

    private fun scope(country: DrivingCountry, experience: KoreaExperience) =
        GuidancePolicy.scopeFor(DriverProfile(AppLanguage.EN, country, experience))

    // ---- 개인화 표 다섯 줄 (docs/mvp-scenario.md 3.3) ----

    @Test
    fun row1_japan_firstTimeLeftHandDrive_getsRightSideAndLhdAdaptationAndDetail() {
        assertEquals(
            GuidanceScope(DetailLevel.DETAILED, rightSideAdaptation = true, leftHandDriveAdaptation = true),
            scope(DrivingCountry.JP, FIRST_TIME_LEFT_HAND_DRIVE),
        )
    }

    @Test
    fun row2_us_firstTimeInKorea_getsDetailOnly() {
        assertEquals(
            GuidanceScope(DetailLevel.DETAILED, rightSideAdaptation = false, leftHandDriveAdaptation = false),
            scope(DrivingCountry.US, FIRST_TIME_IN_KOREA),
        )
    }

    @Test
    fun row3_other_firstTimeInKorea_getsDetailOnly() {
        assertEquals(
            GuidanceScope(DetailLevel.DETAILED, rightSideAdaptation = false, leftHandDriveAdaptation = false),
            scope(DrivingCountry.OTHER, FIRST_TIME_IN_KOREA),
        )
    }

    @Test
    fun row4_other_firstTimeLeftHandDrive_getsLhdAdaptationAndDetail() {
        assertEquals(
            GuidanceScope(DetailLevel.DETAILED, rightSideAdaptation = false, leftHandDriveAdaptation = true),
            scope(DrivingCountry.OTHER, FIRST_TIME_LEFT_HAND_DRIVE),
        )
    }

    @Test
    fun row5_other_droveInKorea_getsMinimal() {
        assertEquals(
            GuidanceScope(DetailLevel.MINIMAL, rightSideAdaptation = false, leftHandDriveAdaptation = false),
            scope(DrivingCountry.OTHER, DROVE_IN_KOREA),
        )
    }

    // ---- 통행 방향 ----

    @Test
    fun trafficSide_ofAllElevenCountries() {
        val expected = mapOf(
            DrivingCountry.CN to TrafficSide.RIGHT,
            DrivingCountry.JP to TrafficSide.LEFT,
            DrivingCountry.TW to TrafficSide.RIGHT,
            DrivingCountry.US to TrafficSide.RIGHT,
            DrivingCountry.HK to TrafficSide.LEFT,
            DrivingCountry.PH to TrafficSide.RIGHT,
            DrivingCountry.VN to TrafficSide.RIGHT,
            DrivingCountry.TH to TrafficSide.LEFT,
            DrivingCountry.SG to TrafficSide.LEFT,
            DrivingCountry.MY to TrafficSide.LEFT,
            DrivingCountry.OTHER to TrafficSide.UNKNOWN,
        )
        assertEquals(DrivingCountry.entries.toSet(), expected.keys)
        expected.forEach { (country, side) -> assertEquals(country.name, side, GuidancePolicy.trafficSideOf(country)) }
    }

    @Test
    fun other_neverGetsRightSideAdaptation() {
        KoreaExperience.entries.forEach {
            assertFalse(it.name, scope(DrivingCountry.OTHER, it).rightSideAdaptation)
        }
    }

    @Test
    fun rightSideAdaptation_onlyForLeftTrafficCountriesWithoutKoreaExperience() {
        DrivingCountry.entries.forEach { country ->
            KoreaExperience.entries.forEach { experience ->
                val expected = GuidancePolicy.trafficSideOf(country) == TrafficSide.LEFT && experience != DROVE_IN_KOREA
                assertEquals("$country/$experience", expected, scope(country, experience).rightSideAdaptation)
            }
        }
    }

    @Test
    fun lhdAdaptation_dependsOnlyOnExperience() {
        DrivingCountry.entries.forEach { country ->
            assertEquals(true, scope(country, FIRST_TIME_LEFT_HAND_DRIVE).leftHandDriveAdaptation)
            assertEquals(false, scope(country, FIRST_TIME_IN_KOREA).leftHandDriveAdaptation)
            assertEquals(false, scope(country, DROVE_IN_KOREA).leftHandDriveAdaptation)
        }
    }

    @Test
    fun language_doesNotChangeScope() {
        AppLanguage.entries.forEach { language ->
            assertEquals(
                scope(DrivingCountry.JP, FIRST_TIME_LEFT_HAND_DRIVE),
                GuidancePolicy.scopeFor(DriverProfile(language, DrivingCountry.JP, FIRST_TIME_LEFT_HAND_DRIVE)),
            )
        }
    }

    // ---- 페르소나 ----

    @Test
    fun personas_matchScenario() {
        assertEquals(scope(DrivingCountry.JP, FIRST_TIME_LEFT_HAND_DRIVE), GuidancePolicy.scopeFor(Personas.NAKAMURA))
        assertEquals(scope(DrivingCountry.OTHER, FIRST_TIME_IN_KOREA), GuidancePolicy.scopeFor(Personas.CARLOS))
        assertEquals(AppLanguage.EN, Personas.NAKAMURA.language)
        assertEquals(AppLanguage.ES, Personas.CARLOS.language)
    }
}
