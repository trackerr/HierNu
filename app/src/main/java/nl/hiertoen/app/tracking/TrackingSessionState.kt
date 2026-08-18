package nl.hiertoen.app.tracking

import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.motion.MotionState

/**
 * Wat er nu op het rijscherm getoond mag worden — §4.3. Alleen aanwezig wanneer de
 * MotionStateEngine STILL bevestigt; TrackingService garandeert dat [TrackingSessionState.Active
 * .displayedPhoto] elders al null is zodra dat niet meer zo is (§12.3, §13.4).
 */
data class DisplayedPhotoInfo(
    val momentId: String,
    val title: String,
    val imageUrl: String,
    val thumbUrl: String,
    val year: Int?,
    val attribution: String,
    val distanceM: Double,
    val provider: String,
    val sourcePageUrl: String,
)

/**
 * De ene regel die §12.3/§14.4 harde eisen stelt: nooit tonen buiten STILL, ongeacht wat er
 * gecachet staat. Losgetrokken van [TrackingService] zodat deze exacte gate apart getest kan
 * worden, in plaats van begraven te zitten in een niet-testbare Service-methode.
 */
fun displayedPhotoFor(motionState: MotionState, cachedPhoto: DisplayedPhotoInfo?): DisplayedPhotoInfo? =
    if (motionState == MotionState.STILL) cachedPhoto else null

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
        val displayedPhoto: DisplayedPhotoInfo? = null,
    ) : TrackingSessionState()
}
