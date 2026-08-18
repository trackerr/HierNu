package nl.hiertoen.app.motion

import nl.hiertoen.app.core.ActivityType

/** Eén sensormeting die de MotionStateEngine verwerkt. */
data class MotionInput(
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val accuracyM: Double,
    val activityType: ActivityType = ActivityType.UNKNOWN,
)
