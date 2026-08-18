package nl.hiertoen.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Zie technische bouwspecificatie §10.2. */
enum class TripStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    RECOVERABLE,
    DELETED_PENDING,
}

/** Vervoerswijze; kalibratieprofielen per modus staan in §5.4. Wandelen is bewust nog niet actief in de MVP. */
enum class TripMode {
    CAR,
    BICYCLE,
    WALKING,
    UNKNOWN,
}

/**
 * Eén geregistreerde rit. Velden volgen §10.1; `algorithmVersion` maakt veldtestresultaten
 * herleidbaar naar de motion-engineversie waarmee de rit is opgenomen (§6.1).
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val status: TripStatus,
    val mode: TripMode,
    val distanceM: Double,
    val movingMs: Long,
    val stoppedMs: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val algorithmVersion: String,
    val createdAt: Long,
)
