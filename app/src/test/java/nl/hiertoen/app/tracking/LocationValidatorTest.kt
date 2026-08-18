package nl.hiertoen.app.tracking

import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationValidatorTest {

    @Test
    fun `eerste punt van een rit is altijd VALID bij goede nauwkeurigheid`() {
        val result = LocationValidator.validate(
            previous = null,
            lat = 52.2215,
            lon = 6.8937,
            timestampMs = 1_000L,
            accuracyM = 10.0,
            maxAccuracyM = 35.0,
        )
        assertEquals(TrackPointValidity.VALID, result)
    }

    @Test
    fun `slechte nauwkeurigheid geeft LOW_ACCURACY`() {
        val result = LocationValidator.validate(
            previous = null,
            lat = 52.2215,
            lon = 6.8937,
            timestampMs = 1_000L,
            accuracyM = 50.0,
            maxAccuracyM = 35.0,
        )
        assertEquals(TrackPointValidity.LOW_ACCURACY, result)
    }

    @Test
    fun `realistische verplaatsing tussen twee punten is VALID`() {
        val previous = TestFixtures.trackPoint(id = "p0", timestamp = 0L)
        // ~28m verderop na 3s ~ 33 km/u, ruim binnen het plausibele bereik.
        val result = LocationValidator.validate(
            previous = previous,
            lat = previous.lat + 0.00025,
            lon = previous.lon,
            timestampMs = 3_000L,
            accuracyM = 10.0,
            maxAccuracyM = 35.0,
        )
        assertEquals(TrackPointValidity.VALID, result)
    }

    @Test
    fun `fysiek onwaarschijnlijke sprong geeft IMPLAUSIBLE_JUMP`() {
        val previous = TestFixtures.trackPoint(id = "p0", timestamp = 0L)
        // ~5.5 km verderop binnen 1s: onmogelijk voor deze app.
        val result = LocationValidator.validate(
            previous = previous,
            lat = previous.lat + 0.05,
            lon = previous.lon,
            timestampMs = 1_000L,
            accuracyM = 10.0,
            maxAccuracyM = 35.0,
        )
        assertEquals(TrackPointValidity.IMPLAUSIBLE_JUMP, result)
    }

    @Test
    fun `niet-oplopende tijdstempel geeft IMPLAUSIBLE_JUMP`() {
        val previous = TestFixtures.trackPoint(id = "p0", timestamp = 5_000L)
        val result = LocationValidator.validate(
            previous = previous,
            lat = previous.lat,
            lon = previous.lon,
            timestampMs = 4_000L,
            accuracyM = 10.0,
            maxAccuracyM = 35.0,
        )
        assertEquals(TrackPointValidity.IMPLAUSIBLE_JUMP, result)
    }
}
