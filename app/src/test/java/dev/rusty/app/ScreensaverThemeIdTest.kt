package dev.rusty.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverThemeIdTest {

    @Test fun defaultsToClockForNullOrUnknown() {
        assertEquals(ScreensaverThemeId.CLOCK, ScreensaverThemeId.fromPrefValue(null))
        assertEquals(ScreensaverThemeId.CLOCK, ScreensaverThemeId.fromPrefValue("bogus"))
    }

    @Test fun legacyPartyStillFallsBackToClock() {
        assertEquals(ScreensaverThemeId.CLOCK, ScreensaverThemeId.fromPrefValue("PARTY"))
    }

    @Test fun roundTripsPrefValue() {
        for (id in ScreensaverThemeId.values()) {
            assertEquals(id, ScreensaverThemeId.fromPrefValue(id.prefValue))
        }
    }

    @Test fun canvasRoundTrips() {
        assertEquals(ScreensaverThemeId.CANVAS, ScreensaverThemeId.fromPrefValue("CANVAS"))
    }

    @Test fun albumArtRoundTrips() {
        assertEquals(ScreensaverThemeId.ALBUM_ART, ScreensaverThemeId.fromPrefValue("ALBUM_ART"))
    }

    @Test fun albumArtSitsBetweenOledAndCanvas() {
        // Declaration order is the settings-selector order, so this pins the picker layout.
        val order = ScreensaverThemeId.values().toList()
        assertEquals(order.indexOf(ScreensaverThemeId.OLED) + 1, order.indexOf(ScreensaverThemeId.ALBUM_ART))
        assertEquals(order.indexOf(ScreensaverThemeId.ALBUM_ART) + 1, order.indexOf(ScreensaverThemeId.CANVAS))
    }

    @Test fun slideshowRoundTrips() {
        assertEquals(
            ScreensaverThemeId.SLIDESHOW,
            ScreensaverThemeId.fromPrefValue(ScreensaverThemeId.SLIDESHOW.prefValue)
        )
    }
}
