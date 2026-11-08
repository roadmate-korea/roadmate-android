package com.roadmatekorea.android.profile

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.profile.model.ProfileCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCodecTest {

    @Test
    fun everyProfile_roundTrips() {
        AppLanguage.entries.forEach { l ->
            DrivingCountry.entries.forEach { c ->
                KoreaExperience.entries.forEach { e ->
                    val p = DriverProfile(l, c, e)
                    val s = ProfileCodec.encode(p)
                    assertEquals(p, ProfileCodec.decode(s.language, s.country, s.experience))
                }
            }
        }
    }

    @Test
    fun storesEnumNames() {
        assertEquals(ProfileCodec.Stored("ES", "OTHER", "FIRST_TIME_IN_KOREA"), ProfileCodec.encode(Personas.CARLOS))
    }

    @Test
    fun missingOrUnknownValue_meansOnboardingAgain() {
        assertNull(ProfileCodec.decode(null, null, null))
        assertNull(ProfileCodec.decode("EN", "JP", null))
        assertNull(ProfileCodec.decode("DE", "JP", "DROVE_IN_KOREA"))
        assertNull(ProfileCodec.decode("EN", "GT", "DROVE_IN_KOREA"))
        assertNull(ProfileCodec.decode("EN", "JP", "SOMETIMES"))
    }
}
