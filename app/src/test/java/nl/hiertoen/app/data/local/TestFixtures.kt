package nl.hiertoen.app.data.local

import nl.hiertoen.app.data.local.entity.ActivityType
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus

/** Deterministische fixtures — geen `Date()`/willekeurige waarden, zodat tests reproduceerbaar zijn. */
object TestFixtures {
    fun trip(
        id: String = "trip-1",
        startedAt: Long = 1_000L,
        status: TripStatus = TripStatus.ACTIVE,
    ) = TripEntity(
        id = id,
        name = null,
        startedAt = startedAt,
        endedAt = null,
        status = status,
        mode = TripMode.CAR,
        distanceM = 0.0,
        movingMs = 0L,
        stoppedMs = 0L,
        avgSpeedKmh = 0.0,
        maxSpeedKmh = 0.0,
        algorithmVersion = "test-1",
        createdAt = startedAt,
    )

    fun trackPoint(
        id: String,
        tripId: String = "trip-1",
        timestamp: Long = 1_000L,
        accuracyM: Float = 10f,
        validity: TrackPointValidity = TrackPointValidity.VALID,
    ) = TrackPointEntity(
        id = id,
        tripId = tripId,
        timestamp = timestamp,
        lat = 52.2215,
        lon = 6.8937,
        altitude = null,
        accuracyM = accuracyM,
        speedMps = 5f,
        bearingDeg = 90f,
        activityType = ActivityType.IN_VEHICLE,
        segmentIndex = 0,
        validity = validity,
    )
}
