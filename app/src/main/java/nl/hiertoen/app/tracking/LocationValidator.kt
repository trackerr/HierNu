package nl.hiertoen.app.tracking

import nl.hiertoen.app.core.GeoMath
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity

/**
 * Geldigheid van een binnenkomend GPS-punt — §6.2/§6.3: nauwkeurigheid > 35 m of een
 * fysiek onwaarschijnlijke sprong telt niet mee in de afstand, maar wordt wel bewaard.
 */
object LocationValidator {
    /** Ruim boven wat met deze app haalbaar is (~200 km/u); voorkomt dat GPS-sprongen de afstand vervuilen. */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 55.6

    fun validate(
        previous: TrackPointEntity?,
        lat: Double,
        lon: Double,
        timestampMs: Long,
        accuracyM: Double,
        maxAccuracyM: Double,
    ): TrackPointValidity {
        if (accuracyM > maxAccuracyM) return TrackPointValidity.LOW_ACCURACY
        if (previous == null) return TrackPointValidity.VALID

        val dtSeconds = (timestampMs - previous.timestamp) / 1_000.0
        if (dtSeconds <= 0) return TrackPointValidity.IMPLAUSIBLE_JUMP

        val distanceM = GeoMath.haversineMeters(previous.lat, previous.lon, lat, lon)
        val impliedSpeedMps = distanceM / dtSeconds
        return if (impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
            TrackPointValidity.IMPLAUSIBLE_JUMP
        } else {
            TrackPointValidity.VALID
        }
    }
}
