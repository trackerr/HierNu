package nl.hiertoen.app.core

/**
 * Activity Recognition-resultaat. Gedeeld tussen MotionStateEngine (§5) en de Room-laag
 * (TrackPointEntity, §10.1) zodat er niet twee identieke enums uit de pas kunnen lopen.
 */
enum class ActivityType {
    IN_VEHICLE,
    ON_BICYCLE,
    WALKING,
    STILL,
    UNKNOWN,
}
