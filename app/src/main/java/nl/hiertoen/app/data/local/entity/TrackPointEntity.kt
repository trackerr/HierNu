package nl.hiertoen.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Activity Recognition-resultaat op het moment van het trackpunt, zie §5. */
enum class ActivityType {
    IN_VEHICLE,
    ON_BICYCLE,
    WALKING,
    STILL,
    UNKNOWN,
}

/**
 * Geldigheid van een trackpunt voor afstandsberekening — §6.2/§6.3: punten met slechte
 * nauwkeurigheid of fysiek onwaarschijnlijke sprongen tellen niet mee in de afstand,
 * maar worden bewaard zodat de ruwe data reproduceerbaar blijft.
 */
enum class TrackPointValidity {
    VALID,
    LOW_ACCURACY,
    IMPLAUSIBLE_JUMP,
}

/**
 * Eén geregistreerd punt van een rit. Velden volgen §10.1; adaptieve registratie-intervallen
 * per vervoerswijze staan in §6.2, afstandsberekening met verwerping van sprongen in §6.3.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("tripId", "timestamp")],
)
data class TrackPointEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val activityType: ActivityType,
    val segmentIndex: Int,
    val validity: TrackPointValidity,
)
