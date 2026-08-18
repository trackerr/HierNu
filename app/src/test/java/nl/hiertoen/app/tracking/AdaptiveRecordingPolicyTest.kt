package nl.hiertoen.app.tracking

import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.motion.MotionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRecordingPolicyTest {

    private val carPolicy = AdaptiveRecordingPolicy(TripMode.CAR)
    private val bicyclePolicy = AdaptiveRecordingPolicy(TripMode.BICYCLE)

    @Test
    fun `stil bewaart alleen het eerste punt`() {
        assertTrue(
            carPolicy.shouldPersist(
                MotionState.STILL,
                elapsedSinceLastPersistMs = 0,
                distanceSinceLastPersistM = 0.0,
                stillAlreadyPersisted = false,
            ),
        )
        assertFalse(
            carPolicy.shouldPersist(
                MotionState.STILL,
                elapsedSinceLastPersistMs = 60_000,
                distanceSinceLastPersistM = 0.0,
                stillAlreadyPersisted = true,
            ),
        )
    }

    @Test
    fun `auto bewaart elke 3s of 15m tijdens MOVING`() {
        assertFalse(
            carPolicy.shouldPersist(MotionState.MOVING, elapsedSinceLastPersistMs = 2_000, distanceSinceLastPersistM = 5.0, stillAlreadyPersisted = false),
        )
        assertTrue(
            carPolicy.shouldPersist(MotionState.MOVING, elapsedSinceLastPersistMs = 3_000, distanceSinceLastPersistM = 5.0, stillAlreadyPersisted = false),
        )
        assertTrue(
            carPolicy.shouldPersist(MotionState.MOVING, elapsedSinceLastPersistMs = 500, distanceSinceLastPersistM = 15.0, stillAlreadyPersisted = false),
        )
    }

    @Test
    fun `fiets bewaart vaker dan auto tijdens MOVING`() {
        assertTrue(
            bicyclePolicy.shouldPersist(MotionState.MOVING, elapsedSinceLastPersistMs = 2_000, distanceSinceLastPersistM = 0.0, stillAlreadyPersisted = false),
        )
        assertFalse(
            carPolicy.shouldPersist(MotionState.MOVING, elapsedSinceLastPersistMs = 2_000, distanceSinceLastPersistM = 0.0, stillAlreadyPersisted = false),
        )
    }

    @Test
    fun `GPS_UNRELIABLE en PAUSED slaan nooit op`() {
        assertFalse(
            carPolicy.shouldPersist(MotionState.GPS_UNRELIABLE, elapsedSinceLastPersistMs = 999_999, distanceSinceLastPersistM = 999.0, stillAlreadyPersisted = false),
        )
        assertFalse(
            carPolicy.shouldPersist(MotionState.PAUSED, elapsedSinceLastPersistMs = 999_999, distanceSinceLastPersistM = 999.0, stillAlreadyPersisted = false),
        )
    }
}
