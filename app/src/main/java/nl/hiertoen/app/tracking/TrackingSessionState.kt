package nl.hiertoen.app.tracking

import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.motion.MotionState

/** Live status van de TrackingService, geëxposeerd aan het rijscherm (§4.2). */
sealed class TrackingSessionState {
    data object NoActiveTrip : TrackingSessionState()

    data class Active(
        val tripId: String,
        val status: TripStatus,
        val motionState: MotionState,
        val mode: TripMode,
        val elapsedMs: Long,
        val movingMs: Long,
        val stoppedMs: Long,
        val distanceM: Double,
        val currentSpeedKmh: Double,
        val maxSpeedKmh: Double,
    ) : TrackingSessionState()
}
