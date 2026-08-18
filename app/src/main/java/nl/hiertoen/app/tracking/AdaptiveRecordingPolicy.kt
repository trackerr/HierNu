package nl.hiertoen.app.tracking

import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.motion.MotionState

/**
 * Adaptieve registratie-intervallen per §6.2. De tabel geeft een bandbreedte per situatie
 * ("3-5 s of 15 m" voor auto); hier gekozen voor de ondergrens/dichtste variant, want
 * onvolledige data is duurder te herstellen dan iets vaker een punt op te slaan.
 */
class AdaptiveRecordingPolicy(mode: TripMode) {
    private val movingIntervalMs = if (mode == TripMode.BICYCLE) 2_000L else 3_000L
    private val movingDistanceM = if (mode == TripMode.BICYCLE) 5.0 else 15.0
    private val slowIntervalMs = 5_000L

    fun shouldPersist(
        motionState: MotionState,
        elapsedSinceLastPersistMs: Long,
        distanceSinceLastPersistM: Double,
        stillAlreadyPersisted: Boolean,
    ): Boolean = when (motionState) {
        // Stil: alleen het eerste stoppunt bewaren, daarna is het een heartbeat zonder trackpunt.
        MotionState.STILL -> !stillAlreadyPersisted
        MotionState.SLOW, MotionState.STOP_CANDIDATE -> elapsedSinceLastPersistMs >= slowIntervalMs
        MotionState.MOVING -> elapsedSinceLastPersistMs >= movingIntervalMs || distanceSinceLastPersistM >= movingDistanceM
        MotionState.GPS_UNRELIABLE, MotionState.IDLE, MotionState.PAUSED -> false
    }
}
