package nl.hiertoen.app.motion

import nl.hiertoen.app.core.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Synthetische snelheidsreeksen tegen de statusmachine (§5), zoals gevraagd in §14.1 en
 * §17.3 ("deterministische klok- en locatie-fixtures"). Geen echte klok of GPS nodig.
 */
class MotionStateEngineTest {

    private lateinit var engine: MotionStateEngine

    companion object {
        private const val BASE_LAT = 52.2215
        private const val BASE_LON = 6.8937
        private const val METERS_PER_DEGREE_LAT = 111_320.0
    }

    @Before
    fun setUp() {
        engine = MotionStateEngine(MotionThresholds.CAR)
        engine.start()
    }

    /** `northOffsetM` verschuift alleen de breedtegraad; genoeg voor rechttoe-rechtaan testscenario's. */
    private fun input(
        tMs: Long,
        speedKmh: Double,
        northOffsetM: Double = 0.0,
        accuracyM: Double = 5.0,
        activity: ActivityType = ActivityType.UNKNOWN,
    ) = MotionInput(
        timestampMs = tMs,
        lat = BASE_LAT + northOffsetM / METERS_PER_DEGREE_LAT,
        lon = BASE_LON,
        speedKmh = speedKmh,
        accuracyM = accuracyM,
        activityType = activity,
    )

    @Test
    fun `start zet status op STOP_CANDIDATE`() {
        assertEquals(MotionState.STOP_CANDIDATE, engine.currentState)
    }

    @Test
    fun `blijvende hoge snelheid wordt pas MOVING na de sustain-periode`() {
        assertEquals(MotionState.SLOW, engine.update(input(0L, speedKmh = 30.0)))
        assertEquals(MotionState.SLOW, engine.update(input(1_000L, speedKmh = 30.0)))
        assertEquals(MotionState.SLOW, engine.update(input(2_900L, speedKmh = 30.0)))
        assertEquals(MotionState.MOVING, engine.update(input(3_000L, speedKmh = 30.0)))
    }

    @Test
    fun `activity IN_VEHICLE activeert direct MOVING ongeacht snelheid`() {
        val result = engine.update(input(0L, speedKmh = 2.0, activity = ActivityType.IN_VEHICLE))
        assertEquals(MotionState.MOVING, result)
    }

    @Test
    fun `trage snelheid wordt pas STILL na de stopvertraging`() {
        assertEquals(MotionState.STOP_CANDIDATE, engine.update(input(0L, speedKmh = 0.5)))
        assertEquals(MotionState.STOP_CANDIDATE, engine.update(input(2_000L, speedKmh = 0.5)))
        assertEquals(MotionState.STOP_CANDIDATE, engine.update(input(3_900L, speedKmh = 0.5)))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.5)))
    }

    @Test
    fun `verplaatsing tijdens dwell boven stopDisplacementM voorkomt premature STILL`() {
        engine.update(input(0L, speedKmh = 0.5, northOffsetM = 0.0))
        engine.update(input(2_000L, speedKmh = 0.5, northOffsetM = 0.0))
        // Op 3s springt de meting >8m weg: dwell moet opnieuw beginnen, dus nog geen STILL op t=4000.
        val atFourSeconds = engine.update(input(4_000L, speedKmh = 0.5, northOffsetM = 9.0))
        assertEquals(MotionState.STOP_CANDIDATE, atFourSeconds)
        // Vanaf hier (nieuw anker op t=4000) duurt het weer 4s voordat STILL bevestigd wordt.
        val atEightSeconds = engine.update(input(8_000L, speedKmh = 0.5, northOffsetM = 9.0))
        assertEquals(MotionState.STILL, atEightSeconds)
    }

    @Test
    fun `kleine GPS-drift tijdens STILL leidt niet terug naar STOP_CANDIDATE`() {
        engine.update(input(0L, speedKmh = 0.5))
        engine.update(input(2_000L, speedKmh = 0.5))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.5)))

        // 9m drift ligt boven stopDisplacementM (8m) maar onder resumeDisplacementM (10m):
        // zou de strikte stop-bevestigingstak opnieuw worden gebruikt, dan flikkert dit naar
        // STOP_CANDIDATE. De statusmachine moet dit als "nog steeds stil" blijven zien.
        val driftSample = engine.update(input(5_000L, speedKmh = 0.5, northOffsetM = 9.0))
        assertEquals(MotionState.STILL, driftSample)
    }

    @Test
    fun `STILL eindigt bij snelheid boven de hervattendrempel`() {
        engine.update(input(0L, speedKmh = 0.5))
        engine.update(input(2_000L, speedKmh = 0.5))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.5)))

        val result = engine.update(input(4_500L, speedKmh = 5.0))
        assertEquals(MotionState.MOVING, result)
    }

    @Test
    fun `STILL eindigt bij verplaatsing boven de hervattendrempel, ook bij lage snelheid`() {
        engine.update(input(0L, speedKmh = 0.5))
        engine.update(input(2_000L, speedKmh = 0.5))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.5)))

        val result = engine.update(input(4_500L, speedKmh = 0.5, northOffsetM = 11.0))
        assertEquals(MotionState.MOVING, result)
    }

    @Test
    fun `lage nauwkeurigheid geeft GPS_UNRELIABLE ongeacht snelheid`() {
        val result = engine.update(input(0L, speedKmh = 40.0, accuracyM = 50.0))
        assertEquals(MotionState.GPS_UNRELIABLE, result)
    }

    @Test
    fun `pause bevriest de status tot resume`() {
        engine.update(input(0L, speedKmh = 30.0))
        engine.pause()
        assertEquals(MotionState.PAUSED, engine.currentState)

        // Updates tijdens PAUSED worden genegeerd.
        assertEquals(MotionState.PAUSED, engine.update(input(1_000L, speedKmh = 30.0)))

        engine.resume()
        assertEquals(MotionState.STOP_CANDIDATE, engine.currentState)
    }

    @Test
    fun `stop-and-go file flikkert niet tussen STILL en MOVING`() {
        // Snelheid schommelt rond nul in stilstaand verkeer, maar blijft ruim onder de
        // hervattendrempel (3 km/u) en de auto verplaatst zich nauwelijks.
        engine.update(input(0L, speedKmh = 0.0))
        engine.update(input(2_000L, speedKmh = 1.2))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.0)))

        val speeds = listOf(0.8, 1.4, 0.3, 1.0, 0.0, 1.3)
        var t = 4_500L
        for (speed in speeds) {
            val state = engine.update(input(t, speedKmh = speed, northOffsetM = 1.5))
            assertEquals("op t=$t mag status niet naar MOVING flikkeren", MotionState.STILL, state)
            t += 500L
        }
    }

    @Test
    fun `stop gooit interne timers weg zodat een nieuwe rit niet meteen STILL is`() {
        engine.update(input(0L, speedKmh = 0.5))
        engine.update(input(2_000L, speedKmh = 0.5))
        assertEquals(MotionState.STILL, engine.update(input(4_000L, speedKmh = 0.5)))

        engine.stop()
        assertEquals(MotionState.IDLE, engine.currentState)

        engine.start()
        assertNotEquals(MotionState.STILL, engine.currentState)
        assertEquals(MotionState.STOP_CANDIDATE, engine.update(input(0L, speedKmh = 0.5)))
    }
}
